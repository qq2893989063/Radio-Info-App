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
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoGsm
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager

class RfChartFragment : Fragment() {
    private var tvInfo: TextView? = null
    private var chart: ChartView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val history = mutableMapOf<String, MutableList<Float>>()
    private var cellNames = listOf<String>()

    private val refresh = object : Runnable {
        override fun run() { loadData(); handler.postDelayed(this, 2000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(R.layout.fragment_rfchart, container, false)
            tvInfo = v.findViewById(R.id.tvRfInfo)
            chart = v.findViewById(R.id.chartRf)
            chart?.setYRange(-130f, -30f, "dBm")
            chart?.setMaxPoints(60)
            v.findViewById<View>(R.id.btnRefresh)?.setOnClickListener { loadData() }
            v
        } catch (e: Exception) { Log.e("RadioInfo", "RfView", e); null }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val tm = ctx.getSystemService(TelephonyManager::class.java) ?: return
            val cells = tm.allCellInfo ?: emptyList()
            val sb = StringBuilder()
            sb.appendLine("=== 射频频道信号强度 ===")
            sb.appendLine("(RF=射频, dBm=信号功率, 越大越好)")
            sb.appendLine("")

            val tracked = cells.sortedByDescending { it.isRegistered }.take(5)
            val newNames = mutableListOf<String>()

            for ((i, cell) in tracked.withIndex()) {
                val name = getCellName(cell, i)
                val dbm = getDbm(cell)
                newNames.add(name)
                if (!history.containsKey(name)) history[name] = mutableListOf()
                history[name]!!.add(dbm)
                if (history[name]!!.size > 60) history[name]!!.removeAt(0)

                val band = getBand(cell)
                val reg = if (cell.isRegistered) "服务中" else "邻区"
                val level = getLevel(cell)
                val desc = when(level) { 4->"极佳"; 3->"良好"; 2->"一般"; 1->"较弱"; 0->"极弱"; else->"未知" }
                sb.appendLine("[$reg] $name")
                sb.appendLine("  频段(Band): $band | 强度(dBm): $dbm | 等级: $level/4($desc)")
                sb.appendLine("")
            }

            val oldKeys = history.keys.toList()
            for (k in oldKeys) { if (k !in newNames) history.remove(k) }
            cellNames = newNames
            tvInfo?.text = sb.toString()

            val lines = mutableListOf<List<Float>>()
            for (name in cellNames) lines.add(history[name]?.toList() ?: emptyList())
            chart?.setLines(lines)
        } catch (e: Exception) { Log.e("RadioInfo", "RfLoad", e) }
    }

    private fun getCellName(cell: CellInfo, idx: Int) = when (cell) {
        is CellInfoLte -> "LTE#${idx+1}(4G)"; is CellInfoNr -> "NR#${idx+1}(5G)"
        is CellInfoGsm -> "GSM#${idx+1}(2G)"; is CellInfoWcdma -> "3G#${idx+1}"
        else -> "基站#${idx+1}"
    }
    private fun getBand(cell: CellInfo) = when (cell) {
        is CellInfoLte -> try { "B${cell.cellIdentity.earfcn / 100}" } catch (_: Exception) { "B?" }
        is CellInfoNr -> try { "n${(cell.cellIdentity as android.telephony.CellIdentityNr).nrarfcn / 10000}" } catch (_: Exception) { "n?" }
        is CellInfoGsm -> "GSM(2G)"; is CellInfoWcdma -> "WCDMA(3G)"; else -> "未知"
    }
    private fun getDbm(cell: CellInfo): Float = when (cell) {
        is CellInfoLte -> try { cell.cellSignalStrength.dbm.toFloat() } catch (_: Exception) { -999f }
        is CellInfoNr -> try { (cell.cellSignalStrength as android.telephony.CellSignalStrengthNr).ssRsrp.toFloat() } catch (_: Exception) { -999f }
        is CellInfoGsm -> try { cell.cellSignalStrength.dbm.toFloat() } catch (_: Exception) { -999f }
        is CellInfoWcdma -> try { cell.cellSignalStrength.dbm.toFloat() } catch (_: Exception) { -999f }
        else -> -999f
    }
    private fun getLevel(cell: CellInfo): Int = when (cell) {
        is CellInfoLte -> try { cell.cellSignalStrength.level } catch (_: Exception) { 0 }
        is CellInfoNr -> try { cell.cellSignalStrength.level } catch (_: Exception) { 0 }
        is CellInfoGsm -> try { cell.cellSignalStrength.level } catch (_: Exception) { 0 }
        is CellInfoWcdma -> try { cell.cellSignalStrength.level } catch (_: Exception) { 0 }
        else -> 0
    }
}