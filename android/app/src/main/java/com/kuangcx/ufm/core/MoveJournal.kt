package com.kuangcx.ufm.core

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

enum class MoveState { COPYING, COPIED, DONE, ABORTED, FAILED }

data class MoveRecord(
    val id: String,
    val src: String,
    val dst: String,
    val part: String,
    val size: Long,
    val state: MoveState
)

/**
 * Append-mostly journal persisted as a line-oriented text file. One record per line, fields
 * URL-encoded so a path containing a separator, tab or newline cannot corrupt the file.
 *
 * Deliberately free of org.json: Android's JVM unit tests ship a not-mocked org.json stub, and
 * a journal that cannot be unit-tested is a journal nobody tests.
 */
class MoveJournal(private val dir: File, private val maxEntries: Int = 200) {

    private val file = File(dir, "move-journal.txt")
    private val entries = linkedMapOf<String, MoveRecord>()

    init {
        load()
    }

    @Synchronized
    fun record(entry: MoveRecord) {
        entries[entry.id] = entry
        trim()
        save()
    }

    @Synchronized
    fun updateState(id: String, state: MoveState) {
        entries[id]?.let { entries[id] = it.copy(state = state); save() }
    }

    @Synchronized
    fun all(): List<MoveRecord> = entries.values.toList()

    /**
     * Crash recovery. COPYING entries are aborted (part file deleted, source kept);
     * COPIED entries are marked DONE because their verified copy already exists. Every
     * record is reported to [onSettled], including ones that were already DONE, so a caller
     * can log the whole recovery pass and rescan exactly the paths it touched.
     */
    @Synchronized
    fun recover(onSettled: (MoveRecord) -> Unit): List<MoveRecord> {
        if (entries.isEmpty()) return emptyList()
        for (r in entries.values.toList()) {
            when (r.state) {
                MoveState.COPYING -> {
                    runCatching { File(r.part).delete() }
                    entries[r.id] = r.copy(state = MoveState.ABORTED)
                }
                MoveState.COPIED -> {
                    entries[r.id] = r.copy(state = MoveState.DONE)
                }
                else -> Unit
            }
        }
        save()
        val settled = entries.values.toList()
        settled.forEach(onSettled)
        return settled
    }

    private fun trim() {
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            file.readLines().filter { it.isNotBlank() }.forEach { line ->
                val f = line.split(SEP).map { URLDecoder.decode(it, "UTF-8") }
                if (f.size == 6) {
                    val rec = MoveRecord(f[0], f[1], f[2], f[3], f[4].toLong(), MoveState.valueOf(f[5]))
                    entries[rec.id] = rec
                }
            }
        }.onFailure { entries.clear() } // A corrupt journal must never block startup.
    }

    private fun save() {
        val text = entries.values.joinToString(NEWLINE) { r ->
            listOf(r.id, r.src, r.dst, r.part, r.size.toString(), r.state.name)
                .joinToString(SEP) { URLEncoder.encode(it, "UTF-8") }
        }
        val tmp = File(dir, "move-journal.txt.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    private companion object {
        /** Every field is URL-encoded first, so the encoder turns any '|' in a path into %7C. */
        const val SEP = "|"
        const val NEWLINE = "\n"
    }
}
