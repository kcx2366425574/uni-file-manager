package com.kuangcx.ufm.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32

/**
 * Moves files without ever risking the source. Same-volume moves are a single atomic
 * rename. Cross-volume moves go copy -> fsync -> verify -> rename -> delete-source, with
 * every step journalled so a killed process can recover on next launch.
 */
class MoveEngine(
    private val journal: MoveJournal,
    private val crossVolume: (File, File) -> Boolean
) {

    data class Result(val ok: Boolean, val code: String? = null, val message: String? = null)

    fun moveFile(src: File, destDir: File): Result {
        if (!src.exists()) return Result(false, "E_NOENT", "source missing")
        if (src.isDirectory) return Result(false, "E_IO", "directories are not moved by this engine")
        if (!destDir.exists() && !destDir.mkdirs()) return Result(false, "E_IO", "cannot create dest")
        val dst = File(destDir, src.name)
        if (dst.exists()) return Result(false, "E_EXISTS", "destination exists")

        return if (!crossVolume(src, destDir)) {
            // Same volume: atomic. A failure here leaves the source exactly as it was.
            if (src.renameTo(dst)) Result(true)
            else Result(false, "E_RENAME_FAILED", "rename failed")
        } else {
            moveAcrossVolumes(src, dst)
        }
    }

    fun copyFile(src: File, destDir: File): Result {
        if (!src.exists()) return Result(false, "E_NOENT", "source missing")
        if (src.isDirectory) return Result(false, "E_IO", "directories are not copied by this engine")
        if (!destDir.exists() && !destDir.mkdirs()) return Result(false, "E_IO", "cannot create dest")
        val dst = File(destDir, src.name)
        if (dst.exists()) return Result(false, "E_EXISTS", "destination exists")
        val part = File(destDir, src.name + ".part")
        return try {
            copyAndSync(src, part)
            if (part.length() != src.length()) {
                part.delete()
                return Result(false, "E_COPY_FAILED", "size mismatch")
            }
            if (src.length() > BIG_FILE_BYTES && crc32(src) != crc32(part)) {
                part.delete()
                return Result(false, "E_COPY_FAILED", "checksum mismatch")
            }
            if (!part.renameTo(dst)) {
                part.delete()
                return Result(false, "E_COPY_FAILED", "rename of part failed")
            }
            Result(true)
        } catch (e: Exception) {
            runCatching { part.delete() }
            Result(false, "E_COPY_FAILED", e.message ?: "copy failed")
        }
    }

    private fun moveAcrossVolumes(src: File, dst: File): Result {
        // Parenthesised: without the inner parens the elvis binds looser than `<`.
        if ((dst.parentFile?.usableSpace ?: 0L) < (src.length() * SPACE_SAFETY).toLong()) {
            return Result(false, "E_NO_SPACE", "not enough space")
        }
        val part = File(dst.absolutePath + ".part")
        val id = UUID.randomUUID().toString()
        journal.record(
            MoveRecord(id, src.absolutePath, dst.absolutePath, part.absolutePath, src.length(), MoveState.COPYING)
        )
        return try {
            copyAndSync(src, part)
            if (part.length() != src.length()) throw java.io.IOException("size mismatch")
            if (src.length() > BIG_FILE_BYTES && crc32(src) != crc32(part)) {
                throw java.io.IOException("checksum mismatch")
            }
            journal.updateState(id, MoveState.COPIED)
            if (!part.renameTo(dst)) throw java.io.IOException("rename of part failed")
            if (!src.delete()) {
                // The destination is complete and verified; keep the journal at COPIED so a later
                // pass retries the deletion instead of losing track of the leftover source.
                return Result(false, "E_IO", "copied but source could not be deleted")
            }
            journal.updateState(id, MoveState.DONE)
            Result(true)
        } catch (e: Exception) {
            runCatching { part.delete() }
            runCatching { if (dst.exists() && dst.length() != src.length()) dst.delete() }
            journal.updateState(id, MoveState.FAILED)
            Result(false, "E_COPY_FAILED", e.message ?: "copy failed")
        }
    }

    /** Finishes or rolls back work interrupted by a process death. */
    fun recover(): List<MoveRecord> {
        val out = mutableListOf<MoveRecord>()
        journal.recover { r ->
            if (r.state == MoveState.COPIED) {
                // The verified copy exists; only the source deletion is outstanding.
                val src = File(r.src)
                val dst = File(r.dst)
                if (dst.exists() && dst.length() == r.size) {
                    src.delete()
                    journal.updateState(r.id, MoveState.DONE)
                }
            }
            out += r
        }
        return out
    }

    private fun copyAndSync(src: File, part: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(part).use { output ->
                input.copyTo(output, BUFFER)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun crc32(f: File): Long = FileInputStream(f).use { input ->
        val crc = CRC32()
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            crc.update(buf, 0, n)
        }
        crc.value
    }

    companion object {
        const val BIG_FILE_BYTES = 8L * 1024 * 1024
        const val BUFFER = 256 * 1024
        /** Headroom so a nearly-full target fails before writing half a file. */
        const val SPACE_SAFETY = 1.1
    }
}
