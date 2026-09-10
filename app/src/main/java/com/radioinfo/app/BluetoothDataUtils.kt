package com.radioinfo.app

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import java.util.Locale
import kotlin.math.pow

internal object BluetoothDataUtils {
    fun estimateDistanceMeters(rssi: Int, txPower: Int? = null): Double? {
        if (rssi >= 0 || rssi < -127) return null
        val referencePower = txPower?.takeIf { it in -127..0 } ?: -59
        val ratio = if (rssi >= referencePower) {
            (referencePower - rssi) / 20.0
        } else {
            (referencePower - rssi) / 10.0
        }
        return 10.0.pow(ratio).coerceIn(0.1, 1000.0)
    }

    fun formatDistance(distanceMeters: Double?): String = when {
        distanceMeters == null -> "不可估算"
        distanceMeters < 1.0 -> "约 %.1f m".format(Locale.US, distanceMeters)
        distanceMeters < 10.0 -> "约 %.1f m".format(Locale.US, distanceMeters)
        else -> "约 %.0f m".format(Locale.US, distanceMeters)
    }

    fun maskAddress(address: String?): String {
        if (address.isNullOrBlank()) return "地址不可用"
        val parts = address.split(":")
        return if (parts.size == 6) {
            "${parts[0]}:${parts[1]}:${parts[2]}:**:**:**"
        } else {
            "地址已隐藏"
        }
    }

    fun deviceTypeLabel(type: Int): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE 低功耗"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "双模 Classic + BLE"
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "经典蓝牙"
        else -> "未知类型"
    }

    fun classicProtocolLabel(deviceClass: BluetoothClass?): String {
        val major = deviceClass?.majorDeviceClass ?: return "RFCOMM/经典蓝牙"
        val category = when (major) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "音频/视频"
            BluetoothClass.Device.Major.COMPUTER -> "计算机"
            BluetoothClass.Device.Major.PHONE -> "手机"
            BluetoothClass.Device.Major.PERIPHERAL -> "外设"
            BluetoothClass.Device.Major.NETWORKING -> "网络设备"
            BluetoothClass.Device.Major.HEALTH -> "健康设备"
            BluetoothClass.Device.Major.IMAGING -> "影像设备"
            BluetoothClass.Device.Major.TOY -> "玩具"
            else -> "其他设备"
        }
        return "$category / RFCOMM"
    }
}
