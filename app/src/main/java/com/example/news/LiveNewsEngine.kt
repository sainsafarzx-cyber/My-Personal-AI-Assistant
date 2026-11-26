package com.example.news

import android.util.Xml
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class NewsCategory(val wireValue: String, val label: String, val feedUrl: String) {
    TOP(
        wireValue = "top",
        label = "टॉप न्यूज़",
        feedUrl = "https://news.google.com/rss?hl=hi&gl=IN&ceid=IN:hi"
    ),
    TECH(
        wireValue = "tech",
        label = "टेक & AI",
        feedUrl = "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=hi&gl=IN&ceid=IN:hi"
    ),
    SPORTS(
        wireValue = "sports",
        label = "खेल",
        feedUrl = "https://news.google.com/rss/headlines/section/topic/SPORTS?hl=hi&gl=IN&ceid=IN:hi"
    ),
    WORLD(
        wireValue = "world",
        label = "World",
        feedUrl = "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"
    );

    companion object {
        fun fromTopicHint(hint: String): NewsCategory {
            val lower = hint.lowercase()
            return when {
                lower.contains("tech") || lower.contains("टेक") || lower.contains("ai") || lower.contains("mobile") -> TECH
                lower.contains("sport") || lower.contains("cricket") || lower.contains("खेल") || lower.contains("क्रिकेट") -> SPORTS
                lower.contains("world") || lower.contains("global") || lower.contains("english") || lower.contains("दुनिया") -> WORLD
                else -> TOP
            }
        }
    }
}

@Immutable
data class NewsArticle(
    val title: String,
    val source: String,
    val link: String,
    val publishedAt: String
)

/**
 * 100% Free, Zero-API-Key Live News reader powered by Google News RSS + Android XmlPullParser.
 */
object LiveNewsEngine {

    private const val TIMEOUT_MS = 8_000

    suspend fun fetchNews(
        category: NewsCategory = NewsCategory.TOP,
        query: String = ""
    ): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val targetUrl = if (cleanQuery.isNotBlank()) {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            "https://news.google.com/rss/search?q=$encoded&hl=hi&gl=IN&ceid=IN:hi"
        } else {
            category.feedUrl
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "LEON-AI-NewsReader/1.0")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("News server returned HTTP $code"))
            }
            val articles = conn.inputStream.use { stream ->
                parseRssXml(stream)
            }
            if (articles.isEmpty()) {
                Result.failure(IllegalStateException("No news headlines available right now."))
            } else {
                Result.success(articles.take(20))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseRssXml(inputStream: InputStream): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        var insideItem = false
        var title = ""
        var link = ""
        var pubDate = ""
        var source = ""
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.orEmpty().lowercase()
                    if (currentTag == "item") {
                        insideItem = true
                        title = ""
                        link = ""
                        pubDate = ""
                        source = ""
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideItem) {
                        val text = parser.text.orEmpty()
                        when (currentTag) {
                            "title" -> title += text
                            "link" -> link += text
                            "pubdate" -> pubDate += text
                            "source" -> source += text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val endTag = parser.name.orEmpty().lowercase()
                    if (endTag == "item" && insideItem) {
                        insideItem = false
                        val cleanedTitle = cleanText(title)
                        val splitSource = extractTrailingSource(cleanedTitle)
                        val finalSource = cleanText(source).ifBlank { splitSource.second }.ifBlank { "News" }
                        val finalTitle = if (splitSource.second.isNotBlank()) splitSource.first else cleanedTitle
                        if (finalTitle.isNotBlank()) {
                            articles.add(
                                NewsArticle(
                                    title = finalTitle,
                                    source = finalSource,
                                    link = link.trim(),
                                    publishedAt = formatPublishedLabel(pubDate)
                                )
                            )
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    fun buildSpokenDigest(
        articles: List<NewsArticle>,
        category: NewsCategory,
        ownerName: String = ""
    ): String {
        val boss = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
        if (articles.isEmpty()) {
            return "जी $boss, अभी ताज़ा खबरें लोड नहीं हो सकीं। कृपया इंटरनेट चेक करें।"
        }
        val top = articles.take(3)
        val headlinesText = top.mapIndexed { idx, article ->
            "खबर ${idx + 1}: ${article.title}"
        }.joinToString("। ")
        return "जी $boss, ${category.label} की ताज़ा मुख्य खबरें सुनिए। $headlinesText।"
    }

    private fun cleanText(raw: String): String {
        return raw
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractTrailingSource(title: String): Pair<String, String> {
        val idx = title.lastIndexOf(" - ")
        return if (idx > 12 && idx < title.length - 3) {
            title.substring(0, idx).trim() to title.substring(idx + 3).trim()
        } else {
            title to ""
        }
    }

    private fun formatPublishedLabel(pubDate: String): String {
        val trimmed = pubDate.trim()
        if (trimmed.isBlank()) return "Today"
        // Typical RSS pubDate: "Fri, 20 Nov 2026 05:30:00 GMT" -> "20 Nov • 05:30"
        val parts = trimmed.split(" ")
        return if (parts.size >= 5) {
            "${parts[1]} ${parts[2]} • ${parts[4].take(5)}"
        } else {
            trimmed.take(22)
        }
    }
}
