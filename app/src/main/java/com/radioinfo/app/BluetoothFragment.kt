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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

class BluetoothFragment : Fragment(), SensorEventListener {
    private var tvInfo: TextView? = null
    private var targetSpinner: Spinner? = null
    private var locationButton: MaterialButton? = null
    private val handler = Handler(Looper.getMainLooper())
    private val devices = linkedMapOf<String, DeviceRecord>()
    private val targetAddresses = mutableListOf<String>()
    private var targetAdapter: ArrayAdapter<String>? = null
    private var classicReceiver: BroadcastReceiver? = null
    private var leScanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanning = false
    private var advertising = false
    private var locating = false
    private var targetAddress: String? = null
    private var targetRssi: Int? = null
    private var targetTxPower: Int? = null
    private val targetRssiSamples = ArrayDeque<Int>()
    private var sensorManager: SensorManager? = null
    private var motionSensor: Sensor? = null
    private var lastMotionAt = 0L
    private var gravityInitialized = false
    private val gravity = FloatArray(3)
    private var updatingTargetSelector = false
    private var pendingEnableAction: (() -> Unit)? = null
    private var pendingPermissionAction: (() -> Unit)? = null

    private val scanTimeout: Runnable = object : Runnable {
        override fun run() {
            if (locating) {
                render("目标定位仍在进行，请移动设备以获得信号变化")
                handler.postDelayed(this, SCAN_DURATION_MS)
            } else {
                stopScan()
            }
        }
    }
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
            if (locating) stopLocation() else stopScan()
            render("BLE 扫描失败: ${scanErrorLabel(errorCode)}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bluetooth, container, false).also { view ->
        tvInfo = view.findViewById(R.id.tvBluetoothInfo)
        targetSpinner = view.findViewById(R.id.spinnerBluetoothTarget)
        locationButton = view.findViewById(R.id.btnBluetoothLocate)
        targetAdapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mutableListOf()
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            targetSpinner?.adapter = adapter
        }
        targetSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingTargetSelector) return
                val selected = targetAddresses.getOrNull(position)
                if (selected != targetAddress && !locating) resetLocationMeasurement()
                targetAddress = selected
                updateLocationControls()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                targetAddress = null
                updateLocationControls()
            }
        }
        view.findViewById<View>(R.id.btnBluetoothScan).setOnClickListener { toggleScan() }
        view.findViewById<View>(R.id.btnBluetoothAdvertise).setOnClickListener { toggleAdvertising() }
        locationButton?.setOnClickListener { toggleLocation() }
        view.findViewById<View>(R.id.btnBluetoothSendFile).setOnClickListener { chooseFile() }
        updateLocationControls()
        render()
    }

    override fun onDestroyView() {
        pendingEnableAction = null
        pendingPermissionAction = null
        stopLocation()
        stopScan()
        stopAdvertising()
        tvInfo = null
        targetSpinner = null
        targetAdapter = null
        locationButton = null
        super.onDestroyView()
    }

    private fun toggleScan() {
        if (scanning) {
            if (locating) stopLocation() else stopScan()
        } else {
            ensureBluetooth(BLUETOOTH_SCAN_REQUEST) { startScan() }
        }
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
        if (!locating) {
            devices.clear()
            targetAddresses.clear()
            targetAddress = null
            targetAdapter?.clear()
            targetAdapter?.notifyDataSetChanged()
        }
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
            if (locating) stopLocation() else stopScan()
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
        refreshTargetSelector()
        updateTargetObservation(address, rssi, txPower)
        render()
    }

    private fun refreshTargetSelector() {
        val previous = targetAddress
        val ordered = devices.values.sortedByDescending { it.rssi }
        targetAddresses.clear()
        targetAddresses += ordered.map { it.address }
        updatingTargetSelector = true
        targetAdapter?.clear()
        targetAdapter?.addAll(ordered.map { device ->
            "${device.name} (${BluetoothDataUtils.maskAddress(device.address)})"
        })
        targetAdapter?.notifyDataSetChanged()
        val selectedIndex = targetAddresses.indexOf(previous).takeIf { it >= 0 } ?: 0
        targetAddress = targetAddresses.getOrNull(selectedIndex)
        targetSpinner?.setSelection(selectedIndex, false)
        updatingTargetSelector = false
        updateLocationControls()
    }

    private fun updateTargetObservation(address: String, rssi: Int, txPower: Int?) {
        if (!locating || address != targetAddress || rssi !in -127..-1) return
        targetRssi = rssi
        targetTxPower = txPower ?: targetTxPower
        targetRssiSamples.addLast(rssi)
        while (targetRssiSamples.size > MAX_RSSI_SAMPLES) targetRssiSamples.removeFirst()
    }

    private fun render(status: String? = null) {
        val text = buildString {
            appendLine("=== 蓝牙广播与附近设备 ===")
            appendLine("扫描: ${if (scanning) "进行中" else "已停止"} | 本机广播: ${if (advertising) "开启" else "关闭"}")
            status?.let { appendLine(it) }
            appendLine("")
            appendLine("距离为 RSSI 粗略估算，会受墙体、手机方向和发射功率影响。")
            if (locating) appendLocationStatus(this)
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

    private fun appendLocationStatus(text: StringBuilder) {
        val target = targetAddress?.let { devices[it] }
        text.appendLine("")
        text.appendLine("=== 指定设备定位 ===")
        text.appendLine("目标: ${target?.name ?: "等待目标广播"}")
        val rssi = targetRssi
        if (rssi == null || targetRssiSamples.isEmpty()) {
            text.appendLine("尚未收到目标广播，请缓慢移动设备。")
            if (motionSensor == null) text.appendLine("未检测到加速度传感器，无法判断移动状态。")
            return
        }
        val samples = targetRssiSamples.toList()
        val averageRssi = samples.average().roundToInt()
        val distance = BluetoothDataUtils.estimateDistanceMeters(averageRssi, targetTxPower)
        text.appendLine("目标 RSSI: $rssi dBm | 平滑 RSSI: $averageRssi dBm")
        text.appendLine("目标距离: ${BluetoothDataUtils.formatDistance(distance)}")
        val isMoving = lastMotionAt > 0L &&
            SystemClock.elapsedRealtime() - lastMotionAt < MOTION_ACTIVE_WINDOW_MS
        text.appendLine("移动检测: ${if (isMoving) "已检测到移动" else "请移动设备"}")
        if (!isMoving) {
            text.appendLine("提示: 请缓慢移动并观察信号变化，信号增强通常表示正在接近目标。")
            return
        }
        if (samples.size >= MIN_TREND_SAMPLES) {
            val midpoint = samples.size / 2
            val olderAverage = samples.take(midpoint).average()
            val recentAverage = samples.drop(midpoint).average()
            val trend = recentAverage - olderAverage
            val direction = when {
                trend > 2.0 -> "信号增强，继续当前移动方向"
                trend < -2.0 -> "信号减弱，请返回并换方向"
                else -> "变化不明显，请换方向"
            }
            text.appendLine("方向提示: $direction")
        } else {
            text.appendLine("方向提示: 继续缓慢移动，等待更多 RSSI 样本。")
        }
    }

    private fun toggleLocation() {
        if (locating) {
            stopLocation()
            return
        }
        if (targetAddress == null) {
            render("请先扫描并选择一个目标蓝牙设备")
            return
        }
        ensureBluetooth(BLUETOOTH_SCAN_REQUEST) { startLocation() }
    }

    private fun startLocation() {
        if (targetAddress == null) {
            render("请先扫描并选择一个目标蓝牙设备")
            return
        }
        locating = true
        resetLocationMeasurement()
        startMotionTracking()
        updateLocationControls()
        if (scanning) {
            render("目标定位已开始，请缓慢移动设备")
        } else {
            startScan()
        }
    }

    private fun stopLocation() {
        if (!locating) return
        locating = false
        stopMotionTracking()
        if (scanning) stopScan() else render("目标定位已停止")
        updateLocationControls()
    }

    private fun resetLocationMeasurement() {
        targetRssi = null
        targetTxPower = null
        targetRssiSamples.clear()
        lastMotionAt = 0L
    }

    private fun updateLocationControls() {
        locationButton?.text = if (locating) "停止定位" else "定位选中设备"
        locationButton?.isEnabled = locating || targetAddress != null
        targetSpinner?.isEnabled = !locating && targetAddresses.isNotEmpty()
    }

    private fun startMotionTracking() {
        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        motionSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gravityInitialized = false
        if (motionSensor != null) {
            sensorManager?.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopMotionTracking() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        motionSensor = null
        gravityInitialized = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        if (values.size < 3) return
        val magnitude = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        } else {
            if (!gravityInitialized) {
                values.copyInto(gravity)
                gravityInitialized = true
                return
            }
            val alpha = 0.8f
            for (index in 0..2) {
                gravity[index] = alpha * gravity[index] + (1f - alpha) * values[index]
            }
            val linearX = values[0] - gravity[0]
            val linearY = values[1] - gravity[1]
            val linearZ = values[2] - gravity[2]
            sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
        }
        if (magnitude >= MOTION_THRESHOLD) lastMotionAt = SystemClock.elapsedRealtime()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

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
            pendingPermissionAction = action
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
            pendingPermissionAction = null
            render("蓝牙权限未授予，相关功能不可用")
            return
        }
        pendingPermissionAction?.also { action ->
            pendingPermissionAction = null
            action()
        } ?: if (requestCode == BLUETOOTH_SCAN_REQUEST) {
            startScan()
        } else {
            startAdvertising()
        }
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
            pendingPermissionAction = null
            if (locating) stopLocation()
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
        private const val MAX_RSSI_SAMPLES = 8
        private const val MIN_TREND_SAMPLES = 4
        private const val MOTION_THRESHOLD = 1.0f
        private const val MOTION_ACTIVE_WINDOW_MS = 2_000L
        private val APP_SERVICE_UUID: UUID = UUID.fromString("7b2f2d6b-1b45-4e4c-9a45-2d1c4f9d1404")
    }
}
