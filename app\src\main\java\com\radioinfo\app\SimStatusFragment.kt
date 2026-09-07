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
            sb.appendLine("=== SIM 卡状态信息 ===")
            val state = when (tm.simState) {
                5 -> "就绪 (READY) - SIM卡正常工作"
                1 -> "缺失 (ABSENT) - 未插入SIM卡"
                2 -> "需要PIN码 - 输入SIM卡解锁码"
                3 -> "需要PUK码 - PIN码输入次数过多被锁"
                else -> "状态码:${tm.simState}"
            }
            sb.appendLine("SIM状态: $state")
            sb.appendLine("运营商名称: ${tm.simOperatorName ?: "无"}")
            sb.appendLine("运营商代码: ${tm.simOperator ?: "无"}")
            sb.appendLine("国家代码: ${tm.simCountryIso ?: "无"}")
            try {
                val sm = ctx.getSystemService(android.telephony.SubscriptionManager::class.java)
                val subs = sm?.activeSubscriptionInfoList ?: emptyList()
                sb.appendLine("已插入SIM卡数量: ${subs.size}")
                for (s in subs) sb.appendLine("  SIM卡: ${s.displayName} | 运营商: ${s.carrierName}")
            } catch (_: Exception) {}
            sb.appendLine("当前网络运营商: ${tm.networkOperatorName ?: "无"}")
            tv?.text = sb.toString()
        } catch (e: Exception) {
            tv?.text = "错误: ${e.message}"
            Log.e("RadioInfo", "SimLoad", e)
        }
    }
}