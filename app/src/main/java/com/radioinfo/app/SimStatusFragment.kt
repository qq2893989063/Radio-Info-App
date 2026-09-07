package com.radioinfo.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.radioinfo.app.databinding.FragmentSimBinding

class SimStatusFragment : Fragment() {

    private var _binding: FragmentSimBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadSimInfo()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSimBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRefresh.setOnClickListener { loadSimInfo() }
    }

    override fun onResume() { super.onResume(); handler.post(refreshRunnable) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refreshRunnable) }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun loadSimInfo() {
        val tm = requireContext().getSystemService(TelephonyManager::class.java)
        val sm = requireContext().getSystemService(SubscriptionManager::class.java)

        val sb = StringBuilder()

        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("  📱 SIM 卡状态信息")
        sb.appendLine("═══════════════════════════════════")

        val simStateText = when (tm.simState) {
            TelephonyManager.SIM_STATE_READY -> "✅ SIM卡就绪 (READY)"
            TelephonyManager.SIM_STATE_ABSENT -> "❌ SIM卡缺失 (ABSENT)"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "🔒 需要PIN码"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "🔒 需要PUK码"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "🔒 网络锁定"
            TelephonyManager.SIM_STATE_NOT_READY -> "⏳ SIM卡未就绪"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "🚫 SIM卡永久禁用"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "⚠️ SIM卡I/O错误"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "⚠️ SIM卡受限"
            else -> "❓ 未知状态 (${tm.simState})"
        }
        sb.appendLine("\n■ SIM卡状态: $simStateText")

        sb.appendLine("\n--- 运营商信息 ---")
        sb.appendLine("SIM运营商名称: ${tm.simOperatorName ?: "N/A"}")
        sb.appendLine("SIM运营商代码: ${tm.simOperator ?: "N/A"}")
        sb.appendLine("SIM国家代码: ${tm.simCountryIso ?: "N/A"}")

        // Masked ICCID - only show last 4 digits
        val iccid = tm.simSerialNumber
        sb.appendLine("SIM序列号(ICCID): ${maskSensitive(iccid)}")

        // Masked phone number
        val phoneNumber = tm.line1Number
        sb.appendLine("电话号码: ${maskSensitive(phoneNumber)}")

        // Subscription Info
        sb.appendLine("\n--- 订阅信息 ---")
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val subs: List<SubscriptionInfo> = sm.activeSubscriptionInfoList ?: emptyList()
                sb.appendLine("活跃SIM卡数: ${subs.size}")

                for ((index, sub) in subs.withIndex()) {
                    sb.appendLine("\n  [SIM ${index + 1}]")
                    sb.appendLine("  订阅ID: ${sub.subscriptionId}")
                    sb.appendLine("  显示名称: ${sub.displayName}")
                    sb.appendLine("  运营商: ${sub.carrierName}")
                    sb.appendLine("  ICCID: ${maskSensitive(sub.iccId)}")
                    sb.appendLine("  电话号码: ${maskSensitive(sub.number)}")
                    sb.appendLine("  SIM插槽索引: ${sub.simSlotIndex}")
                    sb.appendLine("  SIM类型: ${if (sub.isEmbedded) "eSIM" else "物理SIM"}")
                }
            }
        } catch (e: SecurityException) {
            sb.appendLine("  ⚠️ 权限不足，无法获取订阅信息")
        }

        sb.appendLine("\n--- 当前网络 ---")
        sb.appendLine("网络运营商: ${tm.networkOperatorName ?: "N/A"}")
        sb.appendLine("网络运营商代码: ${tm.networkOperator ?: "N/A"}")
        sb.appendLine("数据网络类型: ${getNetworkTypeName(tm.dataNetworkType)}")
        sb.appendLine("语音网络类型: ${getNetworkTypeName(tm.voiceNetworkType)}")

        val phoneType = when (tm.phoneType) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            TelephonyManager.PHONE_TYPE_NONE -> "无 (WiFi Only)"
            else -> "未知"
        }
        sb.appendLine("手机类型: $phoneType")

        binding.tvSimInfo.text = sb.toString()
    }

    /**
     * Mask sensitive data: show only last 4 characters
     * e.g. "89860123456789012345" -> "**** **** **** 2345"
     */
    private fun maskSensitive(value: String?): String {
        if (value.isNullOrEmpty()) return "N/A"
        if (value.length <= 4) return "****"
        val last4 = value.takeLast(4)
        val masked = "*".repeat(value.length - 4)
        // Insert spaces every 4 chars for readability
        return (masked + last4).chunked(4).joinToString(" ")
    }

    private fun getNetworkTypeName(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+ (3G)"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
            TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "未知"
            else -> "类型 $type"
        }
    }
}
