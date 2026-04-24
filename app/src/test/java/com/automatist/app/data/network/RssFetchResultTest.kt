package com.automatist.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [RssFetchResult] / [RssFetchFailure] — the typed aggregation that
 * replaces the prior "catch-all-and-swallow" flow in [RssParser]. The real
 * network path can't be unit-tested in isolation without rewiring OkHttp, but
 * this fences the contract that the engine relies on: as long as per-URL
 * failures are captured with their classification + message, the workflow
 * run log can surface the real reason a News Feed action came back empty
 * instead of collapsing everything into "RSS feed returned no content".
 */
class RssFetchResultTest {

    @Test
    fun `allFailed is false when at least one item was fetched`() {
        val r = RssFetchResult(
            aggregatedText = "Title: x\n",
            failures = listOf(
                RssFetchFailure(
                    url = "https://broken.example",
                    kind = RssFetchFailure.Kind.NETWORK,
                    message = "connect timeout",
                    cause = null
                )
            ),
            itemsFound = 1
        )
        assertFalse(
            "a partial success must not look like a total failure — " +
                "this is the difference between 'some feeds unreachable' and 'News Feed completely down'",
            r.allFailed
        )
    }

    @Test
    fun `allFailed is true when every URL failed`() {
        val r = RssFetchResult(
            aggregatedText = "",
            failures = listOf(
                RssFetchFailure(
                    url = "https://broken.example",
                    kind = RssFetchFailure.Kind.NETWORK,
                    message = "connect timeout",
                    cause = null
                )
            ),
            itemsFound = 0
        )
        assertTrue(r.allFailed)
    }

    @Test
    fun `allFailed is false when there are zero urls and zero failures`() {
        // Defensive: the caller shouldn't ask for zero URLs, but if it does
        // the empty result is NOT a "failure" — it's just nothing to do.
        val r = RssFetchResult(aggregatedText = "", failures = emptyList(), itemsFound = 0)
        assertFalse(r.allFailed)
    }

    @Test
    fun `failure kinds cover the four classes the engine branches on`() {
        // Fence: the engine's executeRssFeed maps each kind to a distinct
        // user-facing label. If a new kind is added here but not handled
        // there, the compiler won't complain (the kind is a String-ish enum
        // in the UI layer), so this test documents the expected set.
        val expected = setOf(
            RssFetchFailure.Kind.NETWORK,
            RssFetchFailure.Kind.HTTP_STATUS,
            RssFetchFailure.Kind.PARSE,
            RssFetchFailure.Kind.EMPTY
        )
        assertEquals(expected, RssFetchFailure.Kind.entries.toSet())
    }

    @Test
    fun `failure captures the underlying cause for rawDetail reporting`() {
        val cause = RuntimeException("the underlying wire-level error")
        val failure = RssFetchFailure(
            url = "https://x.example",
            kind = RssFetchFailure.Kind.NETWORK,
            message = "Network error: ${cause.message}",
            cause = cause
        )
        // The engine's error builder appends `Exception: X: Y` only when
        // cause is non-null — this guards that the cause survives end-to-end.
        assertTrue(failure.cause === cause)
    }

    @Test
    fun `aggregatedText preserves fetched items when only some URLs failed`() {
        // Mixed-result shape the multi-feed path produces. The text for the
        // good feed must still be the 'Title: …' format the engine and the
        // downstream keyword filter expect, regardless of partial failures.
        val r = RssFetchResult(
            aggregatedText = "Title: Alpha\nSnippet: one\n\nTitle: Beta\nSnippet: two\n\n",
            failures = listOf(
                RssFetchFailure(
                    url = "https://broken.example",
                    kind = RssFetchFailure.Kind.HTTP_STATUS,
                    message = "HTTP 503 Service Unavailable",
                    cause = null
                )
            ),
            itemsFound = 2
        )
        assertTrue(r.aggregatedText.contains("Title: Alpha"))
        assertTrue(r.aggregatedText.contains("Title: Beta"))
        assertEquals(1, r.failures.size)
        assertFalse(r.allFailed)
    }

    // ── Kind-specific assertions ──

    @Test
    fun `NETWORK kind carries an IOException-shaped message`() {
        val failure = RssFetchFailure(
            url = "https://x.example",
            kind = RssFetchFailure.Kind.NETWORK,
            message = "Network error: Unable to resolve host",
            cause = null
        )
        assertTrue(failure.message.contains("Network", ignoreCase = true))
    }

    @Test
    fun `HTTP_STATUS kind carries the HTTP code`() {
        val failure = RssFetchFailure(
            url = "https://x.example",
            kind = RssFetchFailure.Kind.HTTP_STATUS,
            message = "HTTP 403 Forbidden",
            cause = null
        )
        assertTrue(failure.message.contains("403"))
    }

    @Test
    fun `EMPTY kind captures the 'fetched but zero items' condition`() {
        val failure = RssFetchFailure(
            url = "https://x.example",
            kind = RssFetchFailure.Kind.EMPTY,
            message = "Feed fetched successfully but contained no items.",
            cause = null
        )
        assertTrue(failure.message.contains("no items", ignoreCase = true))
    }

    @Test
    fun `PARSE kind carries the underlying parser message`() {
        val cause = RuntimeException("unexpected end of stream at line 42")
        val failure = RssFetchFailure(
            url = "https://x.example",
            kind = RssFetchFailure.Kind.PARSE,
            message = "Parse error: ${cause.message}",
            cause = cause
        )
        assertTrue(failure.message.contains("Parse error"))
        assertTrue(failure.message.contains("line 42"))
    }
}
