package com.radioinfo.app

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoGsm
import android.telephony.CellInfoWcdma
import android.telephony.CellInfoCdma
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.radioinfo.app.databinding.FragmentSignalBinding

class SignalStrengthFragment : Fragment() {

    private var _binding: FragmentSignalBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() { loadSignalInfo(); handler.postDelayed(this, 2000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRefresh.setOnClickListener { loadSignalInfo() }
    }

    override fun onResume() { super.onResume(); handler.post(refreshRunnable) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refreshRunnable) }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun loadSignalInfo() {
        val tm = requireContext().getSystemService(TelephonyManager::class.java)
        val sb = StringBuilder()

        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("  📊 信号强度详情")
        sb.appendLine("═══════════════════════════════════")

        // Signal Strength from TelephonyManager
        sb.appendLine("\n■ 系统信号强度:")
        try {
            @Suppress("DEPRECATION")
            val signalStrength: SignalStrength = tm.signalStrength
            if (signalStrength != null) {
                sb.appendLine("  Level: ${signalStrength.level}/4")
                sb.appendLine("  ASU Level: ${signalStrength.asLevel}")
                val dbm = signalStrength.dbm
                sb.appendLine("  dBm: $dbm dBm")

                // Get all cell signal info
                val cellSignals = signalStrength.cellSignalStrengths
                sb.appendLine("  信号源数量: ${cellSignals.size}")
                for ((i, cs) in cellSignals.withIndex()) {
                    sb.appendLine("\n  [信号源 #${i+1}] ${cs.javaClass.simpleName}")
                    sb.appendLine("    dBm: ${cs.dbm} dBm")
                    sb.appendLine("    ASU: ${cs.asuLevel}")
                    sb.appendLine("    Level: ${cs.level}/4")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("  ⚠️ 无法获取: ${e.message}")
        }

        // Detailed cell info with signal
        sb.appendLine("\n■ 各基站信号强度:")
        sb.appendLine("═══════════════════════════════════")

        try {
            val cellInfoList: List<CellInfo> = tm.allCellInfo ?: emptyList()
            if (cellInfoList.isEmpty()) {
                sb.appendLine("\n  ⚠️ 无基站信息 (需要位置权限)")
            } else {
                // Sort by registered first, then by signal strength
                val sorted = cellInfoList.sortedWith(
                    compareByDescending<CellInfo> { it.isRegistered }
                        .thenByDescending { getCellDbm(it) }
                )

                for ((i, cell) in sorted.withIndex()) {
                    val regTag = if (cell.isRegistered) "✅服务" else "   邻区"
                    sb.appendLine("\n  [$regTag] 基站 #${i+1}")

                    when (cell) {
                        is CellInfoLte -> {
                            val ss = cell.cellSignalStrength
                            val id = cell.cellIdentity
                            sb.appendLine("    类型: LTE (4G)")
                            sb.appendLine("    PCI: ${id.pci} | EARFCN: ${id.earfcn}")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                sb.appendLine("    ┌─────────────────────────────┐")
                                sb.appendLine("    │  RSRP:  ${formatSignal(ss.rsrp)} dBm  │")
                                sb.appendLine("    │  RSRQ:  ${formatSignal(ss.rsrq)} dB   │")
                                sb.appendLine("    │  RSSNR: ${formatSignal(ss.rssnr)} dB   │")
                                sb.appendLine("    │  ASU:   ${ss.asuLevel}              │")
                                sb.appendLine("    │  dBm:   ${ss.dbm} dBm        │")
                                sb.appendLine("    │  Level: ${ss.level}/4                │")
                                sb.appendLine("    └─────────────────────────────┘")
                                sb.appendLine("    信号质量: ${getSignalQuality(ss.rsrp)}")
                            }
                        }
                        is CellInfoNr -> {
                            val ss = cell.cellSignalStrength as android.telephony.CellSignalStrengthNr
                            val id = cell.cellIdentity as android.telephony.CellIdentityNr
                            sb.appendLine("    类型: NR (5G)")
                            sb.appendLine("    PCI: ${id.pci} | NRARFCN: ${id.nrarfcn}")
                            sb.appendLine("    ┌─────────────────────────────┐")
                            sb.appendLine("    │  SS-RSRP: ${formatSignal(ss.ssRsrp)} dBm  │")
                            sb.appendLine("    │  SS-RSRQ: ${formatSignal(ss.ssRsrq)} dB   │")
                            sb.appendLine("    │  SS-SINR: ${formatSignal(ss.ssSinr)} dB   │")
                            sb.appendLine("    │  CSI-RSRP:${formatSignal(ss.csiRsrp)} dBm  │")
                            sb.appendLine("    │  CSI-RSRQ:${formatSignal(ss.csiRsrq)} dB   │")
                            sb.appendLine("    │  CSI-SINR:${formatSignal(ss.csiSinr)} dB   │")
                            sb.appendLine("    │  Level:   ${ss.level}/4              │")
                            sb.appendLine("    └─────────────────────────────┘")
                            sb.appendLine("    信号质量: ${getSignalQuality(ss.ssRsrp)}")
                        }
                        is CellInfoGsm -> {
                            val ss = cell.cellSignalStrength
                            val id = cell.cellIdentity
                            sb.appendLine("    类型: GSM (2G)")
                            sb.appendLine("    CID: ${id.cid} | ARFCN: ${id.arfcn}")
                            sb.appendLine("    dBm: ${ss.dbm} dBm | ASU: ${ss.asuLevel}")
                            sb.appendLine("    Level: ${ss.level}/4")
                            sb.appendLine("    信号质量: ${getSignalQuality(ss.dbm)}")
                        }
                        is CellInfoWcdma -> {
                            val ss = cell.cellSignalStrength
                            val id = cell.cellIdentity
                            sb.appendLine("    类型: WCDMA (3G)")
                            sb.appendLine("    CID: ${id.cid} | PSC: ${id.psc}")
                            sb.appendLine("    dBm: ${ss.dbm} dBm | ASU: ${ss.asuLevel}")
                            sb.appendLine("    Level: ${ss.level}/4")
                            sb.appendLine("    信号质量: ${getSignalQuality(ss.dbm)}")
                        }
                        is CellInfoCdma -> {
                            val ss = cell.cellSignalStrength
                            sb.appendLine("    类型: CDMA")
                            sb.appendLine("    dBm: ${ss.dbm} dBm | ASU: ${ss.asuLevel}")
                            sb.appendLine("    Level: ${ss.level}/4")
                        }
                        else -> {
                            sb.appendLine("    类型: ${cell.javaClass.simpleName}")
                        }
                    }
                }

                // Signal bar visualization
                sb.appendLine("\n■ 信号强度可视化:")
                sb.appendLine("─────────────────────────────────")
                for ((i, cell) in sorted.take(6).withIndex()) {
                    val dbm = getCellDbm(cell)
                    val label = if (cell.isRegistered) "服务" else "邻区${i}"
                    val bar = getSignalBar(dbm)
                    val dbmStr = if (dbm != Int.MIN_VALUE) "${dbm}dBm" else "N/A"
                    sb.appendLine("  $label: $bar $dbmStr")
                }
            }
        } catch (e: SecurityException) {
            sb.appendLine("\n  ⚠️ 权限不足")
        }

        binding.tvSignalInfo.text = sb.toString()
    }

    private fun getCellDbm(cell: CellInfo): Int = when (cell) {
        is CellInfoLte -> cell.cellSignalStrength.dbm
        is CellInfoNr -> (cell.cellSignalStrength as android.telephony.CellSignalStrengthNr).ssRsrp
        is CellInfoGsm -> cell.cellSignalStrength.dbm
        is CellInfoWcdma -> cell.cellSignalStrength.dbm
        is CellInfoCdma -> cell.cellSignalStrength.dbm
        else -> Int.MIN_VALUE
    }

    private fun formatSignal(value: Int): String {
        return if (value == Int.MIN_VALUE || value == 2147483647) "N/A " else String.format("%+4d", value)
    }

    private fun getSignalQuality(dbm: Int): String = when {
        dbm >= -65 -> "⭐⭐⭐⭐⭐ 极佳 (> -65 dBm)"
        dbm >= -75 -> "⭐⭐⭐⭐  优秀 (-65 ~ -75 dBm)"
        dbm >= -85 -> "⭐⭐⭐   良好 (-75 ~ -85 dBm)"
        dbm >= -100 -> "⭐⭐    一般 (-85 ~ -100 dBm)"
        dbm >= -110 -> "⭐     较弱 (-100 ~ -110 dBm)"
        dbm >= -120 -> "⚠️    很弱 (-110 ~ -120 dBm)"
        dbm != Int.MIN_VALUE -> "❌    极弱 (< -120 dBm)"
        else -> "❓    未知"
    }

    private fun getSignalBar(dbm: Int): String = when {
        dbm >= -65 -> "▂▃▅▇█"
        dbm >= -75 -> "▂▃▅▇░"
        dbm >= -85 -> "▂▃▅░░"
        dbm >= -100 -> "▂▃░░░"
        dbm >= -115 -> "▂░░░░"
        dbm >= -130 -> "░░░░░"
        else -> "─────"
    }
}
