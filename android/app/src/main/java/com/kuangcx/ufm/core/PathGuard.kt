package com.kuangcx.ufm.core

import android.os.Environment
import java.io.File

/**
 * Refuses deletions whose loss would damage the device rather than the user's own files.
 * A file manager holding MANAGE_EXTERNAL_STORAGE can reach everything, so the guard has to live
 * in code: deleting /storage/emulated/0 takes DCIM, Android/data and every app's data with it.
 *
 * Deliberately narrow — it blocks the storage roots and the system directories directly under
 * them, not ordinary folders the user created.
 */
object PathGuard {

    private val SYSTEM_DIRS = setOf(
        "Android", "DCIM", "Pictures", "Download", "Movies", "Music", "Documents", "Recordings"
    )

    /**
     * @param root the external storage root, passed in rather than read from Environment so the
     * policy is unit-testable on a JVM (Environment's static field is gone in Java 9+).
     * @return a Chinese reason when [target] must not be deleted, otherwise null.
     */
    fun denyDelete(target: File, root: String): String? {
        val canonicalTarget = runCatching { target.canonicalPath }.getOrNull() ?: return "路径无法解析"
        val canonicalRoot = runCatching { File(root).canonicalPath }.getOrDefault(root)
        if (canonicalTarget == canonicalRoot) return "不能删除存储根目录"
        if (canonicalTarget == "/storage" || canonicalTarget == "/sdcard") return "不能删除存储根目录"
        val parent = target.parentFile?.let { runCatching { it.canonicalPath }.getOrNull() }
        if (parent == canonicalRoot && target.name in SYSTEM_DIRS) return "不能删除系统目录"
        return null
    }

    /** Convenience overload for production code. */
    fun denyDelete(target: File): String? =
        denyDelete(target, Environment.getExternalStorageDirectory().absolutePath)
}
