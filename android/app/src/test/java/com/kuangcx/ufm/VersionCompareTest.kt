package com.kuangcx.ufm

import com.kuangcx.ufm.core.compareVersionCode
import com.kuangcx.ufm.core.parseVersionCodeFromTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `tag with v prefix parses into a monotonic code`() {
        assertEquals(10200L, parseVersionCodeFromTag("v1.2.0"))
        assertEquals(10000L, parseVersionCodeFromTag("v1.0.0"))
        assertEquals(10101L, parseVersionCodeFromTag("1.1.1"))
        assertEquals(23100L, parseVersionCodeFromTag("v2.31.0"))
    }

    @Test
    fun `malformed tags do not parse`() {
        assertNull(parseVersionCodeFromTag(""))
        assertNull(parseVersionCodeFromTag("release-1"))
        assertNull(parseVersionCodeFromTag("v1.2"))
        assertNull(parseVersionCodeFromTag("v1.2.0-beta"))
        assertNull(parseVersionCodeFromTag("v1.100.0"))
    }

    @Test
    fun `comparison reports older equal and newer`() {
        assertEquals(-1, compareVersionCode(10000, 10200))
        assertEquals(0, compareVersionCode(10200, 10200))
        assertEquals(1, compareVersionCode(10200, 10100))
    }

    @Test
    fun `parsed codes order the way version strings do`() {
        val a = parseVersionCodeFromTag("v1.9.0")!!
        val b = parseVersionCodeFromTag("v1.10.0")!!
        assertEquals(-1, compareVersionCode(a, b))
    }
}
