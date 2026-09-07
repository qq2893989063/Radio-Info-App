package com.radioinfo.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.radioinfo.app.databinding.FragmentSimBinding

class SimStatusFragment : Fragment() {
    private var _binding: FragmentSimBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() { loadSimInfo(); handler.postDelayed(this, 3000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSimBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRefresh.setOnClickListener { loadSimInfo() }
    }
    override fun onResume() { super.onResume(); handler.post(refreshRunnable) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refreshRunnable) }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun loadSimInfo() {
        val tm = requireContext().getSystemService(TelephonyManager::class.java)
        val sm = requireContext().getSystemService(SubscriptionManager::class.java)
        val sb = StringBuilder()
        sb.appendLine("=== SIM 卡状态信息 ===")
        sb.appendLine("")

        val state = when (tm.getSimState()) {
            5 -> "SIM卡就绪 (READY)"
            1 -> "SIM卡缺失 (ABSENT)"
            2 -> "需要PIN码"
            3 -> "需要PUK码"
            4 -> "网络锁定"
            6 -> "SIM卡未就绪"
            7 -> "SIM卡永久禁用"
            8 -> "SIM卡I/O错误"
            9 -> "SIM卡受限"
            else -> "未知状态 (${tm.getSimState()})"
        }
        sb.appendLine("[SIM卡状态] $state")
        sb.appendLine("")
        sb.appendLine("[运营商信息]")
        sb.appendLine("  运营商名称: ${tm.getSimOperatorName() ?: "N/A"}")
        sb.appendLine("  运营商代码: ${tm.getSimOperator() ?: "N/A"}")
        sb.appendLine("  国家代码: ${tm.getSimCountryIso() ?: "N/A"}")
        sb.appendLine("  ICCID: ${mask(tm.getSimSerialNumber())}")
        sb.appendLine("  电话号码: ${mask(tm.getLine1Number())}")

        sb.appendLine("")
        sb.appendLine("[订阅信息]")
        try {
            val subs = sm.getActiveSubscriptionInfoList() ?: emptyList()
            sb.appendLine("  活跃SIM卡: ${subs.size}张")
            for ((i, sub) in subs.withIndex()) {
                sb.appendLine("  [SIM${i+1}] ${sub.getDisplayName()} | ${sub.getCarrierName()}")
                sb.appendLine("    ICCID: ${mask(sub.getIccId())} | 类型: ${if (sub.isEmbedded()) "eSIM" else "物理SIM"}")
            }
        } catch (e: SecurityException) {
            sb.appendLine("  权限不足")
        }

        sb.appendLine("")
        sb.appendLine("[当前网络]")
        sb.appendLine("  网络运营商: ${tm.getNetworkOperatorName() ?: "N/A"}")
        sb.appendLine("  数据网络: ${netType(tm.getDataNetworkType())}")
        sb.appendLine("  语音网络: ${netType(tm.getVoiceNetworkType())}")
        binding.tvSimInfo.text = sb.toString()
    }

    private fun mask(v: String?): String {
        if (v.isNullOrEmpty()) return "N/A"
        if (v.length <= 4) return "****"
        return "*".repeat(v.length - 4) + v.takeLast(4)
    }
    private fun netType(t: Int) = when(t) {
        1->"GPRS";2->"EDGE";3->"UMTS";8->"HSDPA";9->"HSUPA"
        10->"HSPA";13->"LTE(4G)";15->"HSPA+";16->"GSM";20->"NR(5G)";else->"未知"
    }
}