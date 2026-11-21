package com.kuangcx.ufm

import com.kuangcx.ufm.core.OpenIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenIntentTest {

    @Test
    fun `a view intent is recognised and routes to the open page`() {
        val r = OpenIntent.parse("android.intent.action.VIEW", "content://downloads/all_downloads/42", "application/vnd.android.package-archive")
        assertTrue(r.isOpen)
        assertEquals("/pages/open/open?uri=content%3A%2F%2Fdownloads%2Fall_downloads%2F42&mime=application%2Fvnd.android.package-archive", r.route)
    }

    @Test
    fun `a send intent with an image is recognised`() {
        val r = OpenIntent.parse("android.intent.action.SEND", "content://media/external/images/media/7", "image/jpeg")
        assertTrue(r.isOpen)
        assertTrue(r.route!!.contains("mime=image%2Fjpeg"))
    }

    @Test
    fun `the launcher intent is not an open request`() {
        val r = OpenIntent.parse("android.intent.action.MAIN", null, null)
        assertFalse(r.isOpen)
        assertEquals(null, r.route)
    }

    @Test
    fun `a view intent without data is ignored`() {
        assertFalse(OpenIntent.parse("android.intent.action.VIEW", null, null).isOpen)
    }

    @Test
    fun `a missing mime falls back to an empty parameter`() {
        val r = OpenIntent.parse("android.intent.action.VIEW", "content://x/1", null)
        assertTrue(r.isOpen)
        assertTrue(r.route!!.contains("mime="))
    }
}
