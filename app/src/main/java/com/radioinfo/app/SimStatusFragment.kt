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

class SimStatusFragment : Fragment() {
    private var tv: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { loadData(); handler.postDelayed(this, 3000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(com.radioinfo.app.R.layout.fragment_sim, container, false)
            tv = v.findViewById(com.radioinfo.app.R.id.tvSimInfo)
            v.findViewById<View>(com.radioinfo.app.R.id.btnRefresh)?.setOnClickListener { loadData() }
            v
        } catch (e: Exception) { Log.e("RadioInfo", "SimView", e); null }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java) ?: return
            val sb = StringBuilder()
            sb.appendLine("=== SIM ===")
            val state = when (tm.simState) {
                5->"READY"; 1->"ABSENT"; 2->"PIN"; 3->"PUK"; else->"State:${tm.simState}"
            }
            sb.appendLine("State: $state")
            sb.appendLine("Carrier: ${tm.simOperatorName ?: "N/A"}")
            sb.appendLine("Code: ${tm.simOperator ?: "N/A"}")
            sb.appendLine("Country: ${tm.simCountryIso ?: "N/A"}")
            try {
                val sm = ctx.getSystemService(android.telephony.SubscriptionManager::class.java)
                val subs = sm?.activeSubscriptionInfoList ?: emptyList()
                sb.appendLine("SIMs: ${subs.size}")
                for (s in subs) sb.appendLine("  ${s.displayName} | ${s.carrierName}")
            } catch (_: Exception) {}
            sb.appendLine("Network: ${tm.networkOperatorName ?: "N/A"}")
            tv?.text = sb.toString()
        } catch (e: Exception) {
            tv?.text = "Error: ${e.message}"
            Log.e("RadioInfo", "SimLoad", e)
        }
    }
}