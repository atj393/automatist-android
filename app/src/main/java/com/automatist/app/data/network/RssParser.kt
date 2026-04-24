package com.automatist.app.data.network

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class RssItem(
    val title: String,
    val snippet: String
)

/**
 * Per-URL failure that would otherwise be silently swallowed. Carries enough
 * context to tell the engine — and therefore the run log — why a News Feed
 * action came back empty instead of collapsing every failure into a generic
 * "RSS feed returned no content".
 */
data class RssFetchFailure(
    val url: String,
    val kind: Kind,
    val message: String,
    val cause: Throwable?
) {
    enum class Kind {
        /** Network / TLS / DNS / timeout — typically transient on background runs. */
        NETWORK,
        /** HTTP request landed but returned 4xx/5xx. */
        HTTP_STATUS,
        /** Body fetched but the XML parser couldn't read it. */
        PARSE,
        /** Body was empty or contained zero item/entry tags. */
        EMPTY
    }
}

/**
 * Result of a multi-URL fetch. [aggregatedText] keeps the existing String
 * contract for happy-path callers; [failures] captures per-URL problems so
 * a caller that ends up with [aggregatedText] blank can surface WHY in the
 * error detail instead of a vague catch-all message.
 */
data class RssFetchResult(
    val aggregatedText: String,
    val failures: List<RssFetchFailure>,
    val itemsFound: Int
) {
    /** True when every requested URL failed. */
    val allFailed: Boolean get() = failures.isNotEmpty() && itemsFound == 0
}

@Singleton
class RssParser @Inject constructor(
    private val client: OkHttpClient
) {
    /**
     * Backwards-compatible String-returning entry point. Callers who want to
     * diagnose per-URL failures (engine's `executeRssFeed`) should use
     * [fetchAndParseDetailed] instead.
     */
    suspend fun fetchAndParse(urls: List<String>): String =
        fetchAndParseDetailed(urls).aggregatedText

    /**
     * Fetch + parse with full failure visibility.
     *
     * Contract:
     *  - Happy path is unchanged — aggregated text is the same 5-item /
     *    8_000-char string the previous `fetchAndParse` returned.
     *  - Per-URL failures are captured in [RssFetchResult.failures] rather
     *    than swallowed. The caller decides how to surface them.
     *  - A single `IOException` on a URL gets one retry with a short backoff.
     *    Overnight scheduled runs sit on flakier radio/Wi-Fi states; one
     *    retry rescues a meaningful share of transient failures without
     *    turning this into a reliability replacement for WorkManager.
     */
    suspend fun fetchAndParseDetailed(urls: List<String>): RssFetchResult = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<RssItem>()
        val failures = mutableListOf<RssFetchFailure>()

        for (rawUrl in urls) {
            val url = if (!rawUrl.startsWith("http://", ignoreCase = true) &&
                !rawUrl.startsWith("https://", ignoreCase = true)
            ) "https://$rawUrl" else rawUrl

            val before = allItems.size
            val failure = fetchAndParseOne(url, allItems)
            if (failure != null) {
                failures.add(failure)
                continue
            }
            // Parsed successfully but found nothing — distinct from a fetch
            // failure, and worth telling the user about.
            if (allItems.size == before) {
                failures.add(
                    RssFetchFailure(
                        url = url,
                        kind = RssFetchFailure.Kind.EMPTY,
                        message = "Feed fetched successfully but contained no items.",
                        cause = null
                    )
                )
            }
        }

        val aggregatedText = buildString {
            allItems.take(5).forEach { item ->
                append("Title: ${item.title}\n")
                append("Snippet: ${item.snippet}\n\n")
            }
        }.take(8000)

        RssFetchResult(
            aggregatedText = aggregatedText,
            failures = failures,
            itemsFound = allItems.size
        )
    }

    /**
     * Fetch + parse one URL into [accumulator]. Returns null on success,
     * otherwise the captured failure. Applies one retry on IOException.
     */
    private fun fetchAndParseOne(
        url: String,
        accumulator: MutableList<RssItem>
    ): RssFetchFailure? {
        var lastIo: IOException? = null
        for (attempt in 0 until 2) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return RssFetchFailure(
                            url = url,
                            kind = RssFetchFailure.Kind.HTTP_STATUS,
                            message = "HTTP ${response.code} ${response.message.ifBlank { "" }}".trim(),
                            cause = null
                        )
                    }
                    val stream = response.body?.byteStream()
                        ?: return RssFetchFailure(
                            url = url,
                            kind = RssFetchFailure.Kind.NETWORK,
                            message = "Response had no body.",
                            cause = null
                        )
                    try {
                        parseInto(stream, accumulator)
                        return null
                    } catch (e: Exception) {
                        return RssFetchFailure(
                            url = url,
                            kind = RssFetchFailure.Kind.PARSE,
                            message = "Parse error: ${e.message ?: e.javaClass.simpleName}",
                            cause = e
                        )
                    } finally {
                        try { stream.close() } catch (_: Exception) { /* ignore */ }
                    }
                }
            } catch (e: IOException) {
                lastIo = e
                Log.w("RssParser", "Attempt ${attempt + 1} for $url failed: ${e.message}")
                // Short backoff before retry. Noop on the second attempt because
                // the loop exits naturally.
                if (attempt == 0) {
                    try { Thread.sleep(500L) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            } catch (e: Exception) {
                // Non-IO unexpected — don't retry, classify as network for
                // lack of a better bucket.
                return RssFetchFailure(
                    url = url,
                    kind = RssFetchFailure.Kind.NETWORK,
                    message = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}",
                    cause = e
                )
            }
        }
        return RssFetchFailure(
            url = url,
            kind = RssFetchFailure.Kind.NETWORK,
            message = "Network error: ${lastIo?.message ?: lastIo?.javaClass?.simpleName ?: "unknown"}",
            cause = lastIo
        )
    }

    /** XML parse into the provided accumulator. Pulled out so fetch + parse
     *  branches can report their failures distinctly. */
    private fun parseInto(stream: java.io.InputStream, accumulator: MutableList<RssItem>) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        var eventType = parser.eventType
        var insideItem = false
        var title = ""
        var description = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                        insideItem = true
                        title = ""
                        description = ""
                    } else if (insideItem) {
                        if (name.equals("title", ignoreCase = true)) {
                            title = readText(parser)
                        } else if (name.equals("description", ignoreCase = true) || name.equals("summary", ignoreCase = true)) {
                            description = readText(parser)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                        insideItem = false
                        val snippet = cleanHtml(description).take(500).trim()
                        if (title.isNotBlank() || snippet.isNotBlank()) {
                            accumulator.add(RssItem(title.trim(), snippet))
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }
    
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun cleanHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
