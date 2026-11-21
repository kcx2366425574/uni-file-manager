package com.kuangcx.ufm

import com.kuangcx.ufm.core.FavItem
import com.kuangcx.ufm.core.dedupeByPath
import com.kuangcx.ufm.core.partitionMissing
import org.junit.Assert.assertEquals
import org.junit.Test

class FavStoreTest {

    @Test
    fun `items whose file vanished are reported as missing`() {
        val items = listOf(
            FavItem("/a/1.jpg", 1, 100),
            FavItem("/a/2.jpg", 2, 200)
        )
        val (alive, gone) = partitionMissing(items) { it == "/a/1.jpg" }
        assertEquals(listOf("/a/1.jpg"), alive.map { it.path })
        assertEquals(listOf("/a/2.jpg"), gone.map { it.path })
    }

    @Test
    fun `empty favourites produce empty partitions`() {
        val (alive, gone) = partitionMissing(emptyList<FavItem>()) { true }
        assertEquals(0, alive.size)
        assertEquals(0, gone.size)
    }

    @Test
    fun `dedupe keeps the earliest addedAt per path`() {
        val merged = dedupeByPath(
            listOf(FavItem("/a/1.jpg", 1, 200), FavItem("/a/1.jpg", 1, 100), FavItem("/a/2.jpg", 2, 50))
        )
        assertEquals(2, merged.size)
        assertEquals(100, merged.first { it.path == "/a/1.jpg" }.addedAt)
    }
}
