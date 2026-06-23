package com.automatist.app.domain.offline

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelUrlSafetyTest {

    @Test
    fun acceptsPlainHttpsOn443() {
        assertTrue(OfflineModelUrlSafety.isSafe("https://huggingface.co/repo/resolve/main/m.task".toHttpUrl()))
    }

    @Test
    fun rejectsPlaintextHttp() {
        assertFalse(OfflineModelUrlSafety.isSafe("http://example.com/m.task".toHttpUrl()))
    }

    @Test
    fun rejectsEmbeddedCredentials() {
        assertFalse(OfflineModelUrlSafety.isSafe("https://user:pass@example.com/m.task".toHttpUrl()))
    }

    @Test
    fun rejectsNonStandardPort() {
        assertFalse(OfflineModelUrlSafety.isSafe("https://example.com:8443/m.task".toHttpUrl()))
    }

    @Test
    fun parseSafeReturnsUrlForSafeInput() {
        assertNotNull(OfflineModelUrlSafety.parseSafe("  https://example.com/m.task  "))
    }

    @Test
    fun parseSafeReturnsNullForUnsafeOrMalformed() {
        assertNull(OfflineModelUrlSafety.parseSafe("http://example.com/m.task"))
        assertNull(OfflineModelUrlSafety.parseSafe("https://user:pw@example.com/m.task"))
        assertNull(OfflineModelUrlSafety.parseSafe("not a url"))
        assertNull(OfflineModelUrlSafety.parseSafe("ftp://example.com/m.task"))
    }

    @Test
    fun maxAllowedBytesAppliesToleranceWhenDeclared() {
        assertEquals(125L, OfflineModelUrlSafety.maxAllowedBytes(100L))
    }

    @Test
    fun maxAllowedBytesIsUnboundedWhenUnknown() {
        assertEquals(Long.MAX_VALUE, OfflineModelUrlSafety.maxAllowedBytes(0L))
        assertEquals(Long.MAX_VALUE, OfflineModelUrlSafety.maxAllowedBytes(-1L))
    }
}
