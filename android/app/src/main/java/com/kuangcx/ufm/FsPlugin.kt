package com.kuangcx.ufm

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.Entry
import com.kuangcx.ufm.core.MoveEngine
import com.kuangcx.ufm.core.MoveJournal
import com.kuangcx.ufm.core.MoveState
import com.kuangcx.ufm.core.PathUtil
import com.kuangcx.ufm.core.SortKey
import com.kuangcx.ufm.core.StorageVolumes
import com.kuangcx.ufm.core.sortEntries

@CapacitorPlugin(name = "Fs")
class FsPlugin : Plugin() {

    @PluginMethod
    fun volumes(call: PluginCall) {
        bridge.execute {
            try {
                val arr = JSArray()
                StorageVolumes.list(context).forEach { v ->
                    arr.put(JSObject().apply {
                        put("label", v.label)
                        put("path", v.path)
                        put("removable", v.removable)
                        put("availableBytes", v.availableBytes)
                    })
                }
                call.resolve(JSObject().apply { put("volumes", arr) })
            } catch (e: Exception) {
                call.reject(e.message ?: "volume enumeration failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun listDir(call: PluginCall) {
        val path = call.getString("path")
        val sortKey = when (call.getString("sort") ?: "name") {
            "size" -> SortKey.SIZE
            "time" -> SortKey.TIME
            "type" -> SortKey.TYPE
            else -> SortKey.NAME
        }
        val asc = call.getBoolean("asc") ?: true
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) {
                    call.reject("no all-files access", "E_PERM")
                    return@execute
                }
                val dir = PathUtil.safe(path)
                if (!dir.exists()) {
                    call.reject("not found", "E_NOENT")
                    return@execute
                }
                if (!dir.isDirectory) {
                    call.reject("not a directory", "E_IO")
                    return@execute
                }
                val files = dir.listFiles() ?: throw java.io.IOException("unreadable directory")
                val raw = files.map { f ->
                    Entry(
                        f.name,
                        f.absolutePath,
                        f.isDirectory,
                        if (f.isDirectory) 0L else f.length(),
                        f.lastModified(),
                        if (f.isDirectory) "" else PathUtil.mimeOf(f)
                    )
                }
                val arr = JSArray()
                sortEntries(raw, sortKey, asc).forEach { e ->
                    arr.put(JSObject().apply {
                        put("name", e.name); put("path", e.path); put("dir", e.dir)
                        put("size", e.size); put("mtime", e.mtime); put("mime", e.mime)
                    })
                }
                call.resolve(JSObject().apply {
                    put("path", dir.absolutePath)
                    put("entries", arr)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "list failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun mkdir(call: PluginCall) {
        val parent = call.getString("parent")
        val name = call.getString("name")
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
                val base = PathUtil.safe(parent)
                if (!base.isDirectory) { call.reject("parent missing", "E_NOENT"); return@execute }
                // A name is a single path segment; separators would escape the parent directory.
                if (name.isNullOrBlank() || name.contains('/') || name == "." || name == "..") {
                    call.reject("bad name", "E_BAD_NAME"); return@execute
                }
                val target = java.io.File(base, name)
                if (target.exists()) { call.reject("exists", "E_EXISTS"); return@execute }
                if (!target.mkdirs()) { call.reject("mkdir failed", "E_IO"); return@execute }
                call.resolve(JSObject().apply { put("path", target.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "mkdir failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun rename(call: PluginCall) {
        val path = call.getString("path")
        val newName = call.getString("newName")
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
                val src = PathUtil.safe(path)
                if (!src.exists()) { call.reject("not found", "E_NOENT"); return@execute }
                if (newName.isNullOrBlank() || newName.contains('/') || newName == "." || newName == "..") {
                    call.reject("bad name", "E_BAD_NAME"); return@execute
                }
                val dst = java.io.File(src.parentFile, newName)
                if (dst.exists() && dst.absolutePath != src.absolutePath) {
                    call.reject("exists", "E_EXISTS"); return@execute
                }
                if (!src.renameTo(dst)) { call.reject("rename failed", "E_RENAME_FAILED"); return@execute }
                lightRescan(src.absolutePath, dst.absolutePath)
                call.resolve(JSObject().apply { put("path", dst.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "rename failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun remove(call: PluginCall) {
        // JSArray.toList() returns List<Any?> which defeats type inference here; read by index.
        val arr = call.getArray("paths")
        val paths = ArrayList<String>()
        if (arr != null) { for (i in 0 until arr.length()) paths.add(arr.getString(i)) }
        bridge.execute {
            if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
            val removed = JSArray()
            val failed = JSArray()
            for (p in paths) {
                try {
                    val f = PathUtil.safe(p)
                    if (!f.exists()) { failed.put(errObj(p, "E_NOENT")); continue }
                    // A file manager holding MANAGE_EXTERNAL_STORAGE can delete DCIM or the storage
                    // root in one pass; refuse before touching anything.
                    val denied = com.kuangcx.ufm.core.PathGuard.denyDelete(f)
                    if (denied != null) { failed.put(errObj(p, "E_PERM")); continue }
                    if (!deleteRecursively(f)) { failed.put(errObj(p, "E_IO")); continue }
                    removed.put(p)
                } catch (e: Exception) {
                    failed.put(errObj(p, "E_IO"))
                }
            }
            lightRescan(*paths.toTypedArray())
            call.resolve(JSObject().apply { put("removed", removed); put("failed", failed) })
        }
    }

    private fun errObj(path: String, code: String) = JSObject().apply { put("path", path); put("code", code) }

    private fun deleteRecursively(f: java.io.File): Boolean {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursively(it) }
        return f.delete()
    }

    /**
     * MediaScannerConnection has no public synchronous API; scanning also runs on the bridge's
     * looper thread, so a fire-and-forget call is the supported usage.
     */
    private fun lightRescan(vararg paths: String) {
        paths.forEach { p ->
            android.media.MediaScannerConnection.scanFile(context, arrayOf(p), null, null)
        }
    }

    // --- move / copy -------------------------------------------------------

    private lateinit var journal: MoveJournal
    private lateinit var engine: MoveEngine

    override fun load() {
        super.load()
        journal = MoveJournal(java.io.File(context.filesDir, "journal"))
        engine = MoveEngine(journal) { a, b ->
            volumeRootOf(a.absolutePath) != volumeRootOf(b.absolutePath)
        }
        // Finish or roll back whatever a previous process left in flight.
        bridge.execute {
            engine.recover().forEach { r ->
                if (r.state == MoveState.DONE) lightRescan(r.src, r.dst)
            }
        }
    }

    /** Longest matching volume root, so /storage/XXXX-XXXX wins over /storage/emulated/0. */
    private fun volumeRootOf(path: String): String =
        StorageVolumes.list(context)
            .filter { path == it.path || path.startsWith(it.path + "/") }
            .maxByOrNull { it.path.length }
            ?.path ?: "/"

    @PluginMethod
    fun move(call: PluginCall) {
        val paths = stringList(call, "paths")
        val destDir = call.getString("destDir")
        bridge.execute { transfer(call, paths, destDir, isMove = true) }
    }

    @PluginMethod
    fun copy(call: PluginCall) {
        val paths = stringList(call, "paths")
        val destDir = call.getString("destDir")
        bridge.execute { transfer(call, paths, destDir, isMove = false) }
    }

    /** JSArray.toList() is List<Any?> and defeats inference, so read by index. */
    private fun stringList(call: PluginCall, key: String): List<String> {
        val arr = call.getArray(key)
        val out = ArrayList<String>()
        if (arr != null) { for (i in 0 until arr.length()) out.add(arr.getString(i)) }
        return out
    }

    private fun transfer(call: PluginCall, paths: List<String>, destDir: String?, isMove: Boolean) {
        if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return }
        val dest = try { PathUtil.safe(destDir) } catch (e: Exception) {
            call.reject("bad destination", "E_BAD_NAME"); return
        }
        if (!dest.isDirectory) { call.reject("destination is not a directory", "E_NOENT"); return }
        val done = JSArray()
        val failed = JSArray()
        for (p in paths) {
            try {
                val src = PathUtil.safe(p)
                val r = if (isMove) engine.moveFile(src, dest) else engine.copyFile(src, dest)
                if (r.ok) {
                    done.put(p)
                    lightRescan(src.absolutePath, java.io.File(dest, src.name).absolutePath)
                } else {
                    failed.put(errObj(p, r.code ?: "E_IO"))
                }
            } catch (e: Exception) {
                failed.put(errObj(p, "E_IO"))
            }
        }
        call.resolve(JSObject().apply {
            put(if (isMove) "moved" else "copied", done)
            put("failed", failed)
        })
    }
}
