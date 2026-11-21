package com.kuangcx.ufm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.CallNum
import com.kuangcx.ufm.core.PathUtil
import com.kuangcx.ufm.core.Similarity
import com.kuangcx.ufm.core.dhash
import com.kuangcx.ufm.core.hamming
import java.io.File
import java.security.MessageDigest

@CapacitorPlugin(name = "Cleanup")
class CleanupPlugin : Plugin() {

    /**
     * Exact duplicates: same byte length plus identical SHA-256 — cheap and no false positives.
     * Near duplicates: dHash within [Similarity.SIMILAR_THRESHOLD]. The two passes never overlap
     * because the second only looks at files the first did not already group.
     */
    @PluginMethod
    fun scanDuplicates(call: PluginCall) {
        val limit = (CallNum.long(call, "limit", 400L)).toInt().coerceIn(1, 2000)
        bridge.execute {
            try {
                val files = collectImages(limit)

                val exactGroups = mutableListOf<List<File>>()
                files.groupBy { it.length() }.forEach { (_, sameSize) ->
                    if (sameSize.size < 2) return@forEach
                    sameSize.groupBy { sha256(it) }.values
                        .filter { it.size >= 2 }
                        .forEach { exactGroups += it }
                }
                val exactPaths = exactGroups.flatten().toHashSet()

                val remaining = files.filter { it !in exactPaths }
                val hashes = remaining.mapNotNull { f -> fingerprint(f)?.let { f to it } }
                val similarGroups = mutableListOf<List<File>>()
                val used = mutableSetOf<File>()
                for (i in hashes.indices) {
                    val (fa, ha) = hashes[i]
                    if (fa in used) continue
                    val bucket = mutableListOf(fa)
                    for (j in i + 1 until hashes.size) {
                        val (fb, hb) = hashes[j]
                        if (fb in used) continue
                        if (hamming(ha, hb) <= Similarity.SIMILAR_THRESHOLD) {
                            bucket += fb
                            used += fb
                        }
                    }
                    if (bucket.size >= 2) {
                        used += fa
                        similarGroups += bucket
                    }
                }

                val arr = JSArray()
                exactGroups.forEach { arr.put(groupJson("exact", it)) }
                similarGroups.forEach { arr.put(groupJson("similar", it)) }
                call.resolve(JSObject().apply { put("groups", arr) })
            } catch (e: Exception) {
                call.reject(e.message ?: "duplicate scan failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun scanBySize(call: PluginCall) {
        val minBytes = CallNum.long(call, "minBytes", 1024L * 1024L)
        bridge.execute {
            try {
                val groups = collectImages(2000)
                    .groupBy { it.length() }
                    .filterKeys { it >= minBytes }
                    .entries
                    .sortedByDescending { it.key }
                    .map { (size, list) ->
                        JSObject().apply {
                            put("size", size)
                            put("photos", JSArray(list.map { photoJson(it) }))
                        }
                    }
                call.resolve(JSObject().apply { put("groups", JSArray(groups)) })
            } catch (e: Exception) {
                call.reject(e.message ?: "size scan failed", "E_IO", e)
            }
        }
    }

    private fun collectImages(limit: Int): List<File> {
        val root = android.os.Environment.getExternalStorageDirectory()
        val roots = listOf(File(root, "DCIM"), File(root, "Pictures"))
        val out = mutableListOf<File>()

        fun walk(dir: File, depth: Int) {
            if (out.size >= limit || depth > 8) return
            val children = dir.listFiles() ?: return
            for (f in children) {
                if (out.size >= limit) return
                if (f.isDirectory) walk(f, depth + 1)
                else if (PathUtil.mimeOf(f).startsWith("image/")) out += f
            }
        }

        roots.forEach { walk(it, 0) }
        return out
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 9x8 grayscale dHash, decoded at a low sample rate so a scan of hundreds stays quick. */
    private fun fingerprint(f: File): Long? {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = BitmapFactory.decodeFile(f.absolutePath, opts) ?: return null
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        val px = IntArray(9 * 8)
        small.getPixels(px, 0, 9, 0, 0, 9, 8)
        val gray = IntArray(9 * 8) { i ->
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            (r * 299 + g * 587 + b * 114) / 1000
        }
        return dhash(gray, 9, 8)
    }

    private fun groupJson(kind: String, files: List<File>) = JSObject().apply {
        put("kind", kind)
        put("key", files.first().length().toString() + "-" + files.size)
        put("photos", JSArray(files.map { photoJson(it) }))
    }

    private fun photoJson(f: File) = JSObject().apply {
        put("path", f.absolutePath)
        put("name", f.name)
        put("size", f.length())
        put("mtime", f.lastModified())
        put("mime", PathUtil.mimeOf(f))
    }
}
