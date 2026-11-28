package com.example.ai

import com.example.data.ActionType
import com.example.data.AliBrainResult
import com.example.data.AssistantLanguage
import com.example.data.DeviceAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Free, zero-API-key public knowledge fallback powered by Hindi & English Wikipedia REST API.
 * Used only when Cloud AI (Firebase AI Logic) fails/is unconfigured on the device while internet is ON.
 */
object PublicKnowledgeFallback {

    private const val TIMEOUT_MS = 4_500

    suspend fun tryLookupAnswer(
        rawPrompt: String,
        defaultLanguage: AssistantLanguage,
        ownerName: String = ""
    ): AliBrainResult? = withContext(Dispatchers.IO) {
        val searchTerm = extractSearchTopic(rawPrompt)
        if (searchTerm.length < 2) return@withContext null

        val hasDevanagari = searchTerm.any { it in '\u0900'..'\u097F' }
        val preferHindi = hasDevanagari || defaultLanguage == AssistantLanguage.HINDI

        val primaryLang = if (preferHindi) "hi" else "en"
        val secondaryLang = if (preferHindi) "en" else "hi"

        val answer = queryWikipediaExtract(searchTerm, primaryLang)
            ?: queryWikipediaExtract(searchTerm, secondaryLang)
            ?: return@withContext null

        val bossLabel = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
        val replyPrefix = if (answer.second == "hi" || preferHindi) "जी $bossLabel, " else "Boss, "
        val spokenLang = if (answer.second == "hi") AssistantLanguage.HINDI else AssistantLanguage.ENGLISH

        AliBrainResult(
            replyText = "$replyPrefix${answer.first}",
            spokenLanguage = spokenLang,
            actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
            suggestions = listOf("ताज़ा न्यूज़ सुनाओ", "गाना बजाओ", "समय बताओ")
        )
    }

    internal fun extractSearchTopic(rawPrompt: String): String {
        val cleaned = rawPrompt
            .trim()
            .replace(
                Regex("(?i)^(?:hey|hi|ok|hello|हे|हाय|सुनो|लियोन|लिऑन|leon|ali|अली)[,\\s]+"),
                ""
            )
            .replace(
                Regex("(?i)\\b(?:who\\s+is|who\\s+was|what\\s+is|what\\s+are|where\\s+is|when\\s+was|tell\\s+me\\s+about|explain|define|meaning\\s+of)\\b"),
                " "
            )
            .replace(
                Regex("(?:कौन\\s+हैं|कौन\\s+है|कौन\\s+था|कौन\\s+थे|क्या\\s+हैं|क्या\\s+है|क्या\\s+होता\\s+है|कहाँ\\s+हैं|कहाँ\\s+है|कब\\s+हुआ|किसने\\s+बनवाया|किसने\\s+बनाया|के\\s+बारे\\s+में|बताइए|बताओ|सुनाओ|समझाओ|किसे\\s+कहते\\s+हैं)"),
                " "
            )
            .replace(Regex("[?？।!.,:;\"'()]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(80)
    }

    internal fun parseWikipediaExtractJson(jsonText: String): String? {
        if (jsonText.isBlank()) return null
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        val query = root.optJSONObject("query") ?: return null
        val pages = query.optJSONObject("pages") ?: return null
        val keys = pages.keys()
        while (keys.hasNext()) {
            val pageKey = keys.next()
            val pageObj = pages.optJSONObject(pageKey) ?: continue
            val extract = pageObj.optString("extract", "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (extract.length >= 20) {
                return trimToConciseSummary(extract)
            }
        }
        return null
    }

    private fun trimToConciseSummary(extract: String): String {
        if (extract.length <= 300) return extract
        val cut = extract.take(300)
        val lastPeriod = maxOf(cut.lastIndexOf('।'), cut.lastIndexOf('.'))
        return if (lastPeriod >= 80) cut.substring(0, lastPeriod + 1).trim() else "${cut.trim()}..."
    }

    private fun queryWikipediaExtract(topic: String, wikiLang: String): Pair<String, String>? {
        val encoded = URLEncoder.encode(topic, "UTF-8")
        val endpoint = "https://$wikiLang.wikipedia.org/w/api.php" +
            "?action=query&generator=search&gsrsearch=$encoded&gsrlimit=1" +
            "&prop=extracts&exintro=1&explaintext=1&exsentences=2&utf8=1&format=json"

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "LEON-AI-Assistant/1.0 (Android)")
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val summary = parseWikipediaExtractJson(body) ?: return null
            summary to wikiLang.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
