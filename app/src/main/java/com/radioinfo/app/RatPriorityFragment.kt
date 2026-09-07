package com.radioinfo.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.radioinfo.app.databinding.FragmentRatBinding

class RatPriorityFragment : Fragment() {
    private var _binding: FragmentRatBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() { loadRatInfo(); handler.postDelayed(this, 3000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRatBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRefresh.setOnClickListener { loadRatInfo() }
    }
    override fun onResume() { super.onResume(); handler.post(refreshRunnable) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refreshRunnable) }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun loadRatInfo() {
        val tm = requireContext().getSystemService(TelephonyManager::class.java)
        val sb = StringBuilder()
        sb.appendLine("=== RAT 优先级 ===")
        sb.appendLine("")
        sb.appendLine("[当前网络]")
        sb.appendLine("  数据网络: ${netType(tm.getDataNetworkType())}")
        sb.appendLine("  语音网络: ${netType(tm.getVoiceNetworkType())}")
        sb.appendLine("  数据状态: ${dataState(tm.getDataState())}")
        sb.appendLine("  运营商: ${tm.getNetworkOperatorName() ?: "N/A"}")

        sb.appendLine("")
        sb.appendLine("[检测到的基站]")
        try {
            val cells: List<CellInfo> = tm.getAllCellInfo() ?: emptyList()
            if (cells.isEmpty()) {
                sb.appendLine("  无基站信息 (需要位置权限)")
            } else {
                for ((i, c) in cells.withIndex()) {
                    val reg = if (c.isRegistered()) "已注册" else "未注册"
                    val rat = when (c) {
                        is CellInfoLte -> "LTE(4G)"
                        is CellInfoNr -> "NR(5G)"
                        else -> c.javaClass.simpleName
                    }
                    sb.appendLine("  [$i] $reg - $rat")
                }
            }
        } catch (e: SecurityException) {
            sb.appendLine("  权限不足")
        }
        binding.tvRatInfo.text = sb.toString()
    }

    private fun netType(t: Int) = when(t) {
        1->"GPRS";2->"EDGE";3->"UMTS";8->"HSDPA";9->"HSUPA"
        10->"HSPA";13->"LTE(4G)";15->"HSPA+";16->"GSM";20->"NR(5G)";0->"未知"
        else->"#$t"
    }
    private fun dataState(s: Int) = when(s) { 0->"断开";1->"连接中";2->"已连接";3->"暂停";else->"未知" }
}