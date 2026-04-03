package com.synapse.app.data.network

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject
import javax.inject.Singleton

data class RssItem(
    val title: String,
    val snippet: String
)

@Singleton
class RssParser @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun fetchAndParse(urls: List<String>): String = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<RssItem>()
        
        for (url in urls) {
            try {
                val formattedUrl = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                    "https://$url"
                } else url
                val request = Request.Builder().url(formattedUrl).build()
                val response = client.newCall(request).execute()
                val inputStream = response.body?.byteStream() ?: continue
                
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(inputStream, null)
                
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
                                val snippet = cleanHtml(description)
                                    .take(500) // Truncate snippet
                                    .trim()
                                
                                if (title.isNotBlank() || snippet.isNotBlank()) {
                                    allItems.add(RssItem(title.trim(), snippet))
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
                inputStream.close()
            } catch (e: Exception) {
                // Skip failed URLs gracefully
                e.printStackTrace()
            }
        }

        // Limit to top 5 recent items overall
        val aggregatedText = buildString {
            allItems.take(5).forEach { item ->
                append("Title: ${item.title}\n")
                append("Snippet: ${item.snippet}\n\n")
            }
        }
        
        // Strict boundary to prevent token overflow
        aggregatedText.take(8000)
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
