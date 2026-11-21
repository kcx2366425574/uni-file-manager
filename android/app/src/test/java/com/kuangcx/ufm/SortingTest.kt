package com.kuangcx.ufm

import com.kuangcx.ufm.core.Entry
import com.kuangcx.ufm.core.SortKey
import com.kuangcx.ufm.core.sortEntries
import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
    private val a = Entry("b.txt", "/x/b.txt", false, 300, 1_000, "text/plain")
    private val b = Entry("A.txt", "/x/A.txt", false, 100, 3_000, "text/plain")
    private val dir = Entry("dir", "/x/dir", true, 0, 2_000, "")

    @Test
    fun `directories always come first`() {
        assertEquals("dir", sortEntries(listOf(a, b, dir), SortKey.SIZE, asc = true).first().name)
    }

    @Test
    fun `name sort is case insensitive`() {
        assertEquals(listOf("A.txt", "b.txt"), sortEntries(listOf(a, b), SortKey.NAME, true).map { it.name })
    }

    @Test
    fun `size and time sort honour direction`() {
        assertEquals(listOf("A.txt", "b.txt"), sortEntries(listOf(a, b), SortKey.SIZE, true).map { it.name })
        assertEquals(listOf("b.txt", "A.txt"), sortEntries(listOf(a, b), SortKey.SIZE, false).map { it.name })
        assertEquals(listOf("A.txt", "b.txt"), sortEntries(listOf(a, b), SortKey.TIME, false).map { it.name })
    }

    @Test
    fun `equal keys fall back to name`() {
        val x = Entry("z.txt", "/x/z.txt", false, 1, 5, "")
        val y = Entry("y.txt", "/x/y.txt", false, 1, 5, "")
        assertEquals(listOf("y.txt", "z.txt"), sortEntries(listOf(x, y), SortKey.SIZE, true).map { it.name })
    }
}
