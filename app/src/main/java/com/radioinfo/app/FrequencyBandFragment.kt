package com.radioinfo.app

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityWcdma
import android.telephony.CellIdentityCdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellInfoCdma
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

        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("  📻 频段通信状态")
        sb.appendLine("═══════════════════════════════════")

        try {
            val cellInfoList: List<CellInfo> = tm.allCellInfo ?: emptyList()
            if (cellInfoList.isEmpty()) {
                sb.appendLine("\n⚠️ 无可用基站信息")
                sb.appendLine("请确保已授予位置权限并开启移动数据")
            } else {
                // Group by type
                val registered = cellInfoList.filter { it.isRegistered }
                val neighbor = cellInfoList.filter { !it.isRegistered }

                sb.appendLine("\n■ 注册中的服务小区 (${registered.size}个):")
                sb.appendLine("─────────────────────────────────")
                for ((i, cell) in registered.withIndex()) {
                    sb.appendLine("\n  [服务小区 #${i+1}]")
                    appendCellDetails(sb, cell)
                }

                if (neighbor.isNotEmpty()) {
                    sb.appendLine("\n■ 邻区 (${neighbor.size}个):")
                    sb.appendLine("─────────────────────────────────")
                    for ((i, cell) in neighbor.withIndex()) {
                        sb.appendLine("\n  [邻区 #${i+1}]")
                        appendCellDetails(sb, cell)
                    }
                }

                // Band summary
                sb.appendLine("\n■ 频段汇总:")
                sb.appendLine("─────────────────────────────────")
                val bandCount = mutableMapOf<String, Int>()
                for (cell in cellInfoList) {
                    val band = getBandForCell(cell)
                    bandCount[band] = (bandCount[band] ?: 0) + 1
                }
                for ((band, count) in bandCount.toSortedMap()) {
                    val bar = "█".repeat(count)
                    sb.appendLine("  $band: ${count}个小区 $bar")
                }
            }
        } catch (e: SecurityException) {
            sb.appendLine("\n⚠️ 权限不足，无法获取频段信息")
            sb.appendLine("请授予 ACCESS_FINE_LOCATION 权限")
        }

        binding.tvBandInfo.text = sb.toString()
    }

    private fun appendCellDetails(sb: StringBuilder, cell: CellInfo) {
        when (cell) {
            is CellInfoLte -> {
                val id = cell.cellIdentity
                val ss = cell.cellSignalStrength
                sb.appendLine("    类型: LTE (4G)")
                sb.appendLine("    PCI: ${id.pci}")
                sb.appendLine("    TAC: ${id.tac}")
                sb.appendLine("    CI: ${id.ci}")
                sb.appendLine("    EARFCN: ${id.earfcn}")
                sb.appendLine("    频段: Band ${getLteBand(id.earfcn)}")
                sb.appendLine("    带宽: ${id.bandwidth} kHz")
                sb.appendLine("    MCC-MNC: ${id.mccString ?: "?"}-${id.mncString ?: "?"}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    sb.appendLine("    RSRP: ${ss.rsrp} dBm")
                    sb.appendLine("    RSRQ: ${ss.rsrq} dB")
                    sb.appendLine("    RSSNR: ${ss.rssnr} dB")
                    sb.appendLine("    ASU: ${ss.asuLevel}")
                }
                sb.appendLine("    dBm: ${ss.dbm} dBm")
                sb.appendLine("    Level: ${ss.level}/4")
            }
            is CellInfoNr -> {
                val id = cell.cellIdentity as android.telephony.CellIdentityNr
                val ss = cell.cellSignalStrength as android.telephony.CellSignalStrengthNr
                sb.appendLine("    类型: NR (5G)")
                sb.appendLine("    NCI: ${id.nci}")
                sb.appendLine("    PCI: ${id.pci}")
                sb.appendLine("    TAC: ${id.tac}")
                sb.appendLine("    NRARFCN: ${id.nrarfcn}")
                sb.appendLine("    频段: NR Band ${getNrBand(id.nrarfcn)}")
                sb.appendLine("    MCC-MNC: ${id.mccString ?: "?"}-${id.mncString ?: "?"}")
                sb.appendLine("    SS-RSRP: ${ss.ssRsrp} dBm")
                sb.appendLine("    SS-RSRQ: ${ss.ssRsrq} dB")
                sb.appendLine("    SS-SINR: ${ss.ssSinr} dB")
                sb.appendLine("    csiRSRP: ${ss.csiRsrp} dBm")
                sb.appendLine("    csiRSRQ: ${ss.csiRsrq} dB")
                sb.appendLine("    csiSINR: ${ss.csiSinr} dB")
                sb.appendLine("    Level: ${ss.level}/4")
            }
            is CellInfoGsm -> {
                val id = cell.cellIdentity
                val ss = cell.cellSignalStrength
                sb.appendLine("    类型: GSM (2G)")
                sb.appendLine("    CID: ${id.cid}")
                sb.appendLine("    LAC: ${id.lac}")
                sb.appendLine("    ARFCN: ${id.arfcn}")
                sb.appendLine("    频段: GSM Band ${getGsmBand(id.arfcn)}")
                sb.appendLine("    dBm: ${ss.dbm} dBm")
            }
            is CellInfoWcdma -> {
                val id = cell.cellIdentity
                val ss = cell.cellSignalStrength
                sb.appendLine("    类型: WCDMA (3G)")
                sb.appendLine("    CID: ${id.cid}")
                sb.appendLine("    LAC: ${id.lac}")
                sb.appendLine("    PSC: ${id.psc}")
                sb.appendLine("    UARFCN: ${id.uarfcn}")
                sb.appendLine("    频段: WCDMA Band ${getWcdmaBand(id.uarfcn)}")
                sb.appendLine("    dBm: ${ss.dbm} dBm")
            }
            is CellInfoCdma -> {
                val id = cell.cellIdentity
                val ss = cell.cellSignalStrength
                sb.appendLine("    类型: CDMA")
                sb.appendLine("    BaseID: ${id.networkId}")
                sb.appendLine("    SystemID: ${id.systemId}")
                sb.appendLine("    dBm: ${ss.dbm} dBm")
            }
            else -> {
                sb.appendLine("    类型: ${cell.javaClass.simpleName}")
            }
        }
    }

    private fun getBandForCell(cell: CellInfo): String = when (cell) {
        is CellInfoLte -> "LTE Band ${getLteBand(cell.cellIdentity.earfcn)}"
        is CellInfoNr -> {
            val id = cell.cellIdentity as CellIdentityNr
            "NR Band ${getNrBand(id.nrarfcn)}"
        }
        is CellInfoGsm -> "GSM Band ${getGsmBand(cell.cellIdentity.arfcn)}"
        is CellInfoWcdma -> "WCDMA Band ${getWcdmaBand(cell.cellIdentity.uarfcn)}"
        is CellInfoCdma -> "CDMA"
        else -> "未知"
    }

    private fun getLteBand(earfcn: Int): Int = when (earfcn) {
        in 0..599 -> 1; in 600..1199 -> 2; in 1200..1949 -> 3
        in 1950..2399 -> 4; in 2400..2649 -> 5; in 2750..3449 -> 7
        in 3450..3799 -> 8; in 6150..6449 -> 12; in 9210..9659 -> 13
        in 9870..10359 -> 14; in 10560..10809 -> 15; in 10810..10859 -> 16
        in 10860..10909 -> 17; in 10910..11059 -> 18; in 11060..11159 -> 19
        in 11160..11509 -> 20; in 11510..11959 -> 21; in 11960..12029 -> 22
        in 12030..12339 -> 25; in 12340..12539 -> 26; in 12540..12639 -> 27
        in 12640..12739 -> 28; in 12740..13039 -> 29; in 13040..13259 -> 30
        in 36000..36199 -> 33; in 36200..36349 -> 34; in 36350..36949 -> 35
        in 36950..37549 -> 36; in 37550..37749 -> 37; in 37750..38249 -> 38
        in 38250..38649 -> 39; in 38650..39649 -> 40; in 39650..41589 -> 41
        in 65536..66435 -> 66; in 66436..67335 -> 67; in 67336..67535 -> 68
        in 67536..67835 -> 69
        else -> earfcn
    }

    private fun getNrBand(nrarfcn: Int): Int = when (nrarfcn) {
        in 422000..434000 -> 1; in 386000..398000 -> 3
        in 173800..178800 -> 5; in 524000..538000 -> 7
        in 185000..192000 -> 8; in 145800..149200 -> 12
        in 151600..153600 -> 14; in 158200..160600 -> 15
        in 164200..166000 -> 20; in 386000..399000 -> 25
        in 460000..480000 -> 28; in 496700..499000 -> 40
        in 499200..537999 -> 41; in 620000..680000 -> 77
        in 693334..733333 -> 78; in 743334..795000 -> 79
        in 2054166..2104165 -> 257; in 2016667..2070832 -> 258
        in 2229166..2279166 -> 260; in 2070833..2084999 -> 261
        else -> nrarfcn
    }

    private fun getGsmBand(arfcn: Int): Int = when (arfcn) {
        in 1..124 -> 900; in 128..251 -> 1800
        in 512..885 -> 1900; in 975..1023 -> 900
        else -> arfcn
    }

    private fun getWcdmaBand(uarfcn: Int): Int = when (uarfcn) {
        in 10562..10838 -> 1; in 9662..9938 -> 2
        in 1112..1513 -> 3; in 1537..1738 -> 4
        in 4357..4458 -> 5; in 4387..4412 -> 6
        in 2237..2563 -> 8; in 2937..3088 -> 19
        else -> uarfcn
    }
}
