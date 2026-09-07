package com.radioinfo.app

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class WifiChannelFragment : Fragment() {
    private var tv: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { loadData(); handler.postDelayed(this, 5000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(com.radioinfo.app.R.layout.fragment_wifi, container, false)
            tv = v.findViewById(com.radioinfo.app.R.id.tvWifiInfo)
            v.findViewById<View>(com.radioinfo.app.R.id.btnRefresh)?.setOnClickListener { loadData() }
            v
        } catch (e: Exception) { Log.e("RadioInfo", "WifiView", e); null }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return
            val sb = StringBuilder()
            sb.appendLine("=== WiFi 信道状态 ===")
            sb.appendLine("WiFi开关: ${if (wm.wifiState == 3) "已开启" else "已关闭"}")
            try {
                val ci = wm.connectionInfo
                if (ci != null && ci.networkId != -1) {
                    sb.appendLine("")
                    sb.appendLine("[当前连接的AP]")
                    sb.appendLine("  名称(SSID): ${ci.ssid ?: "隐藏网络"}")
                    sb.appendLine("  MAC地址: ${ci.bssid ?: "无"}")
                    sb.appendLine("  频率: ${ci.frequency} MHz (信道${chFreq(ci.frequency)})")
                    sb.appendLine("  信号强度: ${ci.rssi} dBm (${rssiDesc(ci.rssi)})")
                }
            } catch (_: Exception) {}
            try {
                val scans = wm.scanResults ?: emptyList()
                sb.appendLine("")
                sb.appendLine("[附近WiFi] 共${scans.size}个")
                sb.appendLine("  CH  信号    名称")
                sb.appendLine("  --- ------  ----")
                for (s in scans.take(8)) {
                    val ch = chFreq(s.frequency)
                    val name = if (s.SSID.isNullOrEmpty()) "<隐藏>" else s.SSID.take(16)
                    sb.appendLine("  CH%-2d %+5d  $name".format(ch, s.level))
                }
                sb.appendLine("")
                sb.appendLine("  信号等级: -50以上=极强 -60=强 -70=良 -80=弱 -90以下=很弱")
            } catch (_: Exception) {}
            tv?.text = sb.toString()
        } catch (e: Exception) {
            tv?.text = "错误: ${e.message}"
            Log.e("RadioInfo", "WifiLoad", e)
        }
    }

    private fun chFreq(f: Int) = when { f in 2412..2484 -> (f-2407)/5; f in 5170..5825 -> (f-5000)/5; f in 5955..7115 -> (f-5950)/5; else -> 0 }
    private fun rssiDesc(r: Int) = when { r >= -50 -> "极强"; r >= -60 -> "强"; r >= -70 -> "良好"; r >= -80 -> "弱"; else -> "很弱" }
}