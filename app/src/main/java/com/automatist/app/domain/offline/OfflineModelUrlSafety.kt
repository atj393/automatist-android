package com.automatist.app.domain.offline

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pure, side-effect-free safety checks for offline-model download URLs.
 *
 * A model download must be an ordinary, unauthenticated HTTPS request on the
 * standard secure port. This rules out:
 * - plaintext HTTP (no transport integrity/confidentiality),
 * - credentials embedded in the URL (`https://user:pass@host/...`),
 * - non-standard ports (a cheap signal of an unusual or hostile endpoint).
 *
 * The same predicate is applied both when validating user-entered sources and at
 * download time against the *final* (post-redirect) URL, so a redirect can never
 * silently downgrade the request.
 */
object OfflineModelUrlSafety {

    /** True if [url] is a plain HTTPS download on port 443 with no embedded credentials. */
    fun isSafe(url: HttpUrl): Boolean =
        url.isHttps &&
            url.port == 443 &&
            url.username.isEmpty() &&
            url.password.isEmpty()

    /** Parse [value] as a safe HTTPS URL, or return null if it is malformed or unsafe. */
    fun parseSafe(value: String): HttpUrl? =
        value.trim().toHttpUrlOrNull()?.takeIf(::isSafe)

    /**
     * Upper bound (in bytes) we are willing to download for an entry whose declared size
     * is [declaredBytes]. Declared sizes are estimates — especially for user-provided
     * sources — so a modest tolerance absorbs CDN/metadata variance while a wildly larger
     * payload is rejected mid-stream. A non-positive declared size means "unknown", which
     * disables the cap (the SHA-256 check remains the integrity backstop).
     */
    fun maxAllowedBytes(declaredBytes: Long): Long =
        if (declaredBytes > 0) (declaredBytes * SIZE_TOLERANCE).toLong() else Long.MAX_VALUE

    /** Tolerance multiplier applied to a declared download size before rejecting an oversized payload. */
    const val SIZE_TOLERANCE = 1.25
}
