package com.radioinfo.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.UUID

class BluetoothFragment : Fragment() {
    private var tvInfo: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val devices = linkedMapOf<String, DeviceRecord>()
    private var classicReceiver: BroadcastReceiver? = null
    private var leScanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanning = false
    private var advertising = false
    private var pendingEnableAction: (() -> Unit)? = null

    private val scanTimeout = Runnable { stopScan() }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) launchBluetoothShare(uri)
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val name = record?.deviceName ?: safeDeviceName(result.device) ?: "未知 BLE 设备"
            val services = record?.serviceUuids?.joinToString(",") { it.uuid.toString().take(8) }
            updateDevice(
                result.device,
                name,
                result.rssi,
                BluetoothDataUtils.deviceTypeLabel(safeDeviceType(result.device)),
                "BLE / GATT${services?.let { " [$it]" } ?: ""}",
                record?.txPowerLevel?.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
            )
        }

        override fun onScanFailed(errorCode: Int) {
            stopScan()
            render("BLE 扫描失败: ${scanErrorLabel(errorCode)}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bluetooth, container, false).also { view ->
        tvInfo = view.findViewById(R.id.tvBluetoothInfo)
        view.findViewById<View>(R.id.btnBluetoothScan).setOnClickListener { toggleScan() }
        view.findViewById<View>(R.id.btnBluetoothAdvertise).setOnClickListener { toggleAdvertising() }
        view.findViewById<View>(R.id.btnBluetoothSendFile).setOnClickListener { chooseFile() }
        render()
    }

    override fun onDestroyView() {
        pendingEnableAction = null
        stopScan()
        stopAdvertising()
        tvInfo = null
        super.onDestroyView()
    }

    private fun toggleScan() {
        if (scanning) stopScan() else ensureBluetooth(BLUETOOTH_SCAN_REQUEST) { startScan() }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val adapter = bluetoothAdapter() ?: run {
            render("设备不支持蓝牙")
            return
        }
        if (!adapter.isEnabled) {
            requestBluetoothEnable { startScan() }
            return
        }
        devices.clear()
        scanning = true
        render("正在扫描经典蓝牙和 BLE 广播...")
        registerClassicReceiver()
        try {
            adapter.cancelDiscovery()
            adapter.startDiscovery()
            leScanner = adapter.bluetoothLeScanner
            leScanner?.startScan(leScanCallback)
            handler.removeCallbacks(scanTimeout)
            handler.postDelayed(scanTimeout, SCAN_DURATION_MS)
        } catch (_: SecurityException) {
            stopScan()
            render("蓝牙扫描权限不足，请在系统设置中允许附近设备权限")
        }
    }

    private fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        try {
            leScanner?.stopScan(leScanCallback)
            bluetoothAdapter()?.cancelDiscovery()
        } catch (_: SecurityException) {
            // Permission may be revoked while the page is open.
        }
        leScanner = null
        unregisterClassicReceiver()
        if (scanning) {
            scanning = false
            render()
        }
    }

    private fun registerClassicReceiver() {
        if (classicReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = parcelableDevice(intent) ?: return
                        val name = safeDeviceName(device) ?: "未知经典蓝牙设备"
                        updateDevice(
                            device,
                            name,
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                            BluetoothDataUtils.deviceTypeLabel(safeDeviceType(device)),
                            BluetoothDataUtils.classicProtocolLabel(safeBluetoothClass(device)),
                            null
                        )
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> render()
                }
            }
        }
        classicReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // The Bluetooth system app sends these discovery broadcasts from another UID.
        ContextCompat.registerReceiver(requireContext(), receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun unregisterClassicReceiver() {
        classicReceiver?.let {
            runCatching { requireContext().unregisterReceiver(it) }
        }
        classicReceiver = null
    }

    private fun updateDevice(
        device: BluetoothDevice,
        name: String,
        rssi: Int,
        type: String,
        protocol: String,
        txPower: Int?
    ) {
        if (rssi == Short.MIN_VALUE.toInt()) return
        val address = runCatching { device.address }.getOrNull() ?: name
        devices[address] = DeviceRecord(
            name = name.take(40),
            address = address,
            rssi = rssi,
            type = type,
            protocol = protocol,
            txPower = txPower
        )
        render()
    }

    private fun render(status: String? = null) {
        val text = buildString {
            appendLine("=== 蓝牙广播与附近设备 ===")
            appendLine("扫描: ${if (scanning) "进行中" else "已停止"} | 本机广播: ${if (advertising) "开启" else "关闭"}")
            status?.let { appendLine(it) }
            appendLine("")
            appendLine("距离为 RSSI 粗略估算，会受墙体、手机方向和发射功率影响。")
            appendLine("")
            if (devices.isEmpty()) {
                appendLine("暂无设备。点击扫描后，将同时监听经典蓝牙和 BLE 广播。")
            } else {
                appendLine("附近设备 (${devices.size})")
                appendLine("名称 | RSSI | 距离 | 类型 | 协议 | 地址")
                appendLine("────────────────────────────────────────")
                devices.values.sortedByDescending { it.rssi }.forEach { device ->
                    appendLine(
                        "${device.name} | ${device.rssi} dBm | " +
                            "${BluetoothDataUtils.formatDistance(BluetoothDataUtils.estimateDistanceMeters(device.rssi, device.txPower))} | " +
                            "${device.type} | ${device.protocol} | ${BluetoothDataUtils.maskAddress(device.address)}"
                    )
                }
            }
            appendLine("")
            appendLine("文件发送: 选择文件后打开系统分享器，再选择蓝牙设备。")
            if (advertising) appendLine("本机正在广播 Radio Info App v1.4 服务标识。")
        }
        tvInfo?.text = text
    }

    private fun toggleAdvertising() {
        if (advertising) {
            stopAdvertising()
        } else {
            ensureBluetooth(BLUETOOTH_ADVERTISE_REQUEST) { startAdvertising() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val adapter = bluetoothAdapter() ?: run {
            render("设备不支持蓝牙广播")
            return
        }
        if (!adapter.isEnabled) {
            requestBluetoothEnable { startAdvertising() }
            return
        }
        val localAdvertiser = adapter.bluetoothLeAdvertiser
        if (localAdvertiser == null) {
            render("设备不支持 BLE 广播")
            return
        }
        val serviceUuid = ParcelUuid(APP_SERVICE_UUID)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()
        // A legacy BLE advertisement is limited to 31 bytes. Keep the UUID and
        // TX power in the primary packet and put the version marker in the scan response.
        val data = AdvertiseData.Builder()
            .addServiceUuid(serviceUuid)
            .setIncludeTxPowerLevel(true)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .addServiceData(serviceUuid, byteArrayOf(1, 4))
            .build()
        advertiser = localAdvertiser
        try {
            localAdvertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (_: SecurityException) {
            render("蓝牙广播权限不足，请在系统设置中允许附近设备权限")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            advertising = true
            render("本机 BLE 广播已开启")
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            render("蓝牙广播失败: ${advertiseErrorLabel(errorCode)}")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: SecurityException) {
            // Permission may be revoked while the page is open.
        }
        advertiser = null
        if (advertising) {
            advertising = false
            render()
        }
    }

    private fun chooseFile() {
        openDocument.launch(arrayOf("*/*"))
    }

    private fun launchBluetoothShare(uri: Uri) {
        val mimeType = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "选择蓝牙设备发送文件"))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "系统没有可用的文件分享应用", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureBluetooth(requestCode: Int, action: () -> Unit) {
        val missing = bluetoothPermissions(requestCode).filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), requestCode)
        } else {
            action()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != BLUETOOTH_SCAN_REQUEST && requestCode != BLUETOOTH_ADVERTISE_REQUEST) return
        if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            render("蓝牙权限未授予，相关功能不可用")
            return
        }
        if (requestCode == BLUETOOTH_SCAN_REQUEST) startScan() else startAdvertising()
    }

    @Deprecated("Use Activity Result APIs for new code")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH && resultCode == Activity.RESULT_OK) {
            pendingEnableAction?.also { action ->
                pendingEnableAction = null
                action()
            }
        } else if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            pendingEnableAction = null
            render("蓝牙未开启，相关功能不可用")
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        requireContext().getSystemService(BluetoothManager::class.java)?.adapter

    private fun bluetoothPermissions(requestCode: Int): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (requestCode == BLUETOOTH_ADVERTISE_REQUEST) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        }
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun requestBluetoothEnable(action: () -> Unit) {
        pendingEnableAction = action
        try {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH)
        } catch (_: SecurityException) {
            pendingEnableAction = null
            render("无法请求开启蓝牙，请检查蓝牙权限")
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? = runCatching { device.name }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun safeDeviceType(device: BluetoothDevice): Int = runCatching { device.type }
        .getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)

    @SuppressLint("MissingPermission")
    private fun safeBluetoothClass(device: BluetoothDevice) = runCatching { device.bluetoothClass }.getOrNull()

    private fun parcelableDevice(intent: Intent): BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

    private fun scanErrorLabel(code: Int) = when (code) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "扫描已在进行"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "系统注册失败"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "系统内部错误"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "设备不支持 BLE 扫描"
        else -> "错误码 $code"
    }

    private fun advertiseErrorLabel(code: Int) = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "广播已在进行"
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "广播数据过大"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "设备不支持 BLE 广播"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "广播实例已达上限"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "系统内部错误"
        else -> "错误码 $code"
    }

    private data class DeviceRecord(
        val name: String,
        val address: String,
        val rssi: Int,
        val type: String,
        val protocol: String,
        val txPower: Int?
    )

    companion object {
        private const val BLUETOOTH_SCAN_REQUEST = 2401
        private const val BLUETOOTH_ADVERTISE_REQUEST = 2402
        private const val REQUEST_ENABLE_BLUETOOTH = 2403
        private const val SCAN_DURATION_MS = 12_000L
        private val APP_SERVICE_UUID: UUID = UUID.fromString("7b2f2d6b-1b45-4e4c-9a45-2d1c4f9d1404")
    }
}
