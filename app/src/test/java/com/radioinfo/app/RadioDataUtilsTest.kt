package com.radioinfo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioDataUtilsTest {
    @Test
    fun lteBandUsesTheStandardEarfcnRanges() {
        assertEquals("B1", RadioDataUtils.lteBand(0))
        assertEquals("B3", RadioDataUtils.lteBand(1300))
        assertEquals("B40", RadioDataUtils.lteBand(39649))
        assertEquals("B41", RadioDataUtils.lteBand(39650))
        assertNull(RadioDataUtils.lteBand(5000))
    }

    @Test
    fun nrArfcnIsConvertedUsingTheCorrectRaster() {
        assertEquals(3300.0, RadioDataUtils.nrFrequencyMhz(620000)!!, 0.0001)
        assertEquals(24250.08, RadioDataUtils.nrFrequencyMhz(2016667)!!, 0.0001)
        assertNull(RadioDataUtils.nrFrequencyMhz(-1))
    }

    @Test
    fun unsupportedCountersAndResetsDoNotBecomeNegativeTraffic() {
        assertNull(RadioDataUtils.nonNegativeCounter(-1))
        assertEquals(20L, RadioDataUtils.nonNegativeDelta(100, 80))
        assertNull(RadioDataUtils.nonNegativeDelta(80, 100))
        assertTrue(RadioDataUtils.isValidDbm(-95))
        assertTrue(!RadioDataUtils.isValidDbm(Int.MAX_VALUE))
    }
}
