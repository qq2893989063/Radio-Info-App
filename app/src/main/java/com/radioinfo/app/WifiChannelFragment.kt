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
            sb.appendLine("=== WiFi ===")
            sb.appendLine("State: ${if (wm.wifiState == 3) "ON" else "OFF"}")
            try {
                val ci = wm.connectionInfo
                if (ci != null && ci.networkId != -1) {
                    sb.appendLine("SSID: ${ci.ssid ?: "N/A"}")
                    sb.appendLine("Freq: ${ci.frequency}MHz")
                    sb.appendLine("RSSI: ${ci.rssi}dBm")
                }
            } catch (_: Exception) {}
            try {
                val scans = wm.scanResults ?: emptyList()
                sb.appendLine("APs: ${scans.size}")
                for (s in scans.take(5)) {
                    val ch = when { s.frequency in 2412..2484 -> (s.frequency-2407)/5; s.frequency in 5170..5825 -> (s.frequency-5000)/5; else -> 0 }
                    sb.appendLine("  CH$ch ${s.level}dBm")
                }
            } catch (_: Exception) {}
            tv?.text = sb.toString()
        } catch (e: Exception) {
            tv?.text = "Error: ${e.message}"
            Log.e("RadioInfo", "WifiLoad", e)
        }
    }
}