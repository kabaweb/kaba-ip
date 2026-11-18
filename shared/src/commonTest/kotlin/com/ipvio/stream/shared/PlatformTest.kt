package com.ipvio.stream.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun testPlatformInfo() {
        val info = KmpInfo.info()
        assertTrue(info.startsWith("KorpTV-Shared-KMP"))
    }
}
