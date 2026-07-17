package com.kimi.proxy.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [JwtUtils]. Uses real (anonymised) JWT shapes mirroring
 * the kimi-proxy-web Playwright capture script requirements.
 */
class JwtUtilsTest {

    private val fakePayload = """{"sub":"user-123","ssid":"sess-456","email":"t@example.com","exp":1893456000,"region":"overseas"}"""
    private val fakePayloadB64 = base64Url(fakePayload)
    private val fakeJwt = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.$fakePayloadB64.signature"

    @Test
    fun `decodes well-formed JWT with sub and ssid`() {
        val c = JwtUtils.decode(fakeJwt)
        assertNotNull(c)
        assertEquals("user-123", c!!.sub)
        assertEquals("sess-456", c.ssid)
        assertEquals("t@example.com", c.email)
        assertEquals(1893456000L, c.exp)
        assertEquals("overseas", c.region)
    }

    @Test
    fun `isUsable returns true when sub and ssid present`() {
        assertTrue(JwtUtils.isUsable(fakeJwt))
    }

    @Test
    fun `isUsable returns false for non-jwt string`() {
        assertFalse(JwtUtils.isUsable("not-a-jwt"))
    }

    @Test
    fun `decode returns null for malformed token`() {
        assertNull(JwtUtils.decode("a.b"))
        assertNull(JwtUtils.decode("a.b.c.d"))
    }

    @Test
    fun `decode strips Bearer prefix`() {
        val c = JwtUtils.decode("Bearer $fakeJwt")
        assertNotNull(c)
        assertEquals("user-123", c!!.sub)
    }

    private fun base64Url(input: String): String {
        val raw = java.util.Base64.getEncoder().encodeToString(input.toByteArray())
        return raw.replace('+', '-').replace('/', '_').trimEnd('=')
    }
}
