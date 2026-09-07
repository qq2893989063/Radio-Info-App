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
            sb.appendLine("=== Band ===")
            try {
                val cells = tm.allCellInfo ?: emptyList()
                sb.appendLine("Cells: ${cells.size}")
                for ((i, c) in cells.take(5).withIndex()) {
                    val r = if (c.isRegistered) "S" else "N"
                    when (c) {
                        is android.telephony.CellInfoLte -> {
                            val id = c.cellIdentity
                            sb.appendLine("  [$r] LTE B? PCI:${id.pci}")
                        }
                        is android.telephony.CellInfoNr -> sb.appendLine("  [$r] NR")
                        is android.telephony.CellInfoGsm -> sb.appendLine("  [$r] GSM")
                        is android.telephony.CellInfoWcdma -> sb.appendLine("  [$r] WCDMA")
                        else -> sb.appendLine("  [$r] ${c.javaClass.simpleName}")
                    }
                }
            } catch (_: Exception) {}
            tv?.text = sb.toString()
        } catch (e: Exception) {
            tv?.text = "Error: ${e.message}"
            Log.e("RadioInfo", "BandLoad", e)
        }
    }
}