package com.kuangcx.ufm

import org.junit.Assert.assertEquals
import org.junit.Test

class PermStateTest {
    @Test
    fun `sdk below 30 never reports all files access`() {
        assertEquals(false, PermState.resolve(28, true, true, false).allFiles)
    }

    @Test
    fun `sdk 30 and above reports the granted flag`() {
        assertEquals(true, PermState.resolve(30, true, true, false).allFiles)
        assertEquals(false, PermState.resolve(36, false, true, false).allFiles)
    }
}
