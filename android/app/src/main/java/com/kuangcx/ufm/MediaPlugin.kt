package com.kuangcx.ufm

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.MediaKind
import com.kuangcx.ufm.core.MediaQuery
import java.io.File

@CapacitorPlugin(name = "Media")
class MediaPlugin : Plugin() {

    private val thumbs by lazy { ThumbCache(File(context.cacheDir, "thumbs")) }

    @PluginMethod
    fun query(call: PluginCall) {
        val kind = when (call.getString("kind") ?: "image") {
            "video" -> MediaKind.VIDEO
            "audio" -> MediaKind.AUDIO
            "doc" -> MediaKind.DOC
            else -> MediaKind.IMAGE
        }
        val page = (call.getInt("page") ?: 0).coerceAtLeast(0)
        val pageSize = (call.getInt("pageSize") ?: 200).coerceIn(1, 1000)
        val sinceMs = call.getLong("sinceMs")
        bridge.execute {
            try {
                // Images/Video/Audio are typed collections and reject media_type; only Files needs it.
                val collection = if (kind == MediaKind.DOC) MediaQuery.Collection.FILES else MediaQuery.Collection.TYPED
                val spec = MediaQuery.build(kind, sinceMs, pageSize + 1, page * pageSize, collection)
                val uri = when (kind) {
                    MediaKind.IMAGE -> if (Build.VERSION.SDK_INT >= 29) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    MediaKind.VIDEO -> if (Build.VERSION.SDK_INT >= 29) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    else -> MediaStore.Files.getContentUri("external")
                }
                val projections = arrayOf(
                    MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH
                )
                val items = mutableListOf<JSObject>()
                var hasMore = false
                var fetched = 0
                context.contentResolver
                    .query(uri, projections, spec.selection, spec.selectionArgs, spec.sortOrder)
                    ?.use { c ->
                        val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val dataIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                        val modIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                        val mimeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                        val bucketIdx = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                        val relIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        var seen = 0
                        // MediaStore has no portable OFFSET argument, so offsets are walked here.
                        while (c.moveToNext()) {
                            if (seen++ < spec.offset) continue
                            if (fetched >= pageSize) { hasMore = true; break }
                            fetched++
                            val name = c.getString(nameIdx) ?: "unnamed"
                            // DATA is deprecated from API 29 and can be null for rows inserted
                            // through MediaStore; RELATIVE_PATH + name is the supported fallback.
                            val data = c.getString(dataIdx)
                            val path = data ?: run {
                                val rel = if (relIdx >= 0) c.getString(relIdx) else null
                                if (rel.isNullOrBlank()) null
                                else android.os.Environment.getExternalStorageDirectory().absolutePath +
                                    "/" + rel.trimEnd('/') + "/" + name
                            } ?: continue
                            items += JSObject().apply {
                                put("mediaId", c.getLong(idIdx))
                                put("name", name)
                                put("path", path)
                                put("size", c.getLong(sizeIdx))
                                put("mtime", c.getLong(modIdx) * 1000)
                                put("mime", c.getString(mimeIdx) ?: "")
                                put("bucket", if (bucketIdx >= 0) c.getString(bucketIdx) else "")
                            }
                        }
                    }
                call.resolve(JSObject().apply {
                    put("items", JSArray(items))
                    put("hasMore", hasMore)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "media query failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun thumbnail(call: PluginCall) {
        val path = call.getString("path")
        val maxPx = (call.getInt("maxPx") ?: 256).coerceIn(64, 1024)
        bridge.execute {
            try {
                if (path.isNullOrBlank()) {
                    call.reject("path required", "E_NOENT"); return@execute
                }
                val f = thumbs.get(path, maxPx)
                if (f == null) {
                    call.reject("thumbnail failed", "E_IO"); return@execute
                }
                call.resolve(JSObject().apply { put("url", f.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "thumbnail failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun trimThumbs(call: PluginCall) {
        bridge.execute {
            thumbs.trimTo(ThumbCache.DISK_BUDGET)
            call.resolve()
        }
    }
}
