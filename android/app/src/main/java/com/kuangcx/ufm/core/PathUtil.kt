package com.kuangcx.ufm.core

import java.io.File

object PathUtil {
    /** Absolute paths only; traversal segments are rejected before touching the filesystem. */
    fun safe(path: String?): File {
        require(!path.isNullOrBlank()) { "path required" }
        require(path.startsWith("/")) { "absolute path required" }
        require(!path.contains("/../") && !path.endsWith("/..")) { "traversal rejected" }
        return File(path).canonicalFile
    }

    fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "pdf" -> "application/pdf"
        "txt", "log", "md" -> "text/plain"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
