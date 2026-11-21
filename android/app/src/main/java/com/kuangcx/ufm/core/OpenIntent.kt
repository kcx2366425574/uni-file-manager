package com.kuangcx.ufm.core

import java.net.URLEncoder

/**
 * Parses the intent that launched the task so a file opened from another app (a downloaded
 * APK, a photo, a PDF) lands on a page that can show or install it.
 *
 * Pure and free of android.content.Intent so it unit-tests on the JVM.
 */
object OpenIntent {

    data class Result(val isOpen: Boolean, val route: String?)

    private val OPEN_ACTIONS = setOf(
        "android.intent.action.VIEW",
        "android.intent.action.SEND",
        "android.intent.action.SEND_MULTIPLE"
    )

    fun parse(action: String?, uri: String?, mime: String?): Result {
        if (action == null || action !in OPEN_ACTIONS) return Result(false, null)
        if (uri.isNullOrBlank()) return Result(false, null)
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return Result(
            isOpen = true,
            route = "/pages/open/open?uri=${enc(uri)}&mime=${enc(mime ?: "")}"
        )
    }
}
