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

class SignalStrengthFragment : Fragment() {
    private var tv: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { loadData(); handler.postDelayed(this, 2000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(com.radioinfo.app.R.layout.fragment_signal, container, false)
            tv = v.findViewById(com.radioinfo.app.R.id.tvSignalInfo)
            v.findViewById<View>(com.radioinfo.app.R.id.btnRefresh)?.setOnClickListener { loadData() }
            v
        } catch (e: Exception) { Log.e("RadioInfo", "SigView", e); null }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun loadData() {
        try {
            val ctx = context ?: return
            val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java) ?: return
            val sb = StringBuilder()
            sb.appendLine("=== 信号强度详情 ===")
            sb.appendLine("(dBm=分贝毫瓦, 数值越接近0信号越强)")
            sb.appendLine("")
            try {
                val cells = tm.allCellInfo ?: emptyList()
                if (cells.isEmpty()) {
                    sb.appendLine("无基站信息(需授予位置权限)")
                } else {
                    sb.appendLine("共 ${cells.size} 个基站信号:")
                    sb.appendLine("")
                    for ((i, c) in cells.take(5).withIndex()) {
                        val r = if (c.isRegistered) "服务" else "邻区"
                        when (c) {
                            is android.telephony.CellInfoLte -> {
                                val s = c.cellSignalStrength
                                sb.appendLine("[$r] LTE#${i+1}")
                                sb.appendLine("  等级(Level): ${s.level}/4 (${levelDesc(s.level)})")
                                sb.appendLine("  强度(dBm): ${s.dbm} dBm")
                            }
                            is android.telephony.CellInfoNr -> {
                                val s = c.cellSignalStrength
                                sb.appendLine("[$r] 5G#${i+1}")
                                sb.appendLine("  等级(Level): ${s.level}/4 (${levelDesc(s.level)})")
                            }
                            else -> sb.appendLine("[$r] 基站#${i+1}")
                        }
                        sb.appendLine("")
                    }
                    sb.appendLine("[信号等级说明]")
                    sb.appendLine("  4级: 极佳(> -70dBm)  - 可流畅看4K视频")
                    sb.appendLine("  3级: 良好(-70~-85dBm) - 日常使用流畅")
                    sb.appendLine("  2级: 一般(-85~-100dBm) - 基本可用")
                    sb.appendLine("  1级: 较弱(-100~-110dBm) - 可能卡顿")
                    sb.appendLine("  0级: 极弱(< -110dBm) - 可能断连")
                }
            } catch (_: Exception) {}
            tv?.text = sb.toString()
        } catch (e: Exception) {
            tv?.text = "错误: ${e.message}"
            Log.e("RadioInfo", "SigLoad", e)
        }
    }

    private fun levelDesc(l: Int) = when(l) {
        4 -> "极佳"; 3 -> "良好"; 2 -> "一般"; 1 -> "较弱"; 0 -> "极弱"; else -> "未知"
    }
}