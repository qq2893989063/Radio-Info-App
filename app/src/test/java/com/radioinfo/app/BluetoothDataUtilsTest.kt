package com.radioinfo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothDataUtilsTest {
    @Test
    fun rejectsInvalidRssi() {
        assertNull(BluetoothDataUtils.estimateDistanceMeters(0))
        assertNull(BluetoothDataUtils.estimateDistanceMeters(-128))
        assertTrue(BluetoothDataUtils.estimateDistanceMeters(-59)!! in 0.9..1.1)
    }

    @Test
    fun masksBluetoothAddressBeforeDisplay() {
        assertEquals("AA:BB:CC:**:**:**", BluetoothDataUtils.maskAddress("AA:BB:CC:DD:EE:FF"))
        assertEquals("地址不可用", BluetoothDataUtils.maskAddress(null))
    }
}
