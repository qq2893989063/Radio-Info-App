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

class RatPriorityFragment : Fragment() {
    private var tv: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { loadData(); handler.postDelayed(this, 3000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(com.radioinfo.app.R.layout.fragment_rat, container, false)
            tv = v.findViewById(com.radioinfo.app.R.id.tvRatInfo)
            v.findViewById<View>(com.radioinfo.app.R.id.btnRefresh)?.setOnClickListener { loadData() }
            v
        } catch (e: Exception) { Log.e("RadioInfo", "RatView", e); null }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java) ?: return
            val sb = StringBuilder()
            sb.appendLine("=== RAT ===")
            sb.appendLine("Data: ${netType(tm.dataNetworkType)}")
            sb.appendLine("Voice: ${netType(tm.voiceNetworkType)}")
            sb.appendLine("Carrier: ${tm.networkOperatorName ?: "N/A"}")
            try {
                val cells = tm.allCellInfo ?: emptyList()
                sb.appendLine("Cells: ${cells.size}")
                for ((i, c) in cells.take(5).withIndex()) {
                    val r = if (c.isRegistered) "S" else "N"
                    val t = when (c) {
                        is android.telephony.CellInfoLte -> "LTE"
                        is android.telephony.CellInfoNr -> "NR"
                        else -> "Other"
                    }
                    sb.appendLine("  [$r] $t")
                }
            } catch (_: Exception) {}
            tv?.text = sb.toString()
        } catch (e: Exception) {
            tv?.text = "Error: ${e.message}"
            Log.e("RadioInfo", "RatLoad", e)
        }
    }

    private fun netType(t: Int) = when(t) {
        1->"GPRS";2->"EDGE";3->"UMTS";13->"LTE";20->"NR";else->"#$t"
    }
}