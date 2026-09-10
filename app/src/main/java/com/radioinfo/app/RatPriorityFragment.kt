package com.radioinfo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat

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

    override fun onDestroyView() {
        handler.removeCallbacks(refresh)
        tv = null
        super.onDestroyView()
    }

    private fun loadData() {
        try {
            val ctx = context ?: return
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                tv?.text = "需要电话状态权限才能读取 RAT 信息"
                return
            }
            val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java) ?: return
            val sb = StringBuilder()
            sb.appendLine("=== 无线接入技术(RAT) ===")
            sb.appendLine("")
            sb.appendLine("[数据网络类型]")
            sb.appendLine("  当前: ${netType(tm.dataNetworkType)}")
            sb.appendLine("[语音网络类型]")
            sb.appendLine("  当前: ${netType(tm.voiceNetworkType)}")
            sb.appendLine("  (数据网络=上网用, 语音网络=打电话用)")
            sb.appendLine("")
            sb.appendLine("当前运营商: ${tm.networkOperatorName ?: "无"}")
            try {
                val cells = tm.allCellInfo ?: emptyList()
                sb.appendLine("")
                sb.appendLine("[检测到的基站] 共${cells.size}个")
                sb.appendLine("  S=已注册(服务中) N=邻区(未连接)")
                for ((i, c) in cells.take(8).withIndex()) {
                    val r = if (c.isRegistered) "S" else "N"
                    val t = when {
                        c is android.telephony.CellInfoLte -> "LTE(4G)"
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                            c is android.telephony.CellInfoNr -> "NR(5G)"
                        else -> "其他"
                    }
                    sb.appendLine("  [$r] 基站${i+1}: $t")
                }
            } catch (_: Exception) {}
            tv?.text = sb.toString()
        } catch (e: Exception) {
            tv?.text = "暂时无法读取 RAT 信息，请检查权限"
            Log.e("RadioInfo", "RatLoad", e)
        }
    }

    private fun netType(t: Int) = when(t) {
        1->"GPRS(2G慢速)";2->"EDGE(2G增强)";3->"UMTS(3G)"
        13->"LTE(4G高速)";20->"NR(5G超高速)";else->"类型#$t"
    }
}
