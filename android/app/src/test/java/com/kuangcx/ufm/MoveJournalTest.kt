package com.kuangcx.ufm

import com.kuangcx.ufm.core.MoveJournal
import com.kuangcx.ufm.core.MoveRecord
import com.kuangcx.ufm.core.MoveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoveJournalTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `records survive a new instance`() {
        val dir = tmp.newFolder()
        MoveJournal(dir).record(MoveRecord("id1", "/a/x.jpg", "/b/x.jpg", "/b/x.jpg.part", 10L, MoveState.COPYING))
        val reopened = MoveJournal(dir)
        assertEquals(1, reopened.all().size)
        assertEquals(MoveState.COPYING, reopened.all()[0].state)
    }

    @Test
    fun `state updates persist`() {
        val dir = tmp.newFolder()
        val j = MoveJournal(dir).apply {
            record(MoveRecord("id1", "/a/x.jpg", "/b/x.jpg", "/b/x.jpg.part", 10L, MoveState.COPYING))
        }
        j.updateState("id1", MoveState.DONE)
        assertEquals(MoveState.DONE, MoveJournal(dir).all()[0].state)
    }

    @Test
    fun `recovery aborts COPYING, finishes COPIED, keeps DONE`() {
        val dir = tmp.newFolder()
        val j = MoveJournal(dir)
        j.record(MoveRecord("c", "/a/c.jpg", "/b/c.jpg", "/b/c.jpg.part", 1, MoveState.COPYING))
        j.record(MoveRecord("p", "/a/p.jpg", "/b/p.jpg", "/b/p.jpg.part", 1, MoveState.COPIED))
        j.record(MoveRecord("d", "/a/d.jpg", "/b/d.jpg", "/b/d.jpg.part", 1, MoveState.DONE))
        val outcomes = mutableMapOf<String, MoveState>()
        j.recover { outcomes[it.id] = it.state }
        assertEquals(MoveState.ABORTED, outcomes["c"])
        assertEquals(MoveState.DONE, outcomes["p"])
        assertEquals(MoveState.DONE, outcomes["d"])
    }

    @Test
    fun `journal is capped so it cannot grow forever`() {
        val dir = tmp.newFolder()
        val j = MoveJournal(dir)
        repeat(300) { j.record(MoveRecord("id$it", "/a/$it", "/b/$it", "/b/$it.part", 1, MoveState.DONE)) }
        assertTrue(MoveJournal(dir).all().size <= 200)
    }
}
