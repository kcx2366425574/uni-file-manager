package com.kuangcx.ufm

import com.kuangcx.ufm.core.PathUtil
import org.junit.Assert.assertThrows
import org.junit.Test

class PathUtilTest {
    @Test
    fun `rejects a blank path`() {
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe("") }
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe(null) }
    }

    @Test
    fun `rejects a relative path`() {
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe("DCIM/Camera") }
    }

    @Test
    fun `rejects traversal segments`() {
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe("/sdcard/DCIM/../../etc") }
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe("/sdcard/DCIM/..") }
    }

    @Test
    fun `mime lookup covers photos, video and apk`() {
        org.junit.Assert.assertEquals("image/jpeg", PathUtil.mimeOf(java.io.File("/tmp/a.JPG")))
        org.junit.Assert.assertEquals("application/vnd.android.package-archive", PathUtil.mimeOf(java.io.File("/tmp/b.apk")))
        org.junit.Assert.assertEquals("application/octet-stream", PathUtil.mimeOf(java.io.File("/tmp/c.bin")))
    }
}
