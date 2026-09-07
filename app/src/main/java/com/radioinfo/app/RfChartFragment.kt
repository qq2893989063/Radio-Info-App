package com.radioinfo.app

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

class RfChartFragment : Fragment() {
    private var tvInfo: TextView? = null
    private var chart: ChartView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val history = mutableMapOf<String, MutableList<Float>>()
    private var cellNames = listOf<String>()
    private var lastCells = listOf<CellInfo>()

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
            sb.appendLine("=== RF Channel Signal ===")
            sb.appendLine("Cells: ${cells.size}")
            sb.appendLine("")

            // Track up to 5 strongest cells
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
                val reg = if (cell.isRegistered) "S" else "N"
                sb.appendLine("[$reg] $name")
                sb.appendLine("    Band: $band | dBm: $dbm | Level: ${getLevel(cell)}/4")
                sb.appendLine("")
            }

            // Update history keys
            val oldKeys = history.keys.toList()
            for (k in oldKeys) {
                if (k !in newNames) history.remove(k)
            }
            cellNames = newNames

            tvInfo?.text = sb.toString()

            // Build chart lines
            val lines = mutableListOf<List<Float>>()
            for (name in cellNames) {
                lines.add(history[name]?.toList() ?: emptyList())
            }
            chart?.setLines(lines)

        } catch (e: Exception) {
            Log.e("RadioInfo", "RfLoad", e)
        }
    }

    private fun getCellName(cell: CellInfo, idx: Int): String {
        return when (cell) {
            is CellInfoLte -> "LTE#${idx+1}"
            is CellInfoNr -> "NR#${idx+1}"
            is CellInfoGsm -> "GSM#${idx+1}"
            is CellInfoWcdma -> "3G#${idx+1}"
            else -> "Cell#${idx+1}"
        }
    }

    private fun getBand(cell: CellInfo): String = when (cell) {
        is CellInfoLte -> try { "B${cell.cellIdentity.earfcn / 100}" } catch (_: Exception) { "B?" }
        is CellInfoNr -> try { "n${(cell.cellIdentity as android.telephony.CellIdentityNr).nrarfcn / 10000}" } catch (_: Exception) { "n?" }
        is CellInfoGsm -> "GSM"
        is CellInfoWcdma -> "WCDMA"
        else -> "?"
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