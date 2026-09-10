package com.radioinfo.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import java.io.IOException

class NfcFragment : Fragment() {
    private enum class PayloadType { WEB, APPLICATION, FILE }

    private var tvInfo: TextView? = null
    private var valueInput: EditText? = null
    private var prepareButton: MaterialButton? = null
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var payloadType = PayloadType.WEB
    private var fileUri: Uri? = null
    private var pendingMessage: NdefMessage? = null
    private var preparedForTransfer = false

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        fileUri = uri
        payloadType = PayloadType.FILE
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        valueInput?.setText(uri.lastPathSegment ?: uri.toString())
        valueInput?.isEnabled = false
        updateModeButtons()
        updateStatus("文件已选择：${uri.lastPathSegment ?: "未命名文件"}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_nfc, container, false).also { view ->
        tvInfo = view.findViewById(R.id.tvNfcInfo)
        valueInput = view.findViewById(R.id.etNfcValue)
        prepareButton = view.findViewById(R.id.btnNfcPrepare)
        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        view.findViewById<View>(R.id.btnNfcWeb).setOnClickListener { selectType(PayloadType.WEB) }
        view.findViewById<View>(R.id.btnNfcApplication).setOnClickListener {
            selectType(PayloadType.APPLICATION)
        }
        view.findViewById<View>(R.id.btnNfcFile).setOnClickListener { selectFile() }
        prepareButton?.setOnClickListener { prepareTransfer() }
        view.findViewById<View>(R.id.btnNfcStop).setOnClickListener { stopTransfer() }
        updateModeButtons()
        updateStatus(initialStatus())
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) {
            updateStatus("NFC 已关闭，请在系统设置中开启 NFC")
            return
        }
        val activity = requireActivity()
        pendingIntent = PendingIntent.getActivity(
            activity,
            NFC_PENDING_INTENT_REQUEST,
            Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlags()
        )
        runCatching {
            adapter.enableForegroundDispatch(activity, pendingIntent, nfcIntentFilters(), null)
        }.onFailure {
            updateStatus("无法启用 NFC 前台调度：${it.message ?: "系统错误"}")
        }
        configureNfcPush()
    }

    override fun onPause() {
        val activity = activity
        if (activity != null) {
            runCatching { nfcAdapter?.disableForegroundDispatch(activity) }
            clearNfcPush(activity)
        }
        super.onPause()
    }

    override fun onDestroyView() {
        preparedForTransfer = false
        pendingMessage = null
        tvInfo = null
        valueInput = null
        prepareButton = null
        pendingIntent = null
        super.onDestroyView()
    }

    internal fun handleNfcIntent(intent: Intent) {
        val tag = parcelableTag(intent)
        if (preparedForTransfer && tag != null && pendingMessage != null) {
            val result = writeMessageToTag(tag, pendingMessage!!)
            if (result == null) {
                preparedForTransfer = false
                configureNfcPush()
                updateStatus("NFC 信息已写入标签")
                Toast.makeText(requireContext(), "NFC 信息写入成功", Toast.LENGTH_SHORT).show()
            } else {
                updateStatus(result)
            }
            return
        }

        val message = ndefMessages(intent).firstOrNull() ?: return
        handleReceivedMessage(message)
    }

    private fun selectType(type: PayloadType) {
        payloadType = type
        if (type != PayloadType.FILE) {
            valueInput?.isEnabled = true
            if (type == PayloadType.WEB && fileUri != null) valueInput?.setText("")
            fileUri = null
        }
        updateModeButtons()
        updateStatus(
            when (type) {
                PayloadType.WEB -> "请输入 http:// 或 https:// 网页地址"
                PayloadType.APPLICATION -> "请输入已安装应用的包名，例如 com.example.app"
                PayloadType.FILE -> "请选择要通过 NFC 推送的文件"
            }
        )
    }

    private fun selectFile() {
        payloadType = PayloadType.FILE
        updateModeButtons()
        openDocument.launch(arrayOf("*/*"))
    }

    private fun prepareTransfer() {
        val adapter = nfcAdapter
        if (adapter == null) {
            updateStatus("设备不支持 NFC")
            return
        }
        if (!adapter.isEnabled) {
            updateStatus("NFC 已关闭，请先开启 NFC")
            runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            return
        }
        val message = buildMessage() ?: return
        pendingMessage = message
        preparedForTransfer = true
        configureNfcPush()
        updateStatus(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                "已准备 NFC 信息。可靠近另一台支持 NFC 推送的设备，或靠近可写 NFC 标签。"
            } else {
                "已准备 NFC 信息，请靠近可写 NFC 标签；Android 10+ 不支持系统 NFC 点对点推送。"
            }
        )
    }

    private fun stopTransfer() {
        preparedForTransfer = false
        pendingMessage = null
        configureNfcPush()
        updateStatus("NFC 传输已停止")
    }

    private fun buildMessage(): NdefMessage? {
        return when (payloadType) {
            PayloadType.WEB -> NfcPayloadUtils.createWebMessage(valueInput?.text?.toString().orEmpty())
                ?: run {
                    updateStatus("网页地址必须是带域名的 http:// 或 https:// 地址")
                    null
                }
            PayloadType.APPLICATION -> NfcPayloadUtils.createApplicationMessage(
                valueInput?.text?.toString().orEmpty()
            ) ?: run {
                updateStatus("应用包名格式不正确")
                null
            }
            PayloadType.FILE -> fileUri?.let { uri ->
                val mimeType = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
                NfcPayloadUtils.createFileMessage(uri, mimeType)
            } ?: run {
                updateStatus("请先选择文件")
                null
            }
        }
    }

    private fun configureNfcPush() {
        val activity = activity ?: return
        val adapter = nfcAdapter ?: return
        if (!isResumed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        configureLegacyBeam(adapter, activity)
    }

    private fun clearNfcPush(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            configureLegacyBeam(nfcAdapter, activity, clear = true)
        }
    }

    private fun configureLegacyBeam(adapter: NfcAdapter?, activity: Activity, clear: Boolean = false) {
        if (adapter == null) return
        runCatching {
            val ndefMethod = NfcAdapter::class.java.getMethod(
                "setNdefPushMessage",
                NdefMessage::class.java,
                Activity::class.java
            )
            ndefMethod.invoke(adapter, if (clear) null else pendingMessage, activity)
            val beamMethod = NfcAdapter::class.java.getMethod(
                "setBeamPushUris",
                Array<Uri>::class.java,
                Activity::class.java
            )
            val uris = if (!clear && preparedForTransfer && payloadType == PayloadType.FILE) {
                fileUri?.let { arrayOf(it) }
            } else {
                null
            }
            beamMethod.invoke(adapter, uris, activity)
        }.onFailure {
            if (!clear) updateStatus("当前系统不支持 NFC 点对点推送，可使用可写 NFC 标签")
        }
    }

    private fun handleReceivedMessage(message: NdefMessage) {
        val fileRecord = message.records.firstNotNullOfOrNull { NfcPayloadUtils.filePayload(it) }
        if (fileRecord != null) {
            openReceivedFile(fileRecord.uri, fileRecord.mimeType)
            return
        }

        val packageName = message.records.firstNotNullOfOrNull { NfcPayloadUtils.applicationPackage(it) }
        if (packageName != null) {
            openApplication(packageName)
            return
        }

        val uri = message.records.asSequence()
            .mapNotNull { runCatching { it.toUri() }.getOrNull() }
            .firstOrNull()
        if (uri != null && NfcPayloadUtils.isSafeWebUrl(uri.toString())) {
            openWebPage(uri)
        } else {
            updateStatus("收到 NFC 信息，但内容不是受支持的安全网页、应用或文件")
        }
    }

    private fun openWebPage(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(Intent.createChooser(intent, "打开 NFC 网页"))
            updateStatus("已请求打开网页：$uri")
        } catch (_: Exception) {
            updateStatus("系统没有可用的网页浏览器")
        }
    }

    private fun openApplication(packageName: String) {
        val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            updateStatus("目标应用未安装：$packageName")
            return
        }
        try {
            startActivity(launchIntent)
            updateStatus("已请求打开应用：$packageName")
        } catch (_: Exception) {
            updateStatus("无法打开目标应用：$packageName")
        }
    }

    private fun openReceivedFile(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, "打开 NFC 文件"))
            updateStatus("已请求打开 NFC 文件")
        } catch (_: Exception) {
            updateStatus("没有可打开该文件的应用，或接收设备无法访问此文件 URI")
        }
    }

    private fun writeMessageToTag(tag: Tag, message: NdefMessage): String? {
        val messageSize = message.toByteArray().size
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                when {
                    !ndef.isWritable -> "NFC 标签不可写"
                    ndef.maxSize < messageSize -> "NFC 标签容量不足（需要约 $messageSize 字节）"
                    else -> {
                        ndef.writeNdefMessage(message)
                        null
                    }
                }
            } catch (_: IOException) {
                "写入 NFC 标签失败，请保持标签贴近并重试"
            } catch (_: FormatException) {
                "NFC 信息格式不受标签支持"
            } finally {
                runCatching { ndef.close() }
            }
        }

        val formatable = NdefFormatable.get(tag) ?: return "该 NFC 标签不支持 NDEF 写入"
        return try {
            formatable.connect()
            formatable.format(message)
            null
        } catch (_: IOException) {
            "NFC 标签格式化或写入失败"
        } catch (_: FormatException) {
            "NFC 信息格式不受标签支持"
        } finally {
            runCatching { formatable.close() }
        }
    }

    private fun updateModeButtons() {
        valueInput?.hint = when (payloadType) {
            PayloadType.WEB -> "https://example.com"
            PayloadType.APPLICATION -> "com.example.app"
            PayloadType.FILE -> "请选择文件"
        }
        valueInput?.isEnabled = payloadType != PayloadType.FILE
        prepareButton?.text = if (preparedForTransfer) "重新准备 NFC 信息" else "准备 NFC 传输"
    }

    private fun updateStatus(message: String) {
        tvInfo?.text = message
    }

    private fun initialStatus(): String = when {
        nfcAdapter == null -> "设备不支持 NFC"
        !nfcAdapter!!.isEnabled -> "NFC 已关闭，请开启后使用"
        else -> "选择要传输的网页、应用或文件"
    }

    private fun nfcIntentFilters(): Array<IntentFilter> = arrayOf(
        IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
        IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
        IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataType("*/*")
        }
    )

    private fun pendingIntentFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE
    } else {
        0
    }

    private fun parcelableTag(intent: Intent): Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }

    @Suppress("DEPRECATION")
    private fun ndefMessages(intent: Intent): List<NdefMessage> =
        intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            ?.mapNotNull { it as? NdefMessage }
            ?: emptyList()

    companion object {
        private const val NFC_PENDING_INTENT_REQUEST = 1401
    }
}
