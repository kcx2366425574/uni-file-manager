package com.kuangcx.ufm.core

import java.nio.charset.StandardCharsets

object NameRules {

    private val RESERVED = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    private const val MAX_BYTES = 64

    /** Returns null when the name is usable, otherwise a Chinese reason to show the user. */
    fun validateGroupName(raw: String, existing: List<String>): String? {
        if (raw.isBlank()) return "名称不能为空"
        if (raw == "." || raw == "..") return "名称不合法"
        if (raw.any { it in RESERVED || it.isISOControl() }) {
            return "名称不能包含 / \\ : * ? \" < > | 或控制字符"
        }
        if (raw.toByteArray(StandardCharsets.UTF_8).size > MAX_BYTES) return "名称过长（最多 64 字节）"
        if (existing.any { it.equals(raw, ignoreCase = true) }) return "已存在同名分组"
        return null
    }

    /** Dedupes "旅行 (2)", "旅行 (3)" ... when a file would land on an existing name. */
    fun uniqueFileName(base: String, usedNames: Set<String>): String {
        if (usedNames.none { it.equals(base, ignoreCase = true) }) return base
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var n = 2
        while (true) {
            val candidate = "$stem ($n)$ext"
            if (usedNames.none { it.equals(candidate, ignoreCase = true) }) return candidate
            n++
        }
    }
}
