package com.kuangcx.ufm

import android.os.Environment
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.NameRules
import com.kuangcx.ufm.core.PathUtil
import java.io.File

/**
 * Photo groups are real directories under Pictures/PhotoGroups, so the system gallery sees
 * them too. Refusing to delete a non-empty group is deliberate: the destructive path for
 * photos is the move, never an accidental recursive delete.
 */
@CapacitorPlugin(name = "Group")
class GroupPlugin : Plugin() {

    private val root: File
        get() = File(Environment.getExternalStorageDirectory(), "Pictures/PhotoGroups")

    private fun ensureRoot(): File {
        if (!root.exists()) root.mkdirs()
        return root
    }

    @PluginMethod
    fun list(call: PluginCall) {
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) {
                    call.reject("no all-files access", "E_PERM"); return@execute
                }
                val r = ensureRoot()
                val dirs = (r.listFiles() ?: emptyArray())
                    .filter { it.isDirectory }
                    .sortedBy { it.name.lowercase() }
                val arr = JSArray()
                dirs.forEach { d ->
                    val photos = photoFiles(d)
                    arr.put(JSObject().apply {
                        put("name", d.name)
                        put("path", d.absolutePath)
                        put("count", photos.size)
                        put("cover", photos.firstOrNull()?.absolutePath)
                    })
                }
                call.resolve(JSObject().apply {
                    put("root", r.absolutePath)
                    put("groups", arr)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "list groups failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun create(call: PluginCall) {
        val name = call.getString("name") ?: ""
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) {
                    call.reject("no all-files access", "E_PERM"); return@execute
                }
                val r = ensureRoot()
                val existing = (r.listFiles() ?: emptyArray()).filter { it.isDirectory }.map { it.name }
                val err = NameRules.validateGroupName(name, existing)
                if (err != null) { call.reject(err, "E_BAD_NAME"); return@execute }
                val dir = File(r, name)
                if (!dir.mkdirs()) { call.reject("mkdir failed", "E_IO"); return@execute }
                call.resolve(JSObject().apply { put("path", dir.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "create group failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun rename(call: PluginCall) {
        val path = call.getString("path")
        val newName = call.getString("newName") ?: ""
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) {
                    call.reject("no all-files access", "E_PERM"); return@execute
                }
                val src = PathUtil.safe(path)
                if (!src.isDirectory) { call.reject("not found", "E_NOENT"); return@execute }
                // The group's own name is excluded so a case-only rename stays possible.
                val others = (src.parentFile?.listFiles() ?: emptyArray())
                    .filter { it.isDirectory && it.name != src.name }.map { it.name }
                val err = NameRules.validateGroupName(newName, others)
                if (err != null) { call.reject(err, "E_BAD_NAME"); return@execute }
                val dst = File(src.parentFile, newName)
                if (dst.exists() && dst.absolutePath != src.absolutePath) {
                    call.reject("exists", "E_EXISTS"); return@execute
                }
                if (!src.renameTo(dst)) { call.reject("rename failed", "E_RENAME_FAILED"); return@execute }
                call.resolve(JSObject().apply { put("path", dst.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "rename group failed", "E_IO", e)
            }
        }
    }

    /** Removes only the empty directory shell; photos must be moved out first. */
    @PluginMethod
    fun remove(call: PluginCall) {
        val path = call.getString("path")
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) {
                    call.reject("no all-files access", "E_PERM"); return@execute
                }
                val dir = PathUtil.safe(path)
                if (!dir.isDirectory) { call.reject("not found", "E_NOENT"); return@execute }
                val photos = photoFiles(dir)
                val leftovers = (dir.listFiles() ?: emptyArray()).size
                if (leftovers > 0) {
                    call.reject("分组内还有 $leftovers 个文件，请先移出", "E_NOT_EMPTY"); return@execute
                }
                if (!dir.delete()) { call.reject("delete failed", "E_IO"); return@execute }
                call.resolve()
            } catch (e: Exception) {
                call.reject(e.message ?: "remove group failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun photos(call: PluginCall) {
        val path = call.getString("path")
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) {
                    call.reject("no all-files access", "E_PERM"); return@execute
                }
                val dir = PathUtil.safe(path)
                if (!dir.isDirectory) { call.reject("not found", "E_NOENT"); return@execute }
                val arr = JSArray()
                photoFiles(dir).forEach { f ->
                    arr.put(JSObject().apply {
                        put("name", f.name)
                        put("path", f.absolutePath)
                        put("size", f.length())
                        put("mtime", f.lastModified())
                        put("mime", PathUtil.mimeOf(f))
                    })
                }
                call.resolve(JSObject().apply { put("photos", arr) })
            } catch (e: Exception) {
                call.reject(e.message ?: "list photos failed", "E_IO", e)
            }
        }
    }

    private fun photoFiles(dir: File): List<File> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && PathUtil.mimeOf(it).startsWith("image/") }
            .sortedByDescending { it.lastModified() }
}
