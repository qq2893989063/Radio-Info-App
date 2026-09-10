package com.radioinfo.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcPayloadUtilsTest {
    @Test
    fun acceptsOnlyApplicationPackageNames() {
        assertTrue(NfcPayloadUtils.isSafePackageName("com.example.reader"))
        assertFalse(NfcPayloadUtils.isSafePackageName("https://example.com"))
        assertFalse(NfcPayloadUtils.isSafePackageName("com.example/../../data"))
        assertFalse(NfcPayloadUtils.isSafePackageName("example"))
    }
}
