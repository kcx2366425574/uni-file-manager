package com.kuangcx.ufm.core

/**
 * Encodes MAJOR.MINOR.PATCH into one monotonically increasing Long so ordering matches the
 * version strings. MINOR and PATCH are capped at two digits, which is plenty for this app and
 * keeps the arithmetic obvious.
 */
fun parseVersionCodeFromTag(tag: String): Long? {
    val v = tag.trim().removePrefix("v")
    val parts = v.split(".")
    if (parts.size != 3) return null
    val nums = parts.map { it.toIntOrNull() ?: return null }
    if (nums.any { it < 0 || it > 99 }) return null
    return nums[0] * 10_000L + nums[1] * 100L + nums[2]
}

fun compareVersionCode(local: Long, remote: Long): Int = local.compareTo(remote)

/**
 * Startup update checks are throttled: the GitHub releases API allows 60 anonymous requests per
 * hour, and a check on every launch would burn that. A backwards clock jump is treated as "due"
 * so a wrong device clock can never lock the user out permanently.
 */
fun shouldCheck(now: Long, last: Long, intervalMs: Long = 24L * 60 * 60 * 1000): Boolean {
    if (last <= 0) return true
    if (now < last) return true
    return now - last >= intervalMs
}
