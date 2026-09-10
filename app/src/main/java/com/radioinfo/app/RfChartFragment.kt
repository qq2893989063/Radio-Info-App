package com.radioinfo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat

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

    override fun onDestroyView() {
        handler.removeCallbacks(refresh)
        tvInfo = null
        chart = null
        super.onDestroyView()
    }

    private fun loadData() {
        try {
            val ctx = context ?: return
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                tvInfo?.text = "需要位置权限才能读取射频信息"
                return
            }
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
                if (dbm != null) {
                    history[name]!!.add(dbm)
                    if (history[name]!!.size > 60) history[name]!!.removeAt(0)
                }

                val band = getBand(cell)
                val reg = if (cell.isRegistered) "服务中" else "邻区"
                val level = getLevel(cell)
                val desc = when(level) { 4->"极佳"; 3->"良好"; 2->"一般"; 1->"较弱"; 0->"极弱"; null->"不可用"; else->"未知" }
                sb.appendLine("[$reg] $name")
                sb.appendLine("  频段(Band): $band | 强度(dBm): ${dbm ?: "不可用"} | 等级: ${level?.let { "$it/4" } ?: "不可用"}($desc)")
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
        is CellInfoLte -> "LTE#${idx+1}(4G)"
        is CellInfoGsm -> "GSM#${idx+1}(2G)"
        is CellInfoWcdma -> "3G#${idx+1}"
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr) {
            "NR#${idx+1}(5G)"
        } else {
            "基站#${idx+1}"
        }
    }
    private fun getBand(cell: CellInfo) = when (cell) {
        is CellInfoLte -> RadioDataUtils.lteBand(cell.cellIdentity.earfcn) ?: "未知"
        is CellInfoGsm -> "GSM(2G)"
        is CellInfoWcdma -> "WCDMA(3G)"
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr) {
            val arfcn = (cell.cellIdentity as android.telephony.CellIdentityNr).nrarfcn
            "NR-ARFCN $arfcn (${RadioDataUtils.formatFrequencyMhz(RadioDataUtils.nrFrequencyMhz(arfcn))})"
        } else "未知"
    }
    private fun getDbm(cell: CellInfo): Float? = when {
        cell is CellInfoLte -> try { cell.cellSignalStrength.dbm.toFloat() } catch (_: Exception) { null }
        cell is CellInfoGsm -> try { cell.cellSignalStrength.dbm.toFloat() } catch (_: Exception) { null }
        cell is CellInfoWcdma -> try { cell.cellSignalStrength.dbm.toFloat() } catch (_: Exception) { null }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr ->
            try {
                (cell.cellSignalStrength as android.telephony.CellSignalStrengthNr).ssRsrp.toFloat()
            } catch (_: Exception) { null }
        else -> null
    }?.takeIf { RadioDataUtils.isValidDbm(it.toInt()) }

    private fun getLevel(cell: CellInfo): Int? = when {
        cell is CellInfoLte -> try { cell.cellSignalStrength.level } catch (_: Exception) { null }
        cell is CellInfoGsm -> try { cell.cellSignalStrength.level } catch (_: Exception) { null }
        cell is CellInfoWcdma -> try { cell.cellSignalStrength.level } catch (_: Exception) { null }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr ->
            try { cell.cellSignalStrength.level } catch (_: Exception) { null }
        else -> null
    }
}
