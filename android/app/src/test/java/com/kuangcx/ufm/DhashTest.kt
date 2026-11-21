package com.kuangcx.ufm

import com.kuangcx.ufm.core.dhash
import com.kuangcx.ufm.core.hamming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DhashTest {

    /** A 9x8 horizontal gradient: every right neighbour is brighter than its left one. */
    private fun gradient(): IntArray {
        val w = 9
        val h = 8
        return IntArray(w * h) { i -> (i % w) * 32 }
    }

    @Test
    fun `identical images produce identical hashes`() {
        val a = gradient()
        assertEquals(dhash(a, 9, 8), dhash(a.copyOf(), 9, 8))
    }

    @Test
    fun `hamming distance of a hash with itself is zero`() {
        assertEquals(0, hamming(0x1234_5678_9ABC_DEF0L, 0x1234_5678_9ABC_DEF0L))
    }

    @Test
    fun `hamming counts differing bits`() {
        assertEquals(1, hamming(0b1010L, 0b1011L))
        assertEquals(3, hamming(0b0000L, 0b0111L))
    }

    @Test
    fun `a single flipped bit stays inside the similar threshold`() {
        val h = dhash(gradient(), 9, 8)
        assertTrue(hamming(h, h xor 0b1L) <= 6)
    }

    @Test
    fun `a flat image yields zero hash because no gradient exists`() {
        assertEquals(0L, dhash(IntArray(9 * 8) { 128 }, 9, 8))
    }
}
