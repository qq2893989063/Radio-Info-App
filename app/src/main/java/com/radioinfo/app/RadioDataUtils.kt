package com.radioinfo.app

import java.util.Locale

internal object RadioDataUtils {
    private data class LteBand(val first: Int, val last: Int, val name: String)

    // E-UTRA channel ranges from 3GPP TS 36.101. An EARFCN cannot be
    // converted to a band by dividing it by a constant.
    private val lteBands = listOf(
        LteBand(0, 599, "B1"), LteBand(600, 1199, "B2"),
        LteBand(1200, 1949, "B3"), LteBand(1950, 2399, "B4"),
        LteBand(2400, 2649, "B5"), LteBand(2650, 2749, "B6"),
        LteBand(2750, 3449, "B7"), LteBand(3450, 3799, "B8"),
        LteBand(3800, 4149, "B9"), LteBand(4150, 4749, "B10"),
        LteBand(4750, 4949, "B11"), LteBand(5010, 5179, "B12"),
        LteBand(5180, 5279, "B13"), LteBand(5280, 5379, "B14"),
        LteBand(5730, 5849, "B17"), LteBand(5850, 5999, "B18"),
        LteBand(6000, 6149, "B19"), LteBand(6150, 6449, "B20"),
        LteBand(6450, 6599, "B21"), LteBand(6600, 7399, "B22"),
        LteBand(7500, 7699, "B23"), LteBand(7700, 8039, "B24"),
        LteBand(8040, 8689, "B25"), LteBand(8690, 9039, "B26"),
        LteBand(9040, 9209, "B27"), LteBand(9210, 9659, "B28"),
        LteBand(9660, 9769, "B29"), LteBand(9770, 9869, "B30"),
        LteBand(9870, 9919, "B31"), LteBand(9920, 10359, "B32"),
        LteBand(36000, 36199, "B33"), LteBand(36200, 36349, "B34"),
        LteBand(36350, 36949, "B35"), LteBand(36950, 37549, "B36"),
        LteBand(37550, 37749, "B37"), LteBand(37750, 38249, "B38"),
        LteBand(38250, 38649, "B39"), LteBand(38650, 39649, "B40"),
        LteBand(39650, 41589, "B41"), LteBand(41590, 43589, "B42"),
        LteBand(43590, 45589, "B43"), LteBand(45590, 46589, "B44"),
        LteBand(46590, 46789, "B45"), LteBand(46790, 54539, "B46"),
        LteBand(54540, 55239, "B47"), LteBand(55240, 56739, "B48"),
        LteBand(56740, 58239, "B49"), LteBand(58240, 59089, "B50"),
        LteBand(59090, 59139, "B51"), LteBand(59140, 60139, "B52"),
        LteBand(60140, 60254, "B53")
    )

    fun lteBand(earfcn: Int): String? =
        lteBands.firstOrNull { earfcn in it.first..it.last }?.name

    // NR-ARFCN uses three raster formulas. The resulting frequency is
    // shown instead of guessing a band because several NR bands overlap.
    fun nrFrequencyMhz(nrarfcn: Int): Double? = when (nrarfcn) {
        in 0..599_999 -> nrarfcn * 0.005
        in 600_000..2_016_666 -> 3000.0 + (nrarfcn - 600_000) * 0.015
        in 2_016_667..3_279_165 -> 24_250.08 + (nrarfcn - 2_016_667) * 0.06
        else -> null
    }

    fun formatFrequencyMhz(mhz: Double?): String = mhz?.let {
        String.format(Locale.US, "%.2f MHz", it)
    } ?: "不可用"

    fun isValidDbm(dbm: Int): Boolean = dbm in -200..0

    fun nonNegativeCounter(value: Long): Long? = value.takeIf { it >= 0L }

    fun nonNegativeDelta(current: Long, previous: Long): Long? =
        if (current >= 0L && previous >= 0L && current >= previous) current - previous else null
}
