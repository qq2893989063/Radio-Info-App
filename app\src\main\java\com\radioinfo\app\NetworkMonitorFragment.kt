package com.radioinfo.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.io.BufferedReader
import java.io.FileReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class NetworkMonitorFragment : Fragment() {
    private var tvInfo: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { loadData(); handler.postDelayed(this, 3000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(R.layout.fragment_network, container, false)
            tvInfo = v.findViewById(R.id.tvNetworkInfo)
            v.findViewById<View>(R.id.btnRefresh)?.setOnClickListener { loadData() }
            v
        } catch (e: Exception) { Log.e("RadioInfo", "NetView", e); null }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val sb = StringBuilder()
            sb.appendLine("=== 局域网设备发现 ===")
            sb.appendLine("")

            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            sb.appendLine("[本机网络信息]")
            sb.appendLine("  WiFi连接: ${if (isWifi) "是" else "否"}")

            try {
                val ip = getLocalIp()
                sb.appendLine("  本机IP: ${ip ?: "获取失败"}")
                val gw = getGateway()
                sb.appendLine("  网关(路由器): ${gw ?: "获取失败"}")
            } catch (_: Exception) {}

            sb.appendLine("")
            sb.appendLine("[局域网设备列表 (ARP表)]")
            val devices = readArpTable()
            if (devices.isNotEmpty()) {
                sb.appendLine("  发现 ${devices.size} 个设备:")
                sb.appendLine("  IP地址         MAC地址            设备")
                sb.appendLine("  ───────────── ────────────────── ────")
                for (d in devices) {
                    val vendor = getVendor(d.mac)
                    sb.appendLine("  ${d.ip.padEnd(14)} ${d.mac.padEnd(18)} $vendor")
                }
            } else {
                sb.appendLine("  无法读取ARP表(部分设备限制)")
                sb.appendLine("  (Android 9+可能阻止访问/proc/net/arp)")
            }

            sb.appendLine("")
            sb.appendLine("[重要说明]")
            sb.appendLine("  ⚠️ Android无root权限限制:")
            sb.appendLine("  • 只能发现与本机通信过的设备")
            sb.appendLine("  • 无法获取其他设备的上下行速度")
            sb.appendLine("  • 无法监控其他设备的流量")
            sb.appendLine("  • 这些功能需要root或路由器管理权限")

            sb.appendLine("")
            sb.appendLine("[本机流量统计]")
            try {
                val totalRx = android.net.TrafficStats.getTotalRxBytes()
                val totalTx = android.net.TrafficStats.getTotalTxBytes()
                sb.appendLine("  总下载: ${formatBytes(totalRx)}")
                sb.appendLine("  总上传: ${formatBytes(totalTx)}")
                sb.appendLine("  (这是本机所有应用的总流量)")
            } catch (_: Exception) {}

            tvInfo?.text = sb.toString()
        } catch (e: Exception) { Log.e("RadioInfo", "NetLoad", e) }
    }

    private fun getLocalIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun getGateway(): String? {
        try {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProps = cm.getLinkProperties(cm.activeNetwork ?: return null)
            return linkProps?.routes?.find { it.isDefaultRoute }?.gateway?.hostAddress
        } catch (_: Exception) {}
        return null
    }

    data class ArpEntry(val ip: String, val mac: String)

    private fun readArpTable(): List<ArpEntry> {
        val devices = mutableListOf<ArpEntry>()
        try {
            val reader = BufferedReader(FileReader("/proc/net/arp"))
            var line = reader.readLine()
            while (reader.readLine().also { line = it } != null) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val ip = parts[0]
                    val mac = parts[3]
                    if (mac != "00:00:00:00:00:00" && mac != "ff:ff:ff:ff:ff:ff") {
                        devices.add(ArpEntry(ip, mac.uppercase()))
                    }
                }
            }
            reader.close()
        } catch (_: Exception) {}
        return devices.distinctBy { it.mac }.sortedBy { it.ip }
    }

    private fun getVendor(mac: String): String {
        val prefix = mac.replace(":", "").take(6).uppercase()
        return when {
            prefix.startsWith("005056") || prefix.startsWith("000C29") || prefix.startsWith("001A2B") -> "VMware"
            prefix.startsWith("080027") -> "VirtualBox"
            prefix.startsWith("B827EB") || prefix.startsWith("DC:A632") -> "Raspberry Pi"
            prefix.startsWith("001A11") || prefix.startsWith("3C5AB4") || prefix.startsWith("F4F5D8") -> "Google"
            prefix.startsWith("68C63A") || prefix.startsWith("7811DC") || prefix.startsWith("2811A5") || prefix.startsWith("584498") -> "Xiaomi"
            prefix.startsWith("50642B") -> "Samsung"
            prefix.startsWith("00155D") || prefix.startsWith("001D28") -> "Microsoft"
            prefix.startsWith("F8A9D0") || prefix.startsWith("48DB50") -> "Huawei"
            prefix.startsWith("00E04C") -> "Realtek"
            prefix.startsWith("525400") -> "QEMU/KVM"
            prefix.startsWith("0242AC") -> "Docker"
            prefix.startsWith("AA0000") || prefix.startsWith("0010F5") -> "Cisco"
            else -> "未知设备"
        }
    }

    private fun formatBytes(bytes: Long) = when {
        bytes >= 1073741824 -> String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)
        bytes >= 1048576 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}