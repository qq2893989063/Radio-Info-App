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
import com.radioinfo.app.databinding.FragmentBandBinding

class FrequencyBandFragment : Fragment() {
    private var _binding: FragmentBandBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() { loadBandInfo(); handler.postDelayed(this, 3000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBandBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRefresh.setOnClickListener { loadBandInfo() }
    }
    override fun onResume() { super.onResume(); handler.post(refreshRunnable) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refreshRunnable) }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun loadBandInfo() {
        val tm = requireContext().getSystemService(TelephonyManager::class.java)
        val sb = StringBuilder()
        sb.appendLine("=== Band Info ===")
        sb.appendLine("")

        try {
            val cells: List<CellInfo> = tm.getAllCellInfo() ?: emptyList()
            if (cells.isEmpty()) {
                sb.appendLine("No cell info (needs location)")
            } else {
                val reg = cells.filter { it.isRegistered() }
                val nei = cells.filter { !it.isRegistered() }

                sb.appendLine("[Serving] ${reg.size}")
                for ((i, c) in reg.withIndex()) {
                    sb.appendLine("  #${i+1}")
                    appendDetail(sb, c)
                }
                sb.appendLine("")
                sb.appendLine("[Neighbor] ${nei.size}")
                for ((i, c) in nei.take(5).withIndex()) {
                    sb.appendLine("  #${i+1}")
                    appendDetail(sb, c)
                }

                sb.appendLine("")
                sb.appendLine("[Band Summary]")
                val bandMap = mutableMapOf<String, Int>()
                for (c in cells) {
                    val b = getBand(c)
                    bandMap[b] = (bandMap[b] ?: 0) + 1
                }
                for ((b, cnt) in bandMap.toSortedMap()) {
                    sb.appendLine("  $b: $cnt")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Error: ${e.message}")
        }
        binding.tvBandInfo.text = sb.toString()
    }

    private fun appendDetail(sb: StringBuilder, cell: CellInfo) {
        try {
            when (cell) {
                is CellInfoLte -> {
                    val c = cell.getCellIdentity()
                    val s = cell.getCellSignalStrength()
                    sb.appendLine("    LTE PCI:${c.getPci()} EARFCN:${c.getEarfcn()}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        sb.appendLine("    RSRP:${s.getRsrp()} RSRQ:${s.getRsrq()}")
                    }
                    sb.appendLine("    Level:${s.getLevel()}/4")
                }
                is CellInfoNr -> {
                    val c = cell.getCellIdentity() as android.telephony.CellIdentityNr
                    val s = cell.getCellSignalStrength() as android.telephony.CellSignalStrengthNr
                    sb.appendLine("    NR PCI:${c.getPci()} NRARFCN:${c.getNrarfcn()}")
                    sb.appendLine("    SS-RSRP:${s.getSsRsrp()} Level:${s.getLevel()}/4")
                }
                is CellInfoGsm -> {
                    val s = cell.getCellSignalStrength()
                    sb.appendLine("    GSM Level:${s.getLevel()}/4")
                }
                is CellInfoWcdma -> {
                    val s = cell.getCellSignalStrength()
                    sb.appendLine("    WCDMA Level:${s.getLevel()}/4")
                }
                else -> sb.appendLine("    ${cell.javaClass.simpleName}")
            }
        } catch (e: Exception) {
            sb.appendLine("    Error reading cell")
        }
    }

    private fun getBand(cell: CellInfo): String = when (cell) {
        is CellInfoLte -> "LTE B${lteBand(cell.getCellIdentity().getEarfcn())}"
        is CellInfoNr -> "NR n${nrBand((cell.getCellIdentity() as android.telephony.CellIdentityNr).getNrarfcn())}"
        is CellInfoGsm -> "GSM"
        is CellInfoWcdma -> "WCDMA"
        else -> "Other"
    }

    private fun lteBand(earfcn: Int) = when (earfcn) {
        in 0..599 -> 1; in 600..1199 -> 2; in 1200..1949 -> 3; in 1950..2399 -> 4
        in 2400..2649 -> 5; in 2750..3449 -> 7; in 3450..3799 -> 8
        in 6150..6449 -> 12; in 9210..9659 -> 13; in 11160..11509 -> 20
        in 36000..36949 -> 33; in 38650..39649 -> 40; in 39650..41589 -> 41
        else -> earfcn
    }

    private fun nrBand(nrarfcn: Int) = when (nrarfcn) {
        in 422000..434000 -> 1; in 386000..398000 -> 3; in 524000..538000 -> 7
        in 185000..192000 -> 8; in 620000..680000 -> 77; in 693334..733333 -> 78
        in 743334..795000 -> 79
        else -> nrarfcn
    }
}