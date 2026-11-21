package com.kuangcx.ufm

import com.kuangcx.ufm.core.PathGuard
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PathGuardTest {

    private val root = "/storage/emulated/0"

    @Test
    fun `storage root cannot be deleted`() {
        assertNotNull(PathGuard.denyDelete(File(root), root))
    }

    @Test
    fun `system directories directly under the root cannot be deleted`() {
        for (name in listOf("Android", "DCIM", "Pictures", "Download", "Movies", "Music", "Documents")) {
            assertNotNull("$name must be protected", PathGuard.denyDelete(File("$root/$name"), root))
        }
    }

    @Test
    fun `an ordinary user folder can be deleted`() {
        assertNull(PathGuard.denyDelete(File("$root/Download/trip-2026"), root))
        assertNull(PathGuard.denyDelete(File("$root/DCIM/Camera/IMG_0001.jpg"), root))
        assertNull(PathGuard.denyDelete(File("$root/Pictures/PhotoGroups/旅行"), root))
    }

    @Test
    fun `a folder inside a system directory can be deleted`() {
        assertNull(PathGuard.denyDelete(File("$root/DCIM/Camera"), root))
    }

    @Test
    fun `a user folder whose name merely contains a system name is allowed`() {
        assertNull(PathGuard.denyDelete(File("$root/Download/DCIM-backup"), root))
    }
}
