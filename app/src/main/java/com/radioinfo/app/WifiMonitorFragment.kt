package com.radioinfo.app

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import java.util.Locale

class WifiMonitorFragment : Fragment() {
    private var tvInfo: TextView? = null
    private var chartSignal: ChartView? = null
    private var chartTraffic: ChartView? = null
    private val handler = Handler(Looper.getMainLooper())

    // Traffic tracking
    private var lastRx = 0L; private var lastTx = 0L; private var lastTime = 0L
    private val rxSpeeds = mutableListOf<Float>()
    private val txSpeeds = mutableListOf<Float>()

    // Signal tracking (connected AP)
    private val signalHistory = mutableListOf<Float>()

    private val refresh = object : Runnable {
        override fun run() { loadData(); handler.postDelayed(this, 1000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(R.layout.fragment_wifi_monitor, container, false)
            tvInfo = v.findViewById(R.id.tvWifiMonitor)
            chartSignal = v.findViewById(R.id.chartWifiSignal)
            chartTraffic = v.findViewById(R.id.chartWifiTraffic)
            chartSignal?.setYRange(-100f, -20f, "dBm")
            chartSignal?.setMaxPoints(120)
            chartTraffic?.setYRange(0f, 500f, "KB/s")
            chartTraffic?.setMaxPoints(120)
            v.findViewById<View>(R.id.btnRefresh)?.setOnClickListener { loadData() }
            v
        } catch (e: Exception) { Log.e("RadioInfo", "WifiMonView", e); null }
    }

    override fun onResume() { super.onResume(); lastRx = TrafficStats.getTotalRxBytes(); lastTx = TrafficStats.getTotalTxBytes(); lastTime = System.currentTimeMillis(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    override fun onDestroyView() {
        handler.removeCallbacks(refresh)
        tvInfo = null
        chartSignal = null
        chartTraffic = null
        super.onDestroyView()
    }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            val sb = StringBuilder()
            sb.appendLine("=== WiFi 全频段监控 ===")
            sb.appendLine("")

            // Connection info
            sb.appendLine("[连接状态]")
            sb.appendLine("  WiFi开关: ${if (wm.wifiState == 3) "已开启" else "已关闭"}")
            sb.appendLine("  连接状态: ${if (isWifi) "已连接" else "未连接WiFi"}")

            try {
                val ci = wm.connectionInfo
                if (ci != null && ci.networkId != -1) {
                    sb.appendLine("  网络名称(SSID): ${ci.ssid ?: "隐藏网络"}")
                    sb.appendLine("  MAC地址(BSSID): ${ci.bssid ?: "无"}")
                    sb.appendLine("  频率: ${ci.frequency} MHz → 信道 CH${freq2ch(ci.frequency)}")

                    sb.appendLine("  链路速率: ${ci.linkSpeed} Mbps (协商速度)")
                    sb.appendLine("  信号强度: ${ci.rssi} dBm (${rssiDesc(ci.rssi)})")
                    sb.appendLine("  信号等级: ${WifiManager.calculateSignalLevel(ci.rssi, 5)}/4")
                    val wifiStd = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        when (ci.wifiStandard) {
                            7 -> "WiFi 7 (802.11be)"
                            6 -> "WiFi 6/6E (802.11ax)"
                            5 -> "WiFi 5 (802.11ac)"
                            4 -> "WiFi 4 (802.11n)"
                            3 -> "11g"
                            2 -> "11b"
                            else -> "未知"
                        }
                    } else {
                        "Android 11 以下不可用"
                    }
                    sb.appendLine("  WiFi标准: $wifiStd")

                    // Traffic
                    val now = System.currentTimeMillis()
                    val elapsed = (now - lastTime).coerceAtLeast(1)
                    val currentRx = TrafficStats.getTotalRxBytes()
                    val currentTx = TrafficStats.getTotalTxBytes()
                    val rxBytes = RadioDataUtils.nonNegativeDelta(currentRx, lastRx) ?: 0L
                    val txBytes = RadioDataUtils.nonNegativeDelta(currentTx, lastTx) ?: 0L
                    val rxKB = (rxBytes * 1000.0 / elapsed / 1024).toFloat()
                    val txKB = (txBytes * 1000.0 / elapsed / 1024).toFloat()
                    lastRx = currentRx; lastTx = currentTx; lastTime = now

                    rxSpeeds.add(rxKB); txSpeeds.add(txKB)
                    if (rxSpeeds.size > 120) { rxSpeeds.removeAt(0); txSpeeds.removeAt(0) }

                    signalHistory.add(ci.rssi.toFloat())
                    if (signalHistory.size > 120) signalHistory.removeAt(0)

                    sb.appendLine("")
                    sb.appendLine("[实时流量]")
                    sb.appendLine("  下载速度: ${formatSpeed(rxKB)}")
                    sb.appendLine("  上传速度: ${formatSpeed(txKB)}")
                    sb.appendLine("  累计下载: ${formatBytes(RadioDataUtils.nonNegativeCounter(currentRx))}")
                    sb.appendLine("  累计上传: ${formatBytes(RadioDataUtils.nonNegativeCounter(currentTx))}")
                }
            } catch (_: Exception) {}

            // All nearby APs by band
            try {
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasNearbyPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED
                val scans = if (!hasFineLocation || !hasNearbyPermission) {
                    sb.appendLine("  需要位置权限才能读取附近 WiFi")
                    emptyList()
                } else {
                    wm.scanResults ?: emptyList()
                }
                val band24 = scans.filter { it.frequency in 2400..2500 }
                val band5 = scans.filter { it.frequency in 5000..5900 }
                val band6 = scans.filter { it.frequency in 5925..7125 }

                sb.appendLine("")
                sb.appendLine("[附近WiFi全频段] 共${scans.size}个")
                sb.appendLine("")

                // 2.4GHz band
                sb.appendLine("── 2.4GHz频段 (CH1-14, 穿墙好/速度慢) ──")
                if (band24.isNotEmpty()) {
                    for (ch in 1..14) {
                        val aps = band24.filter { freq2ch(it.frequency) == ch }
                        if (aps.isNotEmpty()) {
                            val strongest = aps.maxByOrNull { it.level }
                            val bars = signalBar(strongest?.level ?: -100)
                            sb.appendLine("  CH%-2d: %d个AP 信号:%+4ddBm %s".format(ch, aps.size, strongest?.level ?: 0, bars))
                        }
                    }
                } else {
                    sb.appendLine("  无2.4GHz信号")
                }

                // 5GHz band
                sb.appendLine("")
                sb.appendLine("── 5GHz频段 (CH36-165, 速度快/穿墙弱) ──")
                if (band5.isNotEmpty()) {
                    val chGroups = mapOf("36-48" to 36..48, "52-64" to 52..64, "100-140" to 100..140, "149-165" to 149..165)
                    for ((name, range) in chGroups) {
                        val aps = band5.filter { freq2ch(it.frequency) in range }
                        if (aps.isNotEmpty()) {
                            val strongest = aps.maxByOrNull { it.level }
                            val bars = signalBar(strongest?.level ?: -100)
                            sb.appendLine("  CH$name: %d个AP 信号:%+4ddBm %s".format(aps.size, strongest?.level ?: 0, bars))
                        }
                    }
                } else {
                    sb.appendLine("  无5GHz信号")
                }

                // 6GHz band
                if (band6.isNotEmpty()) {
                    sb.appendLine("")
                    sb.appendLine("── 6GHz频段 (WiFi 6E/7, 最快/覆盖最小) ──")
                    sb.appendLine("  共${band6.size}个AP")
                }
            } catch (_: Exception) {}

            sb.appendLine("")
            sb.appendLine("[频段说明]")
            sb.appendLine("  2.4GHz: 穿墙强, 速度慢(最高~150Mbps)")
            sb.appendLine("  5GHz:   穿墙弱, 速度快(最高~1Gbps)")
            sb.appendLine("  6GHz:   穿墙最弱, 速度最快(WiFi6E/7)")
            sb.appendLine("  信号等级: ▂▃▅▇█=极强 ▂▃▅▇░=强 ▂▃▅░░=良 ▂▃░░░=弱")

            tvInfo?.text = sb.toString()

            // Update charts
            chartSignal?.setLines(listOf(signalHistory))
            chartTraffic?.setLines(listOf(rxSpeeds, txSpeeds))
            val maxSpeed = (rxSpeeds + txSpeeds).maxOrNull()?.coerceAtLeast(50f) ?: 50f
            chartTraffic?.setYRange(0f, maxSpeed * 1.3f, "KB/s")

        } catch (e: Exception) { Log.e("RadioInfo", "WifiMonLoad", e) }
    }

    private fun freq2ch(f: Int) = when { f in 2412..2484 -> (f-2407)/5; f in 5170..5825 -> (f-5000)/5; f in 5955..7115 -> (f-5950)/5; else -> 0 }
    private fun rssiDesc(r: Int) = when { r >= -50 -> "极强"; r >= -60 -> "强"; r >= -70 -> "良好"; r >= -80 -> "弱"; else -> "很弱" }
    private fun signalBar(r: Int) = when { r >= -50 -> "▂▃▅▇█"; r >= -60 -> "▂▃▅▇░"; r >= -70 -> "▂▃▅░░"; r >= -80 -> "▂▃░░░"; else -> "▂░░░░" }

    private fun formatSpeed(kb: Float) = when { kb >= 1024 -> String.format(Locale.US, "%.1f MB/s", kb / 1024); kb >= 1 -> String.format(Locale.US, "%.1f KB/s", kb); else -> String.format(Locale.US, "%.0f B/s", kb * 1024) }
    private fun formatBytes(bytes: Long?) = when {
        bytes == null -> "不可用"
        bytes >= 1073741824 -> String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)
        bytes >= 1048576 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
