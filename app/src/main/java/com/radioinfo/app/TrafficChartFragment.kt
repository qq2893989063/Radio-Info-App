package com.radioinfo.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.Locale

class TrafficChartFragment : Fragment() {
    private var tvInfo: TextView? = null
    private var chart: ChartView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L
    private val rxSpeeds = mutableListOf<Float>()
    private val txSpeeds = mutableListOf<Float>()

    private val refresh = object : Runnable {
        override fun run() { loadData(); handler.postDelayed(this, 1000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(R.layout.fragment_traffic, container, false)
            tvInfo = v.findViewById(R.id.tvTrafficInfo)
            chart = v.findViewById(R.id.chartTraffic)
            chart?.setYRange(0f, 5000f, "KB/s")
            chart?.setMaxPoints(60)
            v.findViewById<View>(R.id.btnRefresh)?.setOnClickListener { loadData() }
            v
        } catch (e: Exception) { Log.e("RadioInfo", "TrafficView", e); null }
    }

    override fun onResume() { super.onResume(); lastRx = TrafficStats.getTotalRxBytes(); lastTx = TrafficStats.getTotalTxBytes(); lastTime = System.currentTimeMillis(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val now = System.currentTimeMillis()
            val elapsed = (now - lastTime).coerceAtLeast(1)
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()

            val rxBytes = currentRx - lastRx
            val txBytes = currentTx - lastTx
            val rxSpeed = rxBytes * 1000.0 / elapsed
            val txSpeed = txBytes * 1000.0 / elapsed

            lastRx = currentRx; lastTx = currentTx; lastTime = now

            // Convert to KB/s
            val rxKB = (rxSpeed / 1024).toFloat()
            val txKB = (txSpeed / 1024).toFloat()

            rxSpeeds.add(rxKB)
            txSpeeds.add(txKB)
            if (rxSpeeds.size > 60) { rxSpeeds.removeAt(0); txSpeeds.removeAt(0) }

            // WiFi connection info
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            val sb = StringBuilder()
            sb.appendLine("=== WiFi Traffic ===")
            sb.appendLine("WiFi: ${if (hasWifi) "Connected" else "OFF"}")
            sb.appendLine("Download: ${formatSpeed(rxKB)}")
            sb.appendLine("Upload: ${formatSpeed(txKB)}")
            sb.appendLine("Total RX: ${formatBytes(currentRx)}")
            sb.appendLine("Total TX: ${formatBytes(currentTx)}")
            tvInfo?.text = sb.toString()

            chart?.setLines(listOf(rxSpeeds, txSpeeds))

            // Auto-scale Y
            val maxSpeed = (rxSpeeds + txSpeeds).maxOrNull()?.coerceAtLeast(100f) ?: 100f
            chart?.setYRange(0f, maxSpeed * 1.3f, "KB/s")
        } catch (e: Exception) {
            Log.e("RadioInfo", "TrafficLoad", e)
        }
    }

    private fun formatSpeed(kb: Float) = when {
        kb >= 1024 -> String.format(Locale.US, "%.1f MB/s", kb / 1024)
        kb >= 1 -> String.format(Locale.US, "%.1f KB/s", kb)
        else -> String.format(Locale.US, "%.0f B/s", kb * 1024)
    }

    private fun formatBytes(bytes: Long) = when {
        bytes >= 1073741824 -> String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)
        bytes >= 1048576 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}