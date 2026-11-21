package com.kuangcx.ufm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Disk-backed thumbnail cache. Files are keyed by path + size bucket so a resized request
 * never returns a stale dimension. An in-memory LRU sits in front to avoid re-decoding
 * while scrolling.
 *
 * Returns a JPEG on disk rather than a Bitmap because the WebView loads it through
 * Capacitor's local file server; passing bitmaps through the bridge would blow up memory
 * on a gallery of thousands.
 */
class ThumbCache(private val cacheDir: File) {

    private val mem = object : LruCache<String, Bitmap>(MEM_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
    }

    fun get(path: String, maxPx: Int): File? {
        val src = File(path)
        if (!src.isFile) return null
        val out = File(cacheDir, key(path, maxPx) + ".jpg")
        if (out.isFile && out.lastModified() >= src.lastModified() && out.length() > 0) return out

        val bmp = decode(src, maxPx) ?: return null
        mem.put(key(path, maxPx), bmp)
        return try {
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it) }
            out.setLastModified(src.lastModified())
            out
        } catch (e: Exception) {
            out.delete()
            null
        }
    }

    private fun decode(src: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxPx * 2 && bounds.outHeight / sample > maxPx * 2) sample *= 2
        return if (Build.VERSION.SDK_INT >= 29) {
            ThumbnailUtils.createImageThumbnail(src, android.util.Size(maxPx, maxPx), null)
        } else {
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(src.absolutePath, opts)
        }
    }

    /** Evicts least-recently-modified entries once the cache exceeds its byte budget. */
    fun trimTo(maxBytes: Long) {
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= maxBytes) break
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    private fun key(path: String, maxPx: Int): String {
        val md = MessageDigest.getInstance("SHA-1")
        val h = md.digest("$path|$maxPx".toByteArray()).joinToString("") { "%02x".format(it) }
        return h.substring(0, 32)
    }

    companion object {
        const val MEM_BYTES = 32 * 1024 * 1024
        const val THUMB_QUALITY = 82
        const val DISK_BUDGET = 500L * 1024 * 1024
    }
}
