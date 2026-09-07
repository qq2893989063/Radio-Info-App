package com.radioinfo.app

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.NetworkRegistrationInfo
import android.telephony.ServiceState
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
        override fun run() {
            loadRatInfo()
            handler.postDelayed(this, 3000)
        }
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

        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("  📡 RAT (无线接入技术) 优先级")
        sb.appendLine("═══════════════════════════════════")

        sb.appendLine("\n■ 当前网络接入技术:")
        sb.appendLine("  数据网络类型: ${getNetworkTypeDetailed(tm.dataNetworkType)}")
        sb.appendLine("  语音网络类型: ${getNetworkTypeDetailed(tm.voiceNetworkType)}")
        sb.appendLine("  数据连接状态: ${getDataStateName(tm.dataState)}")
        sb.appendLine("  数据活动状态: ${getDataActivityName(tm.dataActivityState)}")

        sb.appendLine("\n■ 服务状态:")
        try {
            val ss: ServiceState = tm.serviceState

            sb.appendLine("  语音服务: ${if (ss.voiceRegState == 0) "已注册" else "未注册 (state=${ss.voiceRegState})"}")
            sb.appendLine("  数据服务: ${if (ss.dataRegState == 0) "已注册" else "未注册 (state=${ss.dataRegState})"}")
            sb.appendLine("  漫游状态: ${if (ss.roaming) "🔄 漫游中" else "🏠 本地网络"}")
            sb.appendLine("  运营商名称: ${ss.operatorAlphaLong ?: ss.operatorAlphaShort ?: "N/A"}")

            sb.appendLine("\n  --- 域注册详情 ---")
            val domains = listOf(
                NetworkRegistrationInfo.DOMAIN_PS to "分组交换(PS/数据)",
                NetworkRegistrationInfo.DOMAIN_CS to "电路交换(CS/语音)"
            )
            for ((domain, domainName) in domains) {
                try {
                    val nri = ss.getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.REGISTRATION_TYPE_WWAN, domain
                    )
                    if (nri != null) {
                        sb.appendLine("\n  [$domainName]")
                        sb.appendLine("    注册状态: ${getRegistrationStateName(nri.registrationState)}")
                        sb.appendLine("    接入技术: ${getAccessTechnologyName(nri.accessNetworkTechnology)}")
                        sb.appendLine("    Roaming: ${nri.isRoaming}")
                        sb.appendLine("    是否可用: ${nri.isAvailable}")
                    }
                } catch (e: Exception) {
                    sb.appendLine("  [$domainName] 无法获取")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("  ⚠️ 无法获取服务状态: ${e.message}")
        }

        sb.appendLine("\n■ 检测到的基站 (Cell Info) RAT:")
        try {
            val cellInfoList: List<CellInfo> = tm.allCellInfo ?: emptyList()
            if (cellInfoList.isEmpty()) {
                sb.appendLine("  无可用基站信息 (需要位置权限)")
            } else {
                for ((i, cellInfo) in cellInfoList.withIndex()) {
                    val registered = if (cellInfo.isRegistered) "✅已注册" else "   未注册"
                    val rat = when (cellInfo) {
                        is CellInfoLte -> "LTE (4G)"
                        is CellInfoNr -> "NR (5G)"
                        else -> cellInfo.javaClass.simpleName
                    }
                    sb.appendLine("  [$i] $registered - $rat")
                }
            }
        } catch (e: SecurityException) {
            sb.appendLine("  ⚠️ 权限不足")
        }

        sb.appendLine("\n■ 网络模式偏好:")
        try {
            sb.appendLine("  首选网络类型: ${getPreferredNetworkType(tm.preferredNetworkType)}")
        } catch (e: Exception) {
            sb.appendLine("  无法获取 (需要系统权限)")
        }

        binding.tvRatInfo.text = sb.toString()
    }

    private fun getNetworkTypeDetailed(type: Int): String = when (type) {
        1 -> "GPRS (2G/2.5G)"; 2 -> "EDGE (2G/2.75G)"; 3 -> "UMTS (3G)"
        4 -> "CDMA (2G)"; 5 -> "EVDO-0 (3G)"; 6 -> "EVDO-A (3G)"
        7 -> "1xRTT (2G)"; 8 -> "HSDPA (3G)"; 9 -> "HSUPA (3G)"
        10 -> "HSPA (3G)"; 11 -> "iDEN (2G)"; 12 -> "EVDO-B (3G)"
        13 -> "LTE (4G)"; 14 -> "eHRPD (3G)"; 15 -> "HSPA+ (3.5G)"
        16 -> "GSM (2G)"; 17 -> "TD-SCDMA (3G)"; 18 -> "IWLAN (WiFi)"
        20 -> "NR (5G)"; 0 -> "未知"
        else -> "类型 #$type"
    }

    private fun getDataStateName(state: Int): String = when (state) {
        0 -> "🔴 已断开"; 1 -> "🟡 连接中..."; 2 -> "🟢 已连接"; 3 -> "🟠 已暂停"
        else -> "❓ 未知 ($state)"
    }

    private fun getDataActivityName(state: Int): String = when (state) {
        0 -> "无活动"; 1 -> "📥 接收中"; 2 -> "📤 发送中"; 3 -> "🔄 双向通信"; 4 -> "💤 休眠"
        else -> "❓ 未知 ($state)"
    }

    private fun getRegistrationStateName(state: Int): String = when (state) {
        1 -> "🏠 归属网络"; 5 -> "🔄 漫游"; 2 -> "🔍 未注册(搜索中)"
        3 -> "❌ 未注册(未搜索)"; 4 -> "🚫 注册被拒"; 0 -> "❓ 未知"
        else -> "状态 #$state"
    }

    private fun getAccessTechnologyName(tech: Int): String = when (tech) {
        1 -> "GPRS"; 2 -> "EDGE"; 3 -> "UMTS"; 8 -> "HSDPA"; 9 -> "HSUPA"
        10 -> "HSPA"; 13 -> "LTE"; 20 -> "NR (5G)"; 0 -> "未知"
        else -> "技术 #$tech"
    }

    private fun getPreferredNetworkType(type: Int): String = when (type) {
        0 -> "WCDMA 仅"; 1 -> "GSM/WCDMA 自动"; 2 -> "CDMA 自动"
        3 -> "CDMA/EVDO 自动"; 4 -> "GSM/WCDMA/CDMA 自动"; 6 -> "GSM/WCDMA 仅"
        7 -> "LTE/GSM/WCDMA/CDMA 自动"; 8 -> "LTE/CDMA/EVDO 自动"
        9 -> "LTE/GSM/WCDMA 自动"; 10 -> "LTE/TD-SCDMA 自动"
        14 -> "NR/LTE/TD-SCDMA/CDMA/EVDO/GSM/WCDMA 自动"
        15 -> "NR/LTE/TD-SCDMA/GSM/WCDMA 自动"
        else -> "类型 #$type"
    }
}
