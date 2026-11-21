package com.kuangcx.ufm

import com.kuangcx.ufm.core.shouldCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateGateTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test
    fun `checks when no previous check was recorded`() {
        assertTrue(shouldCheck(now, 0, day))
    }

    @Test
    fun `skips inside the window`() {
        assertFalse(shouldCheck(now, now - day + 1, day))
    }

    @Test
    fun `checks once the window elapsed`() {
        assertTrue(shouldCheck(now, now - day, day))
    }

    @Test
    fun `checks when the clock moved backwards instead of locking out`() {
        assertTrue(shouldCheck(now, now + day, day))
    }
}
