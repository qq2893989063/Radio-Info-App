package com.radioinfo.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class FrequencyBandFragment : Fragment() {
    private var tv: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { loadData(); handler.postDelayed(this, 3000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(com.radioinfo.app.R.layout.fragment_band, container, false)
            tv = v.findViewById(com.radioinfo.app.R.id.tvBandInfo)
            v.findViewById<View>(com.radioinfo.app.R.id.btnRefresh)?.setOnClickListener { loadData() }
            v
        } catch (e: Exception) { Log.e("RadioInfo", "BandView", e); null }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java) ?: return
            val sb = StringBuilder()
            sb.appendLine("=== 频段通信状态 ===")
            sb.appendLine("(频段=基站使用的无线电频率范围)")
            try {
                val cells = tm.allCellInfo ?: emptyList()
                sb.appendLine("")
                sb.appendLine("检测到 ${cells.size} 个基站频段:")
                val reg = cells.filter { it.isRegistered }
                val nei = cells.filter { !it.isRegistered }
                if (reg.isNotEmpty()) {
                    sb.appendLine("")
                    sb.appendLine("[服务小区] 当前连接的基站")
                    for ((i, c) in reg.take(3).withIndex()) {
                        appendDetail(sb, c, i+1)
                    }
                }
                if (nei.isNotEmpty()) {
                    sb.appendLine("")
                    sb.appendLine("[邻区] 附近的其他基站")
                    for ((i, c) in nei.take(3).withIndex()) {
                        appendDetail(sb, c, i+1)
                    }
                }
                sb.appendLine("")
                sb.appendLine("[频段说明]")
                sb.appendLine("  B1/B3: 电信/联通4G常用频段")
                sb.appendLine("  B41: 移动4G高频段(覆盖广)")
                sb.appendLine("  n78: 5G主流频段")
                sb.appendLine("  PCI: 物理小区标识(区分基站)")
            } catch (_: Exception) {}
            tv?.text = sb.toString()
        } catch (e: Exception) {
            tv?.text = "错误: ${e.message}"
            Log.e("RadioInfo", "BandLoad", e)
        }
    }

    private fun appendDetail(sb: StringBuilder, cell: android.telephony.CellInfo, idx: Int) {
        try {
            when (cell) {
                is android.telephony.CellInfoLte -> {
                    val id = cell.cellIdentity
                    sb.appendLine("  $idx. LTE(4G) PCI:${id.pci}")
                }
                is android.telephony.CellInfoNr -> sb.appendLine("  $idx. NR(5G)")
                is android.telephony.CellInfoGsm -> sb.appendLine("  $idx. GSM(2G)")
                is android.telephony.CellInfoWcdma -> sb.appendLine("  $idx. WCDMA(3G)")
                else -> sb.appendLine("  $idx. 未知类型")
            }
        } catch (_: Exception) { sb.appendLine("  $idx. 读取失败") }
    }
}