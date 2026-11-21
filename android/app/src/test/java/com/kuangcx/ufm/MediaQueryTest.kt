package com.kuangcx.ufm

import com.kuangcx.ufm.core.MediaKind
import com.kuangcx.ufm.core.MediaQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaQueryTest {

    @Test
    fun `typed collections must not filter on media_type`() {
        // Images/Video/Audio collections already contain one kind. Adding media_type makes
        // MediaStore throw "Invalid token media_type" (verified on API 36).
        val spec = MediaQuery.build(MediaKind.IMAGE, null, 200, 0, MediaQuery.Collection.TYPED)
        assertNull(spec.selection)
    }

    @Test
    fun `files collection filters images by the integer media type`() {
        val spec = MediaQuery.build(MediaKind.IMAGE, null, 1, 0, MediaQuery.Collection.FILES)
        assertEquals("media_type=1", spec.selection)
    }

    @Test
    fun `files collection filters documents by excluding the three media types`() {
        val spec = MediaQuery.build(MediaKind.DOC, null, 200, 0, MediaQuery.Collection.FILES)
        assertEquals("media_type NOT IN (1,2,3)", spec.selection)
    }

    @Test
    fun `paging maps to limit and offset with a positive safety cap`() {
        val spec = MediaQuery.build(MediaKind.IMAGE, null, 5000, 400, MediaQuery.Collection.TYPED)
        assertEquals(1000, spec.limit)
        assertEquals(400, spec.offset)
    }

    @Test
    fun `sinceMs adds a date filter in epoch seconds`() {
        val spec = MediaQuery.build(MediaKind.DOC, 1_700_000_000_000L, 200, 0, MediaQuery.Collection.FILES)
        assertEquals("media_type NOT IN (1,2,3) AND date_added>=?", spec.selection)
        assertEquals(listOf("1700000000"), spec.selectionArgs!!.toList())
    }

    @Test
    fun `sort is newest first`() {
        assertEquals(
            "date_added DESC",
            MediaQuery.build(MediaKind.IMAGE, null, 10, 0, MediaQuery.Collection.TYPED).sortOrder
        )
    }
}
