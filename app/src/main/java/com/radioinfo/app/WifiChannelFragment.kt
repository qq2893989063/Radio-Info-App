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
        override fun run() { loadWifiInfo(); handler.postDelayed(this, 5000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWifiBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRefresh.setOnClickListener {
            try {
                val wm = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wm.startScan()
            } catch (_: Exception) {}
            loadWifiInfo()
        }
    }
    override fun onResume() { super.onResume(); handler.post(refreshRunnable) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refreshRunnable) }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun loadWifiInfo() {
        val wm = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val sb = StringBuilder()
        sb.appendLine("=== WiFi 信道状态 ===")
        sb.appendLine("")

        val ws = when(wm.getWifiState()) { 3->"已开启"; 1->"已关闭"; else->"状态:${wm.getWifiState()}" }
        sb.appendLine("[WiFi状态] $ws")

        sb.appendLine("")
        sb.appendLine("[当前连接的AP]")
        try {
            @Suppress("DEPRECATION")
            val ci = wm.getConnectionInfo()
            if (ci != null && ci.getNetworkId() != -1) {
                sb.appendLine("  SSID: ${ci.getSSID() ?: "N/A"}")
                sb.appendLine("  BSSID: ${ci.getBSSID() ?: "N/A"}")
                sb.appendLine("  频率: ${ci.getFrequency()} MHz | 信道: CH${freq2ch(ci.getFrequency())}")
                sb.appendLine("  速率: ${ci.getLinkSpeed()} Mbps | RSSI: ${ci.getRssi()}dBm")
                sb.appendLine("  WiFi标准: ${wifiStd(ci.getWifiStandard())}")
            } else {
                sb.appendLine("  未连接WiFi")
            }
        } catch (e: Exception) { sb.appendLine("  无法获取: ${e.message}") }

        sb.appendLine("")
        sb.appendLine("[扫描到的AP]")
        try {
            val scans: List<ScanResult> = wm.getScanResults() ?: emptyList()
            sb.appendLine("  共 ${scans.size} 个AP")
            sb.appendLine("  CH    SSID                  Freq   RSSI  安全")
            sb.appendLine("  ----  --------------------  -----  ----  ------")
            val chCount = mutableMapOf<Int, Int>()
            for (sr in scans.sortedBy { it.frequency }.take(25)) {
                val ch = freq2ch(sr.frequency)
                chCount[ch] = (chCount[ch] ?: 0) + 1
                val ssid = if (sr.SSID.isNullOrEmpty()) "<隐藏>" else sr.SSID.take(20)
                val sec = when {
                    sr.capabilities?.contains("WPA3") == true -> "WPA3"
                    sr.capabilities?.contains("WPA2") == true -> "WPA2"
                    sr.capabilities?.contains("WPA") == true -> "WPA"
                    sr.capabilities?.contains("WEP") == true -> "WEP"
                    else -> "OPEN"
                }
                sb.appendLine("  CH%-3d %-20s %5d  %+4d  %s".format(ch, ssid, sr.frequency, sr.level, sec))
            }
            sb.appendLine("")
            sb.appendLine("[信道占用统计]")
            for ((ch, cnt) in chCount.toSortedMap()) {
                val band = if (ch <= 14) "2.4G" else "5G"
                sb.appendLine("  CH%-3d [%-2s] %d个AP %s".format(ch, band, cnt, "█".repeat(cnt)))
            }
        } catch (e: SecurityException) { sb.appendLine("  权限不足") }
        binding.tvWifiInfo.text = sb.toString()
    }

    private fun freq2ch(f: Int) = when(f) {
        in 2412..2484 -> (f-2407)/5
        in 5170..5825 -> (f-5000)/5
        in 5955..7115 -> (f-5950)/5
        else -> 0
    }
    private fun wifiStd(s: Int) = when(s) { 7->"WiFi7";6->"WiFi6/6E";5->"WiFi5";4->"WiFi4";3->"11g";2->"11b";else->"?" }
}