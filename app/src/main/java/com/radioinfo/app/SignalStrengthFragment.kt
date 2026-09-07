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
        sb.appendLine("=== 信号强度 ===")
        sb.appendLine("")

        sb.appendLine("[各基站信号]")
        try {
            val cells: List<CellInfo> = tm.getAllCellInfo() ?: emptyList()
            if (cells.isEmpty()) {
                sb.appendLine("  无基站信息 (需要位置权限)")
            } else {
                for ((i, cell) in cells.withIndex()) {
                    val tag = if (cell.isRegistered()) "服务" else "邻区"
                    sb.appendLine("  [$tag] 基站#${i+1}")
                    when (cell) {
                        is CellInfoLte -> {
                            val c = cell.getCellIdentity()
                            val s = cell.getCellSignalStrength()
                            sb.appendLine("    LTE | PCI:${c.getPci()} EARFCN:${c.getEarfcn()}")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                sb.appendLine("    RSRP:${s.getRsrp()} RSRQ:${s.getRsrq()} RSSNR:${s.getRssnr()}")
                            }
                            sb.appendLine("    Level:${s.getLevel()}/4")
                        }
                        is CellInfoNr -> {
                            val c = cell.getCellIdentity() as android.telephony.CellIdentityNr
                            val s = cell.getCellSignalStrength() as android.telephony.CellSignalStrengthNr
                            sb.appendLine("    NR(5G) | PCI:${c.getPci()} NRARFCN:${c.getNrarfcn()}")
                            sb.appendLine("    SS-RSRP:${s.getSsRsrp()} SS-RSRQ:${s.getSsRsrq()} SS-SINR:${s.getSsSinr()}")
                            sb.appendLine("    Level:${s.getLevel()}/4")
                        }
                        is CellInfoGsm -> {
                            val s = cell.getCellSignalStrength()
                            sb.appendLine("    GSM | Level:${s.getLevel()}/4")
                        }
                        is CellInfoWcdma -> {
                            val s = cell.getCellSignalStrength()
                            sb.appendLine("    WCDMA | Level:${s.getLevel()}/4")
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            sb.appendLine("  权限不足")
        }
        binding.tvSignalInfo.text = sb.toString()
    }
}