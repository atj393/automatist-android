package com.automatist.app.data.offline

import com.automatist.app.di.FileDownloadClient
import com.automatist.app.domain.offline.OfflineModelUrlSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches a small model manifest document over HTTPS.
 *
 * Security posture mirrors the model downloader:
 * - the manifest URL must be plain HTTPS on port 443 with no embedded credentials,
 * - the final (post-redirect) URL is re-checked so a redirect cannot downgrade it,
 * - only a strictly bounded number of bytes is read (a manifest is tiny JSON).
 *
 * This class does no parsing or validation — it returns the raw text for the pure
 * [CustomModelManifestParser]. All failures surface as [IllegalArgumentException].
 */
@Singleton
class ManifestFetcher @Inject constructor(
    @FileDownloadClient private val okHttpClient: OkHttpClient
) {
    suspend fun fetch(manifestUrl: String): String {
        val url = OfflineModelUrlSafety.parseSafe(manifestUrl)
            ?: throw IllegalArgumentException(
                "Manifest URL must be a valid HTTPS URL on the standard secure port, with no embedded credentials."
            )

        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!OfflineModelUrlSafety.isSafe(response.request.url)) {
                    throw IllegalArgumentException("The manifest redirected to a non-HTTPS location.")
                }
                if (!response.isSuccessful) {
                    throw IllegalArgumentException("Could not fetch the manifest (HTTP ${response.code}).")
                }
                val body = response.body
                    ?: throw IllegalArgumentException("The manifest response was empty.")
                body.byteStream().use { stream ->
                    CustomModelManifestParser.readCapped(stream)
                }
            }
        }
    }
}
