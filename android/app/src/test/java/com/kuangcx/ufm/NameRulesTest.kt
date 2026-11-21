package com.kuangcx.ufm

import com.kuangcx.ufm.core.NameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameRulesTest {

    private val reservedMessage = "名称不能包含 / \\ : * ? \" < > | 或控制字符"

    @Test
    fun `plain names are accepted`() {
        assertNull(NameRules.validateGroupName("旅行", emptyList()))
        assertNull(NameRules.validateGroupName("Trip 2026", emptyList()))
        assertNull(NameRules.validateGroupName("家人合照", listOf("旅行")))
    }

    @Test
    fun `blank and dotted names are rejected`() {
        assertEquals("名称不能为空", NameRules.validateGroupName("", emptyList()))
        assertEquals("名称不能为空", NameRules.validateGroupName("   ", emptyList()))
        assertEquals("名称不合法", NameRules.validateGroupName(".", emptyList()))
        assertEquals("名称不合法", NameRules.validateGroupName("..", emptyList()))
    }

    @Test
    fun `path separators and reserved characters are rejected`() {
        val bad = listOf(
            "a/b",
            "a\\b",
            "a:b",
            "a*b",
            "a?b",
            "a\"b",
            "a<b",
            "a>b",
            "a|b"
        )
        for (name in bad) {
            assertEquals(reservedMessage, NameRules.validateGroupName(name, emptyList()))
        }
    }

    @Test
    fun `control characters are rejected`() {
        assertEquals(reservedMessage, NameRules.validateGroupName("a\u0001b", emptyList()))
    }

    @Test
    fun `duplicates are rejected case-insensitively`() {
        assertEquals("已存在同名分组", NameRules.validateGroupName("Trip", listOf("trip")))
        assertEquals("已存在同名分组", NameRules.validateGroupName("旅行", listOf("旅行")))
    }

    @Test
    fun `names longer than 64 utf8 bytes are rejected`() {
        val longChinese = "照".repeat(22) // 66 bytes in UTF-8
        assertEquals("名称过长（最多 64 字节）", NameRules.validateGroupName(longChinese, emptyList()))
        assertNull(NameRules.validateGroupName("照".repeat(21), emptyList())) // 63 bytes
    }
}
