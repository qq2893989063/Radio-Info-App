package com.radioinfo.app

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.radioinfo.app.databinding.FragmentWifiBinding

class WifiChannelFragment : Fragment() {

    private var _binding: FragmentWifiBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadWifiInfo()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWifiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRefresh.setOnClickListener { triggerScanAndLoad() }
    }

    override fun onResume() { super.onResume(); handler.post(refreshRunnable) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refreshRunnable) }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun triggerScanAndLoad() {
        try {
            val wm = requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.startScan()
        } catch (_: Exception) {}
        loadWifiInfo()
    }

    private fun loadWifiInfo() {
        val wm = requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        val sb = StringBuilder()

        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("  📶 WiFi 信道状态")
        sb.appendLine("═══════════════════════════════════")

        // WiFi State
        sb.appendLine("\n■ WiFi 状态:")
        val wifiState = when (wm.wifiState) {
            0 -> "❌ WiFi 正在关闭"
            1 -> "❌ WiFi 已关闭"
            2 -> "🟡 WiFi 正在开启"
            3 -> "✅ WiFi 已开启"
            4 -> "❓ 未知"
            else -> "❓ ${wm.wifiState}"
        }
        sb.appendLine("  WiFi开关状态: $wifiState")

        // Connected AP info
        sb.appendLine("\n■ 当前连接的AP:")
        try {
            @Suppress("DEPRECATION")
            val connInfo = wm.connectionInfo
            if (connInfo != null && connInfo.networkId != -1) {
                sb.appendLine("  SSID: ${connInfo.ssid ?: "N/A"}")
                sb.appendLine("  BSSID: ${connInfo.bssid ?: "N/A"}")
                sb.appendLine("  频率: ${connInfo.frequency} MHz")
                val channel = frequencyToChannel(connInfo.frequency)
                sb.appendLine("  信道: CH $channel")
                sb.appendLine("  链路速度: ${connInfo.linkSpeed} Mbps")
                sb.appendLine("  RSSI: ${connInfo.rssi} dBm")
                sb.appendLine("  信号等级: ${getWifiSignalLevel(connInfo.rssi)}/4")
                sb.appendLine("  网络ID: ${connInfo.networkId}")

                val wifiStandard = when {
                    connInfo.wifiStandard >= 6 -> "WiFi 6/6E (802.11ax)"
                    connInfo.wifiStandard == 5 -> "WiFi 5 (802.11ac)"
                    connInfo.wifiStandard == 4 -> "WiFi 4 (802.11n)"
                    connInfo.wifiStandard == 3 -> "WiFi 3 (802.11g)"
                    connInfo.wifiStandard == 2 -> "WiFi 2 (802.11b)"
                    connInfo.wifiStandard == 7 -> "WiFi 7 (802.11be)"
                    else -> "标准 #${connInfo.wifiStandard}"
                }
                sb.appendLine("  WiFi标准: $wifiStandard")
            } else {
                sb.appendLine("  ⚠️ 未连接WiFi")
            }
        } catch (e: Exception) {
            sb.appendLine("  ⚠️ 无法获取连接信息: ${e.message}")
        }

        // Scan Results
        sb.appendLine("\n■ 扫描到的AP (${wm.scanResults?.size ?: 0} 个):")
        sb.appendLine("  ┌──────┬──────────────────────┬──────┬────────┬──────┬────────┐")
        sb.appendLine("  │ 信道 │ SSID                 │ 频率 │ RSSI   │ 标准 │ 安全   │")
        sb.appendLine("  ├──────┼──────────────────────┼──────┼────────┼──────┼────────┤")

        try {
            val scanResults: List<ScanResult> = wm.scanResults ?: emptyList()
            val sortedResults = scanResults.sortedBy { it.frequency }

            val channelCount = mutableMapOf<Int, Int>()
            for (sr in sortedResults) {
                val ch = frequencyToChannel(sr.frequency)
                channelCount[ch] = (channelCount[ch] ?: 0) + 1
            }

            for (sr in sortedResults.take(30)) {
                val ch = frequencyToChannel(sr.frequency)
                val ssid = if (sr.SSID.isNullOrEmpty()) "<隐藏>" else sr.SSID.take(20)
                val rssi = sr.level
                val security = getSecurityType(sr)
                val wifiStd = getWifiStandard(sr)

                sb.appendLine("  │ CH%-3d│ %-20s │ %4d │ %+5d dB│ %-4s │ %-6s │".format(
                    ch, ssid, sr.frequency, rssi, wifiStd, security
                ))
            }
            sb.appendLine("  └──────┴──────────────────────┴──────┴────────┴──────┴────────┘")

            // Channel utilization summary
            sb.appendLine("\n■ 信道占用统计:")
            val sortedChannels = channelCount.toSortedMap()
            for ((ch, count) in sortedChannels) {
                val bar = "█".repeat(count)
                val band = if (ch <= 14) "2.4G" else "5G"
                sb.appendLine("  CH%-3d [%-2s] %d个AP %s".format(ch, band, count, bar))
            }

        } catch (e: SecurityException) {
            sb.appendLine("  ⚠️ 权限不足，无法扫描WiFi")
        }

        binding.tvWifiInfo.text = sb.toString()
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2407) / 5
        freq in 5170..5825 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> 0
    }

    private fun getWifiSignalLevel(rssi: Int): Int {
        return WifiManager.calculateSignalLevel(rssi, 5)
    }

    private fun getSecurityType(sr: ScanResult): String {
        val caps = sr.capabilities ?: ""
        return when {
            caps.contains("WPA3") -> "WPA3"
            caps.contains("WPA2") && caps.contains("WPA-") -> "WPA2/1"
            caps.contains("WPA2") || caps.contains("RSN") -> "WPA2"
            caps.contains("WPA") -> "WPA"
            caps.contains("WEP") -> "WEP"
            caps.contains("OWE") -> "OWE"
            else -> "OPEN"
        }
    }

    private fun getWifiStandard(sr: ScanResult): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return when (sr.wifiStandard) {
                7 -> "7"
                6 -> "6"
                5 -> "5"
                4 -> "4"
                3 -> "g"
                2 -> "b"
                else -> "?"
            }
        }
        return "?"
    }
}
