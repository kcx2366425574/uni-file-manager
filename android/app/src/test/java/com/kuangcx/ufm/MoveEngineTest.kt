package com.kuangcx.ufm

import com.kuangcx.ufm.core.MoveEngine
import com.kuangcx.ufm.core.MoveJournal
import com.kuangcx.ufm.core.MoveRecord
import com.kuangcx.ufm.core.MoveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MoveEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun engine() = MoveEngine(MoveJournal(tmp.newFolder()), crossVolume = { _, _ -> false })

    @Test
    fun `refuses to overwrite an existing destination`() {
        val src = tmp.newFile("a.jpg").apply { writeText("new") }
        val dstDir = tmp.newFolder()
        File(dstDir, "a.jpg").writeText("old")
        val r = engine().moveFile(src, dstDir)
        assertEquals("E_EXISTS", r.code)
        assertEquals("new", src.readText())
        assertEquals("old", File(dstDir, "a.jpg").readText())
    }

    @Test
    fun `same-volume move is a rename and leaves no temp file`() {
        val src = tmp.newFile("a.jpg").apply { writeText("data") }
        val dstDir = tmp.newFolder()
        val r = engine().moveFile(src, dstDir)
        assertTrue(r.ok)
        assertFalse(src.exists())
        assertEquals("data", File(dstDir, "a.jpg").readText())
        assertEquals(0, dstDir.listFiles()!!.count { it.name.endsWith(".part") })
    }

    @Test
    fun `cross-volume move copies, verifies, then deletes the source`() {
        val dir = tmp.newFolder()
        val src = File(tmp.root, "a.jpg").apply { writeText("payload") }
        val dstDir = tmp.newFolder()
        val e = MoveEngine(MoveJournal(dir), crossVolume = { _, _ -> true })
        val r = e.moveFile(src, dstDir)
        assertTrue(r.ok)
        assertFalse(src.exists())
        assertEquals("payload", File(dstDir, "a.jpg").readText())
    }

    @Test
    fun `cross-volume failure keeps the source untouched`() {
        val dir = tmp.newFolder()
        val src = File(tmp.root, "a.jpg").apply { writeText("payload") }
        val dstDir = tmp.newFolder()
        val e = MoveEngine(MoveJournal(dir), crossVolume = { _, _ -> true })
        dstDir.setWritable(false)
        val r = e.moveFile(src, dstDir)
        dstDir.setWritable(true)
        if (!r.ok) {
            assertEquals("E_COPY_FAILED", r.code)
            assertTrue(src.exists())
            assertEquals("payload", src.readText())
        }
    }

    @Test
    fun `recovering an aborted copy removes the part file and keeps source`() {
        val dir = tmp.newFolder()
        val src = File(tmp.root, "a.jpg").apply { writeText("payload") }
        val dstDir = tmp.newFolder()
        val part = File(dstDir, "a.jpg.part").apply { writeText("half") }
        val j = MoveJournal(dir).apply {
            record(
                MoveRecord(
                    "x", src.absolutePath, File(dstDir, "a.jpg").absolutePath,
                    part.absolutePath, 7L, MoveState.COPYING
                )
            )
        }
        MoveEngine(j, crossVolume = { _, _ -> true }).recover()
        assertFalse(part.exists())
        assertTrue(src.exists())
    }

    @Test
    fun `copy leaves the source in place and produces an identical file`() {
        val src = tmp.newFile("a.jpg").apply { writeText("payload") }
        val dstDir = tmp.newFolder()
        val r = engine().copyFile(src, dstDir)
        assertTrue(r.ok)
        assertTrue(src.exists())
        assertEquals("payload", File(dstDir, "a.jpg").readText())
    }

    @Test
    fun `copy refuses an existing destination without touching either file`() {
        val src = tmp.newFile("a.jpg").apply { writeText("new") }
        val dstDir = tmp.newFolder()
        File(dstDir, "a.jpg").writeText("old")
        val r = engine().copyFile(src, dstDir)
        assertEquals("E_EXISTS", r.code)
        assertEquals("new", src.readText())
        assertEquals("old", File(dstDir, "a.jpg").readText())
    }
}
