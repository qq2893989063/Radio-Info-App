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

    override fun onDestroyView() {
        handler.removeCallbacks(refresh)
        tvInfo = null
        chart = null
        super.onDestroyView()
    }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val now = System.currentTimeMillis()
            val elapsed = (now - lastTime).coerceAtLeast(1)
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()
            val rxBytes = RadioDataUtils.nonNegativeDelta(currentRx, lastRx) ?: 0L
            val txBytes = RadioDataUtils.nonNegativeDelta(currentTx, lastTx) ?: 0L
            val rxSpeed = rxBytes * 1000.0 / elapsed
            val txSpeed = txBytes * 1000.0 / elapsed
            lastRx = currentRx; lastTx = currentTx; lastTime = now
            val rxKB = (rxSpeed / 1024).toFloat()
            val txKB = (txSpeed / 1024).toFloat()
            rxSpeeds.add(rxKB); txSpeeds.add(txKB)
            if (rxSpeeds.size > 60) { rxSpeeds.removeAt(0); txSpeeds.removeAt(0) }

            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            val sb = StringBuilder()
            sb.appendLine("=== WiFi 流量监控 ===")
            sb.appendLine("WiFi状态: ${if (hasWifi) "已连接" else "未连接"}")
            sb.appendLine("")
            sb.appendLine("[实时速度]")
            sb.appendLine("  下载速度: ${formatSpeed(rxKB)} (接收数据)")
            sb.appendLine("  上传速度: ${formatSpeed(txKB)} (发送数据)")
            sb.appendLine("")
            sb.appendLine("[累计流量]")
            sb.appendLine("  总下载量: ${formatBytes(RadioDataUtils.nonNegativeCounter(currentRx))}")
            sb.appendLine("  总上传量: ${formatBytes(RadioDataUtils.nonNegativeCounter(currentTx))}")
            sb.appendLine("")
            sb.appendLine("[曲线图说明]")
            sb.appendLine("  红色线=下载速度  绿色线=上传速度")
            sb.appendLine("  横轴=时间(最近60秒)  纵轴=速度(KB/s)")
            tvInfo?.text = sb.toString()

            chart?.setLines(listOf(rxSpeeds, txSpeeds))
            val maxSpeed = (rxSpeeds + txSpeeds).maxOrNull()?.coerceAtLeast(100f) ?: 100f
            chart?.setYRange(0f, maxSpeed * 1.3f, "KB/s")
        } catch (e: Exception) { Log.e("RadioInfo", "TrafficLoad", e) }
    }

    private fun formatSpeed(kb: Float) = when {
        kb >= 1024 -> String.format(Locale.US, "%.1f MB/s", kb / 1024)
        kb >= 1 -> String.format(Locale.US, "%.1f KB/s", kb)
        else -> String.format(Locale.US, "%.0f B/s", kb * 1024)
    }
    private fun formatBytes(bytes: Long?) = when {
        bytes == null -> "不可用"
        bytes >= 1073741824 -> String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)
        bytes >= 1048576 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
