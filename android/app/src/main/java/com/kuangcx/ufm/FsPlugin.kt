package com.kuangcx.ufm

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.Entry
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
}
