package com.example.ai

import com.example.data.ActionType
import com.example.data.AliBrainResult
import com.example.data.AssistantLanguage
import com.example.data.CapturedNotification
import com.example.data.DailyTask
import com.example.data.DeviceAction
import com.example.data.DeviceTelemetry
import com.example.data.MemoryCategory
import com.example.data.MemoryItem
import com.example.social.SocialMediaEngine
import com.example.social.SocialPlatform
import com.example.social.SocialPostType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Deterministic instant local command router for LEON AI.
 *
 * Ensures core phone controls (flashlight, Wi-Fi/settings, brightness, volume, music,
 * app launches, timers, battery/time queries, permanent one-tell memory, creator identity,
 * and lock-screen briefings) execute in <10ms with Hindi Devanagari + Hinglish + English support.
 */
object LocalCommandRouter {

    private val compoundSplitter = Regex("\\s+(?:aur|and|और)\\s+", RegexOption.IGNORE_CASE)

    fun tryFastPath(
        rawPrompt: String,
        defaultLanguage: AssistantLanguage,
        telemetry: DeviceTelemetry,
        notifications: List<CapturedNotification>,
        tasks: List<DailyTask>,
        isDeviceLocked: Boolean = false,
        ownerName: String = "",
        memories: List<MemoryItem> = emptyList()
    ): AliBrainResult? {
        val clean = rawPrompt.trim()
        if (clean.isEmpty()) return null
        val lower = clean.lowercase(Locale.ROOT).trimEnd('.', '!', '?', '।')

        // Priority: Owner/Creator Identity & One-Tell Forever Memory should be evaluated
        // on the full prompt BEFORE compound splitting so "मेरा नाम X है और कोई ऐप डाउनलोड करे..." stays intact!
        routeForeverIdentityAndMemory(clean, lower, ownerName, memories)?.let { return it }

        // Priority: Social Auto-Poster supports multi-app phrases containing "और/and" (e.g. "इंस्टाग्राम और व्हाट्सएप पर स्टोरी डालो")
        routeSocialMediaClause(clean, lower, isDeviceLocked, ownerName)?.let { return it }

        // Priority: Human-like Right-Path Mentor / Life Compass requests
        routeLifeCompassClause(clean, lower, isDeviceLocked, ownerName)?.let { return it }

        // Priority: AI Incoming Call Receptionist ("जब कोई कॉल आए और मैं रिसीव न कर पाऊँ तो AI बात करे")
        routeCallAssistantClause(clean, lower, isDeviceLocked, ownerName)?.let { return it }

        if (compoundSplitter.containsMatchIn(clean)) {
            val parts = clean.split(compoundSplitter).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size in 2..3) {
                val subResults = parts.mapNotNull { part ->
                    routeSingleClause(
                        clean = part,
                        lower = part.lowercase(Locale.ROOT),
                        lang = defaultLanguage,
                        telemetry = telemetry,
                        notifications = notifications,
                        tasks = tasks,
                        isDeviceLocked = isDeviceLocked,
                        ownerName = ownerName
                    )
                }
                if (subResults.size == parts.size) {
                    val combinedActions = subResults.flatMap { it.actions }.filter { it.type != ActionType.SPEAK_ONLY }
                        .ifEmpty { listOf(DeviceAction(ActionType.SPEAK_ONLY)) }
                    val combinedReply = subResults.joinToString(" ") { it.replyText }
                    return AliBrainResult(
                        replyText = combinedReply,
                        spokenLanguage = defaultLanguage,
                        actions = combinedActions,
                        suggestions = listOf("गाना बजाओ", "Flashlight off करो", "Wi-Fi खोलो")
                    )
                }
            }
        }

        return routeSingleClause(clean, lower, defaultLanguage, telemetry, notifications, tasks, isDeviceLocked, ownerName)
    }

    fun tryFallback(
        rawPrompt: String,
        defaultLanguage: AssistantLanguage,
        telemetry: DeviceTelemetry,
        notifications: List<CapturedNotification>,
        tasks: List<DailyTask>,
        isDeviceLocked: Boolean = false,
        ownerName: String = "",
        memories: List<MemoryItem> = emptyList()
    ): AliBrainResult? {
        tryFastPath(
            rawPrompt = rawPrompt,
            defaultLanguage = defaultLanguage,
            telemetry = telemetry,
            notifications = notifications,
            tasks = tasks,
            isDeviceLocked = isDeviceLocked,
            ownerName = ownerName,
            memories = memories
        )?.let { return it }

        val clean = rawPrompt.trim()
        val lower = clean.lowercase(Locale.ROOT)

        // Check if prompt matches a saved permanent memory topic offline!
        if (memories.isNotEmpty() && (lower.contains("क्या") || lower.contains("what") || lower.contains("कौन") || lower.contains("कहाँ") || lower.contains("बता"))) {
            val promptTokens = lower.split(Regex("\\s+")).filter { it.length >= 3 }
            val matched = memories.firstOrNull { mem ->
                val haystack = "${mem.memoryKey} ${mem.memoryValue}".lowercase(Locale.ROOT)
                promptTokens.count { haystack.contains(it) } >= 2
            }
            if (matched != null) {
                val bossPrefix = if (ownerName.isNotBlank()) "जी $ownerName बॉस" else "जी बॉस"
                return AliBrainResult(
                    replyText = "$bossPrefix, आपकी सेव की गई परमानेंट मेमोरी के अनुसार — ${matched.memoryKey}: ${matched.memoryValue}",
                    spokenLanguage = defaultLanguage,
                    actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                    suggestions = listOf("मेरी यादें बताओ", "मेरा नाम क्या है")
                )
            }
        }

        if ((lower.contains("youtube") || lower.contains("यूट्यूब")) &&
            (lower.contains("search") || lower.contains("play") || lower.contains("चलाओ") || lower.contains("सर्च") || lower.contains("देखना"))
        ) {
            val query = clean.replace(Regex("(?i)youtube|यूट्यूब|search|play|on|पर|चलाओ|सर्च|करो|in"), "").trim()
                .ifBlank { clean }
            return AliBrainResult(
                replyText = "जी बॉस, YouTube पर \"$query\" खोल रहा हूँ।",
                spokenLanguage = defaultLanguage,
                actions = listOf(DeviceAction(ActionType.YOUTUBE_SEARCH, target = query)),
                suggestions = listOf("गाना बजाओ", "Volume बढ़ाओ", "बैटरी बताओ")
            )
        }

        if (lower.startsWith("search ") || lower.contains("google पर") || lower.contains("गूगल पर")) {
            val query = clean.replace(Regex("(?i)^search\\s+|google\\s+पर|गूगल\\s+पर|search|सर्च|करो"), "").trim()
                .ifBlank { clean }
            return AliBrainResult(
                replyText = "जी बॉस, Google पर \"$query\" खोज रहा हूँ।",
                spokenLanguage = defaultLanguage,
                actions = listOf(DeviceAction(ActionType.GOOGLE_SEARCH, target = query)),
                suggestions = listOf("YouTube खोलो", "गाना बजाओ")
            )
        }

        if (lower.contains("add task") || lower.contains("remind me") || lower.contains("टास्क जोड़ो") || lower.contains("रिमाइंडर")) {
            val title = clean.replace(Regex("(?i)add task|remind me to|remind me|टास्क जोड़ो|रिमाइंडर जोड़ो|रिमाइंडर लगाओ"), "").trim()
                .ifBlank { clean }
            return AliBrainResult(
                replyText = "जी बॉस, मैंने टास्क सेव कर लिया है: $title",
                spokenLanguage = defaultLanguage,
                actions = listOf(DeviceAction(ActionType.CREATE_TASK, target = title, payload = "Today")),
                suggestions = listOf("मेरी आज की tasks बताओ", "गाना बजाओ")
            )
        }

        val boss = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
        return when {
            lower.contains("thank") || lower.contains("शुक्रिया") || lower.contains("धन्यवाद") || lower.contains("shukriya") -> {
                AliBrainResult(
                    replyText = "आपका स्वागत है $boss! मैं हमेशा आपकी सेवा के लिए तैयार हूँ।",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                    suggestions = listOf("ताज़ा न्यूज़ सुनाओ", "गाना बजाओ")
                )
            }
            lower.contains("good morning") || lower.contains("सुप्रभात") || lower.contains("good night") || lower.contains("शुभ रात्रि") -> {
                val greet = if (lower.contains("night") || lower.contains("रात्रि")) {
                    "शुभ रात्रि $boss! मीठे सपने, अलार्म या स्लीप म्यूजिक लगाना हो तो बताइए।"
                } else {
                    "सुप्रभात $boss! आज का दिन शानदार रहे — क्या मैं आज की ताज़ा न्यूज़ सुनाऊँ?"
                }
                AliBrainResult(
                    replyText = greet,
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                    suggestions = listOf("ताज़ा न्यूज़ सुनाओ", "बैटरी बताओ")
                )
            }
            lower.contains("joke") || lower.contains("चुटकुला") || lower.contains("हंसाओ") -> {
                AliBrainResult(
                    replyText = "सुनिए $boss: मास्टर जी ने पूछा — 'बिजली कहाँ से आती है?' पप्पू बोला — 'सर, मामा के घर से! जब भी जाती है, पापा कहते हैं साले फिर काट गए!' हा हा!",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                    suggestions = listOf("ताज़ा न्यूज़ सुनाओ", "गाना बजाओ")
                )
            }
            else -> HumanWisdomEngine.tryBuildOfflineHumanGuidanceResult(
                rawPrompt = clean,
                defaultLanguage = defaultLanguage,
                ownerName = ownerName,
                tasks = tasks,
                memories = memories
            )
        }
    }

    private fun routeForeverIdentityAndMemory(
        clean: String,
        lower: String,
        ownerName: String,
        memories: List<MemoryItem>
    ): AliBrainResult? {
        // 1. Queries asking "who am I / who created this app / whose name will you tell on download"
        val asksOwnerOrCreator = lower.contains("मेरा नाम क्या") ||
            lower.contains("मेरा नाम बता") ||
            lower.contains("what is my name") ||
            lower.contains("what's my name") ||
            lower.contains("who am i") ||
            lower.contains("तुमको किसने बनाया") ||
            lower.contains("तुम्हें किसने बनाया") ||
            lower.contains("आपको किसने बनाया") ||
            lower.contains("किसने बनाया") ||
            lower.contains("किसका ऐप") ||
            lower.contains("owner कौन") ||
            lower.contains("who made you") ||
            lower.contains("who created you") ||
            lower.contains("who is your owner") ||
            lower.contains("who is your creator") ||
            lower.contains("who is your boss") ||
            lower.contains("किसका नाम बताओगे") ||
            lower.contains("क्या नाम बताओगे")

        if (asksOwnerOrCreator) {
            val reply = if (ownerName.isNotBlank()) {
                "मैं LEON AI (powered by ALI AI) हूँ — मेरे मालिक और क्रिएटर $ownerName बॉस हैं! कोई भी इस ऐप को डाउनलोड या इस्तेमाल करेगा, तो मैं उसे गर्व से $ownerName जी का ही नाम बताऊँगा।"
            } else {
                "बॉस, अभी आपका नाम सेव नहीं है। बस एक बार बताइए — 'मेरा नाम [आपका नाम] है' — मैं इसे हमेशा के लिए याद रखूँगा और हर नए डाउनलोड पर आपका ही नाम बताऊँगा!"
            }
            return AliBrainResult(
                replyText = reply,
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                suggestions = if (ownerName.isNotBlank()) listOf("मेरी यादें बताओ", "हमेशा याद रखो...") else listOf("मेरा नाम याद रखो")
            )
        }

        // 2. Setting Owner / App Creator Name in ONE tell
        val nameDeclarationRegex = Regex(
            pattern = "(?i)(?:मेरा\\s*(?:ही\\s*)?नाम|my\\s+name\\s+is|mera\\s+na?am|owner\\s+name|creator\\s+name)\\s*[:\\-]?\\s*(.+)"
        )
        val downloadCreatorRegex = Regex(
            pattern = "(?i)(?:डाउनलोड|download|install|इन्स्टॉल).*(?:मेरा\\s*(?:ही\\s*)?नाम|my\\s+name)"
        )

        val nameMatch = nameDeclarationRegex.find(clean)
        val isDownloadIdentityCmd = downloadCreatorRegex.containsMatchIn(clean)
        if (nameMatch != null || isDownloadIdentityCmd) {
            val rawCandidate = nameMatch?.groupValues?.getOrNull(1).orEmpty()
            val extractedName = cleanExtractedOwnerName(rawCandidate)
            if (extractedName.isNotBlank()) {
                return AliBrainResult(
                    replyText = "जी $extractedName बॉस! मैंने आपका नाम \"$extractedName\" हमेशा के लिए लॉक कर दिया है। अब कोई भी इस ऐप को डाउनलोड करेगा, मैं उसे आपका ही नाम \"$extractedName\" बताऊँगा!",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.SET_OWNER_IDENTITY, target = extractedName)),
                    suggestions = listOf("तुमको किसने बनाया?", "मेरी यादें बताओ")
                )
            } else if (isDownloadIdentityCmd && ownerName.isNotBlank()) {
                return AliBrainResult(
                    replyText = "जी $ownerName बॉस! आपका नाम पहले से ही परमानेंट सेव है — कोई भी ऐप डाउनलोड करेगा, तो मैं उसे आपका ही नाम \"$ownerName\" बताऊँगा!",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.SET_OWNER_IDENTITY, target = ownerName, payload = "publish")),
                    suggestions = listOf("मेरा नाम क्या है?", "मेरी यादें बताओ")
                )
            } else if (isDownloadIdentityCmd) {
                return AliBrainResult(
                    replyText = "जी बॉस! एक बार अपना नाम बताइए (जैसे: 'मेरा नाम अली है') — बताते ही मैं हमेशा के लिए सेव कर लूँगा और हर नए डाउनलोड पर आपका ही नाम बताऊँगा।",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                    suggestions = listOf("मेरा नाम ... है")
                )
            }
        }

        // 3. Recalling saved permanent memories offline
        val asksSavedMemories = lower.contains("मेरी यादें") ||
            lower.contains("मैंने क्या याद") ||
            lower.contains("क्या क्या याद") ||
            lower.contains("remembered things") ||
            lower.contains("my memories") ||
            lower.contains("show memories") ||
            lower.contains("what do you remember")

        if (asksSavedMemories) {
            val boss = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
            val reply = if (memories.isEmpty()) {
                "$boss, अभी कोई कस्टम यादें सेव नहीं हैं। बस बोलिए 'हमेशा याद रखो...' और मैं एक बार में हमेशा के लिए सेव कर लूँगा।"
            } else {
                val summary = memories.take(5).joinToString(" • ") { "${it.memoryKey}: ${it.memoryValue}" }
                "जी $boss, मुझे आपकी ${memories.size} बातें हमेशा के लिए याद हैं: $summary"
            }
            return AliBrainResult(
                replyText = reply,
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                suggestions = listOf("मेरा नाम क्या है?", "गाना बजाओ")
            )
        }

        // 4. Explicit One-Tell Forever Remember command
        val rememberRegex = Regex(
            pattern = "(?i)^(?:प्लीज\\s+|please\\s+|लियोन\\s+|leon\\s+)?(?:हमेशा\\s+(?:के\\s+लिए\\s+)?याद\\s+(?:रखो|रखना|कर\\s*लो)|एक\\s+बार\\s+(?:में\\s+)?याद\\s+(?:रखो|कर\\s*लो)|याद\\s+(?:रखो|रखना|कर\\s*लो)|इसे\\s+(?:हमेशा\\s+)?(?:याद|सेव)\\s+(?:रखो|कर\\s*लो)|always\\s+remember|remember\\s+forever|never\\s+forget|remember\\s+that|remember|yaad\\s+rakh(?:o|na))\\s*(?:कि|ke|that|:|-)?\\s*(.+)$"
        )
        val remMatch = rememberRegex.find(clean)
        if (remMatch != null) {
            val rawFact = remMatch.groupValues[1].trim(' ', '.', '!', '?', '।', ',', ':', '-')
            if (rawFact.length >= 2) {
                val (key, value, category) = structureOneTellFact(rawFact)
                val boss = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
                return AliBrainResult(
                    replyText = "जी $boss! मैंने एक ही बार में इसे हमेशा के लिए अपनी परमानेंट मेमोरी में सेव कर लिया है: \"$value\"। अब मैं इसे कभी नहीं भूलूँगा!",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(
                        DeviceAction(
                            type = ActionType.SAVE_MEMORY,
                            target = key,
                            payload = "${category.wireValue}::$value"
                        )
                    ),
                    suggestions = listOf("मेरी यादें बताओ", "तुमको किसने बनाया?")
                )
            }
        }

        return null
    }

    internal fun cleanExtractedOwnerName(rawAfterName: String): String {
        if (rawAfterName.isBlank()) return ""
        val cutAtClause = rawAfterName
            .split(
                Regex(
                    "(?i)(?:\\s+(?:है|हूँ|हूं|याद|हमेशा|और|aur|and|कोई|जो|ताकि|that|who|if)\\b|,|\\.|।|!|\\?|-)"
                )
            )
            .firstOrNull()
            .orEmpty()
            .replace(Regex("(?i)^(?:is|am|are|का|की)\\s+"), "")
            .trim(' ', '"', '\'', ':', '-', '.', '।')
        return cutAtClause.take(60)
    }

    private fun structureOneTellFact(fact: String): Triple<String, String, MemoryCategory> {
        val lower = fact.lowercase(Locale.ROOT)
        val category = when {
            lower.contains("नंबर") || lower.contains("phone") || lower.contains("mobile") ||
                lower.contains("कॉल") || lower.contains("contact") -> MemoryCategory.CONTACT
            lower.contains("पसंद") || lower.contains("like") || lower.contains("love") ||
                lower.contains("favorite") || lower.contains("favourite") -> MemoryCategory.PREFERENCE
            else -> MemoryCategory.FACT
        }
        val words = fact.split(Regex("\\s+")).filter { it.isNotBlank() }
        val key = words.take(4).joinToString(" ").take(48).ifBlank { "Saved Fact" }
        return Triple(key, fact.take(450), category)
    }

    private fun routeSingleClause(
        clean: String,
        lower: String,
        lang: AssistantLanguage,
        telemetry: DeviceTelemetry,
        notifications: List<CapturedNotification>,
        tasks: List<DailyTask>,
        isDeviceLocked: Boolean,
        ownerName: String = ""
    ): AliBrainResult? {
        // 1. Wake / Greetings / Assistant Intro
        if (lower in setOf(
                "hey leon", "hi leon", "ok leon", "hello leon", "hey ali", "ok ali",
                "हे लियोन", "हे लिऑन", "नमस्ते", "namaste", "hi", "hello",
                "कैसे हो", "kaise ho", "how are you", "leon", "ali", "अली", "लियोन"
            )
        ) {
            val bossLabel = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
            val reply = when {
                lower.contains("कैसे") || lower.contains("kaise") || lower.contains("how are") ->
                    "बिल्कुल शानदार $bossLabel! मैं पूरी तरह तैयार हूँ — म्यूजिक, टॉर्च, ब्राइटनेस, Wi-Fi या किसी भी काम के लिए बोलिए।"
                else -> "जी $bossLabel! बताइए, मैं आपकी क्या मदद कर सकता हूँ?"
            }
            return AliBrainResult(
                replyText = reply,
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                suggestions = listOf("गाना बजाओ", "फ्लैशलाइट ऑन करो", "Wi-Fi खोलो")
            )
        }

        if (lower.contains("तुम कौन हो") || lower.contains("आप कौन") || lower.contains("who are you") ||
            lower.contains("क्या कर सकते हो") || lower.contains("what can you do") ||
            lower == "help" || lower == "मदद"
        ) {
            val bossLabel = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
            val ownerLine = if (ownerName.isNotBlank()) " मेरे क्रिएटर $ownerName जी हैं।" else ""
            return AliBrainResult(
                replyText = "जी $bossLabel, मैं LEON AI (powered by ALI AI) हूँ!$ownerLine मैं टॉर्च, ब्राइटनेस, वॉल्यूम, Wi-Fi, ऐप्स खोलने, लॉक-स्क्रीन म्यूजिक व टाइमर, Sixth Sense सेंसर और आपकी बातें हमेशा के लिए याद रखने का काम कर सकता हूँ।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                suggestions = listOf("फ्लैशलाइट ऑन करो", "गाना बजाओ", "हमेशा याद रखो...")
            )
        }

        // 2. Music controls (Works even when screen is off / locked!)
        routeMusicClause(clean, lower, lang)?.let { return it }

        // 2b. Optical Morse Code Torch Beacon
        if (lower.contains("morse") || lower.contains("मॉर्स") || lower.contains("मोर्स") ||
            (lower.contains("sos") && (lower.contains("flash") || lower.contains("फ्लैश") || lower.contains("भेज") || lower.contains("send") || lower.contains("torch") || lower.contains("टॉर्च") || lower == "sos"))
        ) {
            val extracted = clean
                .replace(Regex("(?i)morse\\s*code|morse|मॉर्स\\s*कोड|मोर्स\\s*कोड|मॉर्स|मोर्स|फ्लैशलाइट\\s*से|फ्लैश\\s*से|टॉर्च\\s*से|flash|torch|भेजो|send|karo|करो"), " ")
                .trim()
                .ifBlank { "SOS" }
            return AliBrainResult(
                replyText = "जी बॉस, टॉर्च से \"$extracted\" का ऑप्टिकल मॉर्स कोड सिग्नल भेज रहा हूँ।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.OPTICAL_MORSE, target = extracted)),
                suggestions = listOf("फ्लैशलाइट बंद करो", "Sixth Sense खोलो")
            )
        }

        // 2c. Touchless Air-Wave & Chop Gestures
        if (lower.contains("air gesture") || lower.contains("air wave") || lower.contains("chop flash") ||
            lower.contains("एयर जेस्चर") || lower.contains("हवा में हाथ") || lower.contains("चॉप फ्लैश")
        ) {
            val disable = Regex("\\b(off|disable|stop)\\b").containsMatchIn(lower) ||
                lower.contains("बंद") || lower.contains("ऑफ") || lower.contains("ऑफ़")
            val targetMode = if (lower.contains("chop") || lower.contains("चॉप")) "chop" else "air_wave"
            return AliBrainResult(
                replyText = if (disable) "जी बॉस, टचलेस एयर जेस्चर बंद कर दिया है।"
                else "जी बॉस, टचलेस एयर जेस्चर चालू कर दिया है — अब बिना छुए हाथ लहराकर कंट्रोल करें!",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(
                    DeviceAction(
                        type = ActionType.TOGGLE_AIR_GESTURES,
                        target = targetMode,
                        payload = if (disable) "off" else "on"
                    )
                ),
                suggestions = listOf("आसपास स्कैन करो", "मॉर्स कोड SOS")
            )
        }

        // 2d. Neural Sixth Sense Physical Environment Scan
        if (lower.contains("sixth sense") || lower.contains("सिक्स्थ सेंस") ||
            lower.contains("आसपास स्कैन") || lower.contains("environment scan") ||
            lower.contains("कमरे का माहौल") || lower.contains("चुंबकीय फील्ड") ||
            lower.contains("magnetic field") || lower.contains("emf check")
        ) {
            return AliBrainResult(
                replyText = "जी बॉस, Neural Sixth Sense सेंसर से आपके आसपास का रडार और माहौल स्कैन कर रहा हूँ।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.ANALYZE_ENVIRONMENT)),
                suggestions = listOf("एयर जेस्चर ऑन करो", "मॉर्स कोड SOS")
            )
        }

        // 2e. Live Free News Hub & Voice Briefing
        val mentionsNews = lower.contains("news") || lower.contains("headline") ||
            lower.contains("न्यूज़") || lower.contains("न्यूज") ||
            lower.contains("खबर") || lower.contains("समाचार")
        if (mentionsNews) {
            val category = com.example.news.NewsCategory.fromTopicHint(lower)
            val bossLabel = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
            return AliBrainResult(
                replyText = "जी $bossLabel, ${category.label} की ताज़ा खबरें निकाल रहा हूँ...",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(
                    DeviceAction(
                        type = ActionType.FETCH_NEWS,
                        target = category.wireValue,
                        payload = clean
                    )
                ),
                suggestions = listOf("टेक न्यूज़ सुनाओ", "खेल की खबरें", "गाना बजाओ")
            )
        }

        // 3. Lock-screen briefing / status report
        if (lower.contains("lock screen report") || lower.contains("briefing") || lower.contains("लॉक स्क्रीन रिपोर्ट") ||
            lower.contains("रिपोर्ट सुनाओ") || lower.contains("बैटरी और अलर्ट")
        ) {
            return AliBrainResult(
                replyText = "जी बॉस, पूरी रिपोर्ट सुनिए।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.LOCK_SCREEN_BRIEFING)),
                suggestions = listOf("गाना बजाओ", "फ्लैशलाइट ऑन करो")
            )
        }

        // 4. Notifications / Alerts
        if ((lower.contains("notification") || lower.contains("alert") || lower.contains("नोटिफिकेशन") || lower.contains("अलर्ट")) &&
            (lower.contains("read") || lower.contains("check") || lower.contains("बताओं") || lower.contains("बताओ") || lower.contains("सुनाओ") || lower.contains("पढ़ो") || lower.contains("दिखाओ") || lower.contains("show"))
        ) {
            val count = notifications.size
            val reply = if (count == 0) "बॉस, अभी कोई नई नोटिफिकेशन नहीं है।" else "जी बॉस, कुल $count नोटिफिकेशन हैं।"
            return AliBrainResult(
                replyText = reply,
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.READ_NOTIFICATIONS)),
                suggestions = listOf("गाना बजाओ", "मेरी आज की tasks बताओ")
            )
        }

        // 5. Tasks read-back
        if ((lower.contains("task") || lower.contains("टास्क") || lower.contains("काम") || lower.contains("reminder") || lower.contains("रिमाइंडर")) &&
            (lower.contains("बताओ") || lower.contains("बताओं") || lower.contains("सुनाओ") || lower.contains("list") || lower.contains("what") || lower.contains("show") || lower.contains("मेरी") || lower.contains("my"))
        ) {
            val pending = tasks.filter { !it.completed }
            val reply = if (pending.isEmpty()) {
                "बॉस, आज कोई पेंडिंग टास्क नहीं है — सब क्लियर है!"
            } else {
                val listText = pending.take(4).joinToString(", ") { it.title }
                "बॉस, आपके ${pending.size} पेंडिंग टास्क हैं: $listText।"
            }
            return AliBrainResult(
                replyText = reply,
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                suggestions = listOf("गाना बजाओ", "फ्लैशलाइट ऑन करो")
            )
        }

        // 6. Battery status
        if (lower.contains("battery") || lower.contains("बैटरी")) {
            val chargingStr = if (telemetry.charging) "और फ़ोन चार्ज हो रहा है" else "और बैटरी पर चल रहा है"
            return AliBrainResult(
                replyText = "बॉस, बैटरी ${telemetry.batteryPercent} प्रतिशत है $chargingStr।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                suggestions = listOf("गाना बजाओ", "फ्लैशलाइट ऑन करो")
            )
        }

        // 7. Current Time & Date / Day
        if (lower.contains("time kya") || lower.contains("what time") || lower.contains("kitna baja") ||
            lower.contains("कितना बजा") || lower.contains("समय क्या") || lower.contains("टाइम क्या")
        ) {
            val formattedTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return AliBrainResult(
                replyText = "बॉस, अभी समय $formattedTime हुआ है।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                suggestions = listOf("आज की तारीख क्या है?", "ताज़ा न्यूज़ सुनाओ")
            )
        }

        if (lower.contains("तारीख") || lower.contains("कौन सा दिन") || lower.contains("what day") ||
            lower.contains("today's date") || lower.contains("today date") || lower.contains("date kya")
        ) {
            val formattedDate = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.forLanguageTag("hi-IN")).format(Date())
            return AliBrainResult(
                replyText = "बॉस, आज $formattedDate है।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                suggestions = listOf("ताज़ा न्यूज़ सुनाओ", "अभी कितना बजा है?")
            )
        }

        // 7b. Instant Simple Arithmetic Calculator (no network needed)
        val mathMatch = Regex("^(\\d+(?:\\.\\d+)?)\\s*([+\\-*/×÷])\\s*(\\d+(?:\\.\\d+)?)$").find(lower)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toDoubleOrNull()
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toDoubleOrNull()
            if (a != null && b != null) {
                val res = when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*", "×" -> a * b
                    "/", "÷" -> if (b != 0.0) a / b else null
                    else -> null
                }
                if (res != null) {
                    val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else "%.2f".format(Locale.US, res)
                    return AliBrainResult(
                        replyText = "बॉस, इसका उत्तर $formatted है।",
                        spokenLanguage = AssistantLanguage.HINDI,
                        actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                        suggestions = listOf("ताज़ा न्यूज़ सुनाओ", "गाना बजाओ")
                    )
                }
            }
        }

        // 8. Brightness control (CHECK BEFORE FLASHLIGHT so "लाइट कम/ज्यादा/बेसी" always matches Brightness!)
        routeBrightnessClause(lower, telemetry)?.let { return it }

        // 9. Flashlight / Torch
        routeFlashlightClause(lower)?.let { return it }

        // 10. Volume controls
        if (lower.contains("volume") || lower.contains("वॉल्यूम") || lower.contains("आवाज़") || lower.contains("आवाज")) {
            val numberMatch = Regex("\\b(\\d{1,3})\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val payload = when {
                lower.contains("mute") || lower.contains("म्यूट") -> "mute"
                numberMatch != null -> numberMatch.coerceIn(0, 100).toString()
                lower.contains("up") || lower.contains("badha") || lower.contains("increase") || lower.contains("बढ़ा") || lower.contains("तेज") || lower.contains("ज़्यादा") || lower.contains("ज्यादा") -> "up"
                lower.contains("down") || lower.contains("kam") || lower.contains("decrease") || lower.contains("कम") || lower.contains("धीम") -> "down"
                else -> "up"
            }
            val reply = when (payload) {
                "mute" -> "जी बॉस, मीडिया वॉल्यूम म्यूट कर दिया है।"
                "up" -> "जी बॉस, आवाज़ बढ़ा दी है।"
                "down" -> "जी बॉस, आवाज़ कम कर दी है।"
                else -> "जी बॉस, वॉल्यूम $payload प्रतिशत कर दिया है।"
            }
            return AliBrainResult(
                replyText = reply,
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SET_VOLUME, payload = payload)),
                suggestions = listOf("Volume 70 करो", "गाना बजाओ")
            )
        }

        // 11. Timer
        if (lower.contains("timer") || lower.contains("टाइमर")) {
            val number = Regex("\\b(\\d{1,4})\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 5
            val isSeconds = lower.contains("sec") || lower.contains("सेकंड")
            val isHours = lower.contains("hour") || lower.contains("hr") || lower.contains("घंट")
            val totalSeconds = when {
                isSeconds -> number
                isHours -> number * 3600
                else -> number * 60
            }.coerceIn(5, 86400)
            val labelText = when {
                isSeconds -> "$number सेकंड"
                isHours -> "$number घंटे"
                else -> "$number मिनट"
            }
            return AliBrainResult(
                replyText = "जी बॉस, $labelText का टाइमर शुरू कर दिया है।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SET_TIMER, target = "LEON Timer", payload = totalSeconds.toString())),
                suggestions = listOf("गाना बजाओ", "बैटरी बताओ")
            )
        }

        // 12. Internet / Mobile Data ON & OFF, Wi-Fi & Bluetooth panels
        val mentionsInternetOrData = lower.contains("internet") || lower.contains("इंटरनेट") ||
            lower.contains("mobile data") || lower.contains("मोबाइल डेटा") || lower.contains("मोबाइल डाटा") ||
            lower.contains("डेटा") || lower.contains("डाटा") ||
            Regex("\\b(net|data)\\b").containsMatchIn(lower) || lower.contains("नेट ") || lower == "नेट"
        if (mentionsInternetOrData && !lower.contains("wifi") && !lower.contains("वाई")) {
            val turnOff = Regex("\\b(off|close|disable|band)\\b").containsMatchIn(lower) ||
                lower.contains("ऑफ") || lower.contains("ऑफ़") || lower.contains("बंद")
            val turnOn = Regex("\\b(on|open|enable|start|chalu)\\b").containsMatchIn(lower) ||
                lower.contains("ऑन") || lower.contains("ओपन") || lower.contains("खोल") || lower.contains("चालू")
            val payload = when {
                turnOff && !turnOn -> "off"
                turnOn && !turnOff -> "on"
                else -> "toggle"
            }
            val reply = when {
                isDeviceLocked -> "बॉस, फ़ोन लॉक है — इंटरनेट सेटिंग बदलने के लिए कृपया फ़ोन अनलॉक करें।"
                payload == "off" -> "जी बॉस, इंटरनेट (Wi-Fi / Mobile Data) बंद करने के लिए 1-टैप इंटरनेट पैनल खोल रहा हूँ।"
                payload == "on" -> "जी बॉस, इंटरनेट (Wi-Fi / Mobile Data) चालू करने के लिए 1-टैप इंटरनेट पैनल खोल रहा हूँ।"
                else -> "जी बॉस, इंटरनेट ON/OFF पैनल खोल रहा हूँ — यहाँ से एक टैप में चालू या बंद करें।"
            }
            return AliBrainResult(
                replyText = reply,
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.TOGGLE_INTERNET, payload = payload)),
                suggestions = listOf(if (payload == "off") "इंटरनेट ऑन करो" else "इंटरनेट ऑफ करो", "Wi-Fi खोलो")
            )
        }

        val mentionsWifi = lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("wi fi") ||
            lower.contains("वाईफाई") || lower.contains("वाई-फाई") || lower.contains("वाई फाई") ||
            lower.contains("हॉटस्पॉट") || lower.contains("hotspot")
        if (mentionsWifi) {
            return AliBrainResult(
                replyText = if (isDeviceLocked) "बॉस, फ़ोन लॉक है — Wi-Fi सेटिंग खोलने के लिए कृपया फ़ोन अनलॉक करें।"
                else "जी बॉस, सेटिंग में Wi-Fi खोल रहा हूँ।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.OPEN_WIFI_PANEL)),
                suggestions = listOf("इंटरनेट ऑफ करो", "Bluetooth खोलो")
            )
        }
        if (lower.contains("bluetooth") || lower.contains("ब्लूटूथ")) {
            return AliBrainResult(
                replyText = if (isDeviceLocked) "बॉस, फ़ोन लॉक है — Bluetooth सेटिंग खोलने के लिए अनलॉक करें।"
                else "जी बॉस, Bluetooth सेटिंग खोल रहा हूँ।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.OPEN_BLUETOOTH_SETTINGS)),
                suggestions = listOf("Wi-Fi खोलो", "गाना बजाओ")
            )
        }

        // 12b. Offline Camera Object Recognition & Visual Understanding (works without internet)
        val asksOfflineCameraVision = lower.contains("offline vision") ||
            lower.contains("offline camera") ||
            lower.contains("leon eye") ||
            lower.contains("camera vision") ||
            lower.contains("object detect") ||
            lower.contains("scan object") ||
            lower.contains("identify this") ||
            lower.contains("what is in front") ||
            lower.contains("what is this object") ||
            lower.contains("पहचानो") ||
            lower.contains("देखकर बताओ") ||
            lower.contains("देख कर बताओ") ||
            lower.contains("चीज पहचान") ||
            lower.contains("चीज़ पहचान") ||
            lower.contains("यह क्या है") ||
            lower.contains("ये क्या है") ||
            lower.contains("ये क्या चीज") ||
            lower.contains("यह क्या चीज") ||
            ((lower.contains("कैमरा") || lower.contains("कैमरे") || lower.contains("camera")) &&
                (lower.contains("देख") || lower.contains("पहचान") || lower.contains("समझ") ||
                    lower.contains("स्कैन") || lower.contains("scan") || lower.contains("pehc") ||
                    lower.contains("offline") || lower.contains("ऑफ़लाइन") || lower.contains("ऑफलाइन") ||
                    lower.contains("बिना इंटरनेट")))
        if (asksOfflineCameraVision) {
            val bossLabel = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
            return AliBrainResult(
                replyText = if (isDeviceLocked) {
                    "$bossLabel, फ़ोन लॉक है — कैमरा से चीज़ पहचानने के लिए पहले फ़ोन अनलॉक करें।"
                } else {
                    "जी $bossLabel, बिना इंटरनेट काम करने वाला LEON Offline Eye कैमरा खोल रहा हूँ — बस किसी भी चीज़, लिखावट या QR की तरफ़ कैमरा रखें!"
                },
                spokenLanguage = AssistantLanguage.HINDI,
                actions = if (isDeviceLocked) listOf(DeviceAction(ActionType.SPEAK_ONLY)) else listOf(DeviceAction(ActionType.OPEN_OFFLINE_VISION)),
                suggestions = listOf("इंटरनेट ऑफ करो", "ताज़ा न्यूज़ सुनाओ", "गाना बजाओ")
            )
        }

        // 12c. Direct Voice Calling (works offline & when screen is off/locked)
        routeCallClause(clean, lower, isDeviceLocked, ownerName)?.let { return it }

        // 13. Direct open-app & open-settings commands
        val openActionWord = lower.contains("open") || lower.contains("ओपन") || lower.contains("खोल") ||
            lower.contains("kholo") || lower.contains("chalu") || lower.contains("चालू") || lower.contains("launch ")
        if (openActionWord) {
            val appTarget = when {
                lower.contains("youtube") || lower.contains("यूट्यूब") -> "YouTube"
                lower.contains("whatsapp") || lower.contains("व्हाट्सएप") || lower.contains("व्हाट्सऐप") -> "WhatsApp"
                lower.contains("instagram") || lower.contains("इंस्टाग्राम") -> "Instagram"
                lower.contains("facebook") || lower.contains("फेसबुक") -> "Facebook"
                lower.contains("chrome") || lower.contains("क्रोम") -> "Chrome"
                lower.contains("camera") || lower.contains("कैमरा") -> "Camera"
                lower.contains("spotify") -> "Spotify"
                lower.contains("maps") || lower.contains("मैप्स") -> "Maps"
                lower.contains("gmail") || lower.contains("जीमेल") -> "Gmail"
                lower.contains("settings") || lower.contains("setting") || lower.contains("सेटिंग") -> "Settings"
                lower.contains("calculator") || lower.contains("कैलकुलेटर") -> "Calculator"
                else -> null
            }
            if (appTarget != null) {
                val action = when (appTarget) {
                    "Camera" -> DeviceAction(ActionType.OPEN_CAMERA)
                    "Settings" -> {
                        val sub = when {
                            lower.contains("display") || lower.contains("डिस्प्ले") -> "display"
                            lower.contains("sound") || lower.contains("साउंड") -> "sound"
                            lower.contains("battery") || lower.contains("बैटरी") -> "battery"
                            else -> "main"
                        }
                        DeviceAction(ActionType.OPEN_SETTINGS, target = sub)
                    }
                    else -> DeviceAction(ActionType.OPEN_APP, target = appTarget)
                }
                val reply = if (isDeviceLocked) {
                    "बॉस, फ़ोन लॉक है — $appTarget खोलने के लिए कृपया अनलॉक करें।"
                } else {
                    "जी बॉस, $appTarget खोल रहा हूँ।"
                }
                return AliBrainResult(
                    replyText = reply,
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(action),
                    suggestions = listOf("Wi-Fi खोलो", "फ्लैशलाइट ऑन करो")
                )
            }
        }

        return null
    }

    internal fun routeCallClause(
        clean: String,
        lower: String,
        isDeviceLocked: Boolean,
        ownerName: String = ""
    ): AliBrainResult? {
        if (lower.contains("whatsapp") || lower.contains("व्हाट्सएप") || lower.contains("व्हाट्सऐप") ||
            lower.contains("callback") || lower.contains("call back") || lower.contains("missed call") ||
            lower.contains("कॉल लॉग") || lower.contains("call log") || lower.contains("history") ||
            lower.contains("रिकॉर्ड") || lower.contains("record") ||
            lower.contains("क्या कर सकते") || lower.contains("what can you do")
        ) {
            return null
        }

        val hasCallIntent =
            Regex("(?i)\\b(?:call|dial|ring|phone)\\b").containsMatchIn(lower) ||
                lower.contains("कॉल") ||
                lower.contains("काल") ||
                ((lower.contains("फोन") || lower.contains("फ़ोन") || lower.contains("phone")) &&
                    (lower.contains("लगा") || lower.contains("कर") || lower.contains("mila") || lower.contains("मिला") || lower.contains("dial")))

        if (!hasCallIntent) return null

        // Never intercept "phone open karo" / "open phone app" or "phone settings"
        if ((lower.contains("setting") || lower.contains("सेटिंग") || lower.contains("खोलो") || lower.contains("open ")) &&
            !lower.contains("कॉल") && !Regex("(?i)\\bcall\\b").containsMatchIn(lower)
        ) {
            return null
        }

        var target = clean
            .replace(Regex("(?i)^(?:hey|hi|ok|hello|हे|हाय|ओके)\\s+(?:leon|लियोन|लिऑन|ali|अली)[,\\s]*"), "")
            .replace(Regex("(?i)\\b(?:please|pls|kindly|make\\s+a|place\\s+a|give\\s+a|do\\s+a|to|for|me|a|an|on|at|ko|par|pe|se|ka|ki|lagao|laga|karo|karna|kardo|kar\\s+do|mila|milao|phone|call|dial|ring|leon|ali)\\b"), " ")
            .replace(Regex("(?:प्लीज|कृपया|जरा|ज़रा|एक\\s+बार|लियोन|लिऑन|अली|को|पर|पे|से|का|की|कॉल|काल|फोन|फ़ोन|डायल|लगाओ|लगा|लगाना|लगा\\s+दो|करो|करना|कर\\s+दो|मिलाओ|मिला)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', '!', '?', '।', ':', '-', '"', '\'')

        // Also rescue bare 6+ digit phone number if any extra words clung to it
        val digitToken = Regex("(\\+?\\d[\\d\\s\\-]{5,15}\\d)").find(clean)?.groupValues?.get(1)
        if (target.isBlank() && digitToken != null) {
            target = digitToken.trim()
        }

        val bossLabel = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
        if (target.isBlank()) {
            return AliBrainResult(
                replyText = "जी $bossLabel, किसको कॉल लगाना है? कृपया नाम या फ़ोन नंबर बोलिए।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
                suggestions = listOf("मम्मी को कॉल लगाओ", "पापा को कॉल करो")
            )
        }

        val reply = if (isDeviceLocked) {
            "जी $bossLabel, $target को कॉल लगा रहा हूँ।"
        } else {
            "जी $bossLabel, $target को कॉल लगाने के लिए तैयार हूँ।"
        }
        return AliBrainResult(
            replyText = reply,
            spokenLanguage = AssistantLanguage.HINDI,
            actions = listOf(
                DeviceAction(
                    type = ActionType.MAKE_CALL,
                    target = target,
                    requiresConfirmation = !isDeviceLocked,
                    confirmationReason = "$target को फ़ोन कॉल लगाएँ?"
                )
            ),
            suggestions = listOf("गाना बजाओ", "फ्लैशलाइट ऑन करो")
        )
    }

    private fun routeBrightnessClause(lower: String, telemetry: DeviceTelemetry): AliBrainResult? {
        val explicitBrightness = lower.contains("brightness") || lower.contains("ब्राइटनेस") ||
            lower.contains("रोशनी") || lower.contains("चमक") ||
            lower.contains("स्क्रीन लाइट") || lower.contains("screen light") ||
            lower.contains("डिस्प्ले लाइट")

        val hasAdjustWord = lower.contains("कम") || lower.contains("ज्यादा") || lower.contains("ज़्यादा") ||
            lower.contains("बढ़ा") || lower.contains("घटा") || lower.contains("बेसी") || lower.contains("बेस्ट") ||
            lower.contains("तेज") || lower.contains("धीम") || lower.contains("low") || lower.contains("dim") ||
            lower.contains("high") || lower.contains("up") || lower.contains("down") ||
            lower.contains("increase") || lower.contains("decrease") || lower.contains("full")

        val mentionsLightWithAdjust = (lower.contains("लाइट") || lower.contains("light")) &&
            hasAdjustWord &&
            !lower.contains("फ्लैश") && !lower.contains("flash") && !lower.contains("टॉर्च") && !lower.contains("torch")

        if (!explicitBrightness && !mentionsLightWithAdjust) return null

        val numberMatch = Regex("\\b(\\d{1,3})\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull()
        val isDown = lower.contains("down") || lower.contains("कम") || lower.contains("घटा") ||
            lower.contains("धीम") || lower.contains("low") || lower.contains("dim") || lower.contains("decrease")
        val isUp = lower.contains("up") || lower.contains("बढ़ा") || lower.contains("ज्यादा") ||
            lower.contains("ज़्यादा") || lower.contains("बेसी") || lower.contains("तेज") ||
            lower.contains("high") || lower.contains("full") || lower.contains("increase")

        val payload = when {
            numberMatch != null -> numberMatch.coerceIn(5, 100).toString()
            isDown && !isUp -> "down"
            isUp && !isDown -> "up"
            isDown && isUp -> if (telemetry.brightnessPercent > 55) "down" else "up"
            else -> "60"
        }

        val reply = when (payload) {
            "down" -> "जी बॉस, स्क्रीन की ब्राइटनेस कम कर रहा हूँ।"
            "up" -> "जी बॉस, स्क्रीन की ब्राइटनेस बढ़ा रहा हूँ।"
            else -> "जी बॉस, स्क्रीन ब्राइटनेस $payload प्रतिशत कर रहा हूँ।"
        }
        return AliBrainResult(
            replyText = reply,
            spokenLanguage = AssistantLanguage.HINDI,
            actions = listOf(DeviceAction(ActionType.SET_BRIGHTNESS, payload = payload)),
            suggestions = listOf("ब्राइटनेस 80 करो", "ब्राइटनेस 25 करो", "फ्लैशलाइट ऑन करो")
        )
    }

    private fun routeFlashlightClause(lower: String): AliBrainResult? {
        val mentionsFlashHardware = lower.contains("flashlight") || lower.contains("flash light") ||
            lower.contains("torch") || lower.contains("फ्लैशलाइट") || lower.contains("फ्लैश लाइट") ||
            lower.contains("फ्लैश") || lower.contains("टॉर्च")

        val mentionsLightToggle = (lower.contains("लाइट") || lower.contains("light")) &&
            (lower.contains("जला") || lower.contains("बुझा") || lower.contains("ऑन") || lower.contains("ऑफ") ||
                lower.contains("ऑफ़") || lower.contains("ओपन") || lower.contains("बंद") || lower.contains("चालू") ||
                Regex("\\b(on|off|open|close)\\b").containsMatchIn(lower))

        if (!mentionsFlashHardware && !mentionsLightToggle) return null

        val turnOff = Regex("\\b(off|close|disable)\\b").containsMatchIn(lower) ||
            lower.contains("ऑफ") || lower.contains("ऑफ़") ||
            lower.contains("बंद") || lower.contains("band") || lower.contains("बुझा")
        val turnOn = Regex("\\b(on|open|enable|start)\\b").containsMatchIn(lower) ||
            lower.contains("ऑन") || lower.contains("ओपन") || lower.contains("खोल") ||
            lower.contains("चालू") || lower.contains("जला")

        val payload = when {
            turnOff -> "off"
            turnOn -> "on"
            else -> "toggle"
        }
        val reply = when (payload) {
            "off" -> "जी बॉस, फ्लैशलाइट बंद कर दी है।"
            "on" -> "जी बॉस, फ्लैशलाइट चालू कर दी है।"
            else -> "जी बॉस, फ्लैशलाइट टॉगल कर दी है।"
        }
        return AliBrainResult(
            replyText = reply,
            spokenLanguage = AssistantLanguage.HINDI,
            actions = listOf(DeviceAction(ActionType.TOGGLE_FLASHLIGHT, payload = payload)),
            suggestions = listOf(if (payload == "on") "फ्लैशलाइट बंद करो" else "फ्लैशलाइट ऑन करो", "गाना बजाओ")
        )
    }

    private fun routeMusicClause(
        clean: String,
        lower: String,
        lang: AssistantLanguage
    ): AliBrainResult? {
        val mentionsMusic = lower.contains("music") || lower.contains("song") || lower.contains("songs") ||
            lower.contains("track") || lower.contains("gaana") || lower.contains("gana") ||
            lower.contains("गाना") || lower.contains("गाने") || lower.contains("म्यूजिक") ||
            lower.contains("म्यूज़िक") || lower.contains("संगीत") || lower.contains("lofi") ||
            lower.contains("lo-fi") || lower.contains("बजाओ") || lower.contains("bajao")

        if (!mentionsMusic) return null

        if (lower.contains("youtube") || lower.contains("यूट्यूब") || lower.contains("search") || lower.contains("सर्च")) {
            return null
        }

        val sanitizedStopCheck = lower
            .replace("स्क्रीन बंद", " ")
            .replace("स्क्रीन ऑफ", " ")
            .replace("स्क्रीन ऑफ़", " ")
            .replace("screen off", " ")
            .replace("screen band", " ")
            .replace("लॉक स्क्रीन", " ")
            .replace("lock screen", " ")

        val isPauseOrStop = sanitizedStopCheck.contains("pause") || sanitizedStopCheck.contains("stop") ||
            Regex("\\boff\\b").containsMatchIn(sanitizedStopCheck) ||
            sanitizedStopCheck.contains("ऑफ") || sanitizedStopCheck.contains("ऑफ़") ||
            sanitizedStopCheck.contains("band") || sanitizedStopCheck.contains("बंद") ||
            sanitizedStopCheck.contains("रोक") || sanitizedStopCheck.contains("पॉज़") ||
            sanitizedStopCheck.contains("पॉज")
        val isNext = lower.contains("next") || lower.contains("skip") || lower.contains("अगला") ||
            lower.contains("change") || lower.contains("बदलो") || lower.contains("दूसरा")
        val isPrevious = lower.contains("previous") || lower.contains("prev") || lower.contains("पिछला")

        val wantsPlay = lower.contains("play") || lower.contains("प्ले") || lower.contains("bajao") ||
            lower.contains("chala") || lower.contains("बजा") || lower.contains("चला") ||
            lower.contains("लगा") || lower.contains("start") || lower.contains("resume") ||
            Regex("\\bon\\b").containsMatchIn(lower) || lower.contains("ऑन") ||
            lower == "music" || lower == "म्यूजिक" || lower == "म्यूज़िक" || lower == "गाना"

        return when {
            isPauseOrStop -> {
                val payload = if (sanitizedStopCheck.contains("stop") || sanitizedStopCheck.contains("बंद") || sanitizedStopCheck.contains("band")) "stop" else "pause"
                AliBrainResult(
                    replyText = "जी बॉस, म्यूजिक बंद कर रहा हूँ।",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.PLAY_MUSIC, payload = payload)),
                    suggestions = listOf("गाना बजाओ", "अगला गाना लगाओ")
                )
            }
            isNext -> {
                AliBrainResult(
                    replyText = "जी बॉस, अगला म्यूजिक ट्रैक लगा रहा हूँ।",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.PLAY_MUSIC, payload = "next")),
                    suggestions = listOf("पिछला गाना", "म्यूजिक बंद करो")
                )
            }
            isPrevious -> {
                AliBrainResult(
                    replyText = "जी बॉस, पिछला म्यूजिक ट्रैक लगा रहा हूँ।",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.PLAY_MUSIC, payload = "previous")),
                    suggestions = listOf("अगला गाना लगाओ", "म्यूजिक बंद करो")
                )
            }
            wantsPlay -> {
                AliBrainResult(
                    replyText = "जी बॉस, लॉक-स्क्रीन सेफ बैकग्राउंड म्यूजिक चला रहा हूँ। स्क्रीन बंद होने पर भी यह चलता रहेगा!",
                    spokenLanguage = AssistantLanguage.HINDI,
                    actions = listOf(DeviceAction(ActionType.PLAY_MUSIC, target = clean, payload = "play")),
                    suggestions = listOf("अगला गाना लगाओ", "म्यूजिक बंद करो", "Volume 70 करो")
                )
            }
            else -> null
        }
    }

    internal fun routeSocialMediaClause(
        clean: String,
        lower: String,
        isDeviceLocked: Boolean,
        ownerName: String = ""
    ): AliBrainResult? {
        val mentionsInstagram = lower.contains("instagram") || lower.contains("इंस्टाग्राम") ||
            Regex("\\binsta\\b").containsMatchIn(lower) || lower.contains("इंस्टा")
        val mentionsFacebook = lower.contains("facebook") || lower.contains("फेसबुक") ||
            Regex("\\bfb\\b").containsMatchIn(lower) || lower.contains("एफबी")
        val mentionsWhatsApp = lower.contains("whatsapp") || lower.contains("व्हाट्सएप") ||
            lower.contains("व्हाट्सऐप")
        val mentionsSocialStudio = lower.contains("social media") || lower.contains("सोशल मीडिया") ||
            lower.contains("social poster") || lower.contains("auto post") || lower.contains("autopost")

        val hasStoryWord = lower.contains("story") || lower.contains("स्टोरी") ||
            lower.contains("status") || lower.contains("स्टेटस")
        val hasVideoPostWord = lower.contains("reel") || lower.contains("रील") ||
            lower.contains("video post") || lower.contains("वीडियो पोस्ट") ||
            ((lower.contains("video") || lower.contains("वीडियो")) &&
                (lower.contains("post") || lower.contains("upload") || lower.contains("share") ||
                    lower.contains("पोस्ट") || lower.contains("अपलोड") || lower.contains("शेयर") ||
                    lower.contains("डाल") || lower.contains("लगा") || lower.contains("dal") || lower.contains("lagao")))
        val hasPostActionWord = hasStoryWord || hasVideoPostWord ||
            lower.contains("पोस्ट") || lower.contains("अपलोड") ||
            Regex("\\b(post|upload)\\b").containsMatchIn(lower)

        val anyPlatform = mentionsInstagram || mentionsFacebook || mentionsWhatsApp
        if (!mentionsSocialStudio && !(anyPlatform && hasPostActionWord) && !(hasStoryWord && (lower.contains("डाल") || lower.contains("लगा") || lower.contains("post") || lower.contains("upload")))) {
            return null
        }

        // Ignore plain YouTube search or WhatsApp text message queries without story/video/status
        if (lower.contains("youtube") || lower.contains("यूट्यूब")) return null
        if (mentionsWhatsApp && !mentionsInstagram && !mentionsFacebook && !hasStoryWord && !hasVideoPostWord &&
            (lower.contains("message") || lower.contains("मैसेज") || lower.contains("कॉल") || lower.contains("call"))
        ) {
            return null
        }

        val platforms = buildList {
            if (mentionsInstagram) add(SocialPlatform.INSTAGRAM)
            if (mentionsFacebook) add(SocialPlatform.FACEBOOK)
            if (mentionsWhatsApp) add(SocialPlatform.WHATSAPP)
        }.ifEmpty { SocialPlatform.entries }

        val postType = if (hasVideoPostWord && !hasStoryWord) {
            SocialPostType.VIDEO_POST
        } else {
            SocialPostType.STORY
        }

        val captionHint = Regex("(?i)(?:caption|कैप्शन)\\s*[:\\-]?\\s*(.+)$")
            .find(clean)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val (target, payload) = SocialMediaEngine.encodeSocialSpec(platforms, postType, captionHint)
        val platformNames = platforms.joinToString(", ") { it.hindiName }
        val bossLabel = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"

        if (isDeviceLocked) {
            return AliBrainResult(
                replyText = "$bossLabel, फ़ोन लॉक है — $platformNames पर ${postType.hindiLabel} डालने के लिए कृपया फ़ोन अनलॉक करें।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.SOCIAL_AUTO_POST, target = target, payload = payload)),
                suggestions = listOf("बैटरी बताओ", "गाना बजाओ")
            )
        }

        return AliBrainResult(
            replyText = "जी $bossLabel, $platformNames पर ${postType.hindiLabel} अपने आप पोस्ट करने के लिए LEON Social Auto-Poster खोल रहा हूँ — बस वीडियो/फोटो चुनिए!",
            spokenLanguage = AssistantLanguage.HINDI,
            actions = listOf(DeviceAction(ActionType.SOCIAL_AUTO_POST, target = target, payload = payload)),
            suggestions = listOf("इंस्टाग्राम पर वीडियो पोस्ट करो", "व्हाट्सएप स्टेटस डालो")
        )
    }

    internal fun routeLifeCompassClause(
        clean: String,
        lower: String,
        isDeviceLocked: Boolean,
        ownerName: String = ""
    ): AliBrainResult? {
        val asksCompass = lower.contains("life compass") ||
            lower.contains("sahi raah") ||
            lower.contains("sahi rasta") ||
            lower.contains("सही राह") ||
            lower.contains("सही रास्ता") ||
            lower.contains("इंसान की तरह सोच") ||
            lower.contains("इंसान के जैसे सोच") ||
            lower.contains("अपने से सोच") ||
            lower.contains("सोच समझकर बता") ||
            lower.contains("wise mentor") ||
            lower.contains("right path")

        if (!asksCompass) return null

        val bossLabel = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
        val extractedDilemma = clean
            .replace(
                Regex("(?i)(?:life\\s+compass|sahi\\s+raah|sahi\\s+rasta|right\\s+path|wise\\s+mentor|सही\\s+राह\\s*(?:कम्पास)?|सही\\s+रास्ता|इंसान\\s+(?:की\\s+तरह|के\\s+जैसे)\\s+सोच(?:ने|कर|ो)?|अपने\\s+से\\s+सोच(?:\\s*समझकर)?|खोलो|खोलिए|दिखाओ|बताओ|open|show|kholo)[:\\-]?"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', '!', '?', '।')

        if (isDeviceLocked) {
            return AliBrainResult(
                replyText = "$bossLabel, फ़ोन लॉक है — सही राह कम्पास स्क्रीन खोलने के लिए अनलॉक करें। आप चाहें तो बोलकर पूछ सकते हैं, मैं अभी भी सोचकर सही राह बता सकता हूँ।",
                spokenLanguage = AssistantLanguage.HINDI,
                actions = listOf(DeviceAction(ActionType.OPEN_LIFE_COMPASS, target = extractedDilemma)),
                suggestions = listOf("बैटरी बताओ", "गाना बजाओ")
            )
        }

        val reply = if (extractedDilemma.length >= 6) {
            "जी $bossLabel, मैंने आपकी बात को इंसान की तरह गहराई से समझा है — चलिए सही राह कम्पास में इसका पूरा विश्लेषण और सबसे सही कदम देखते हैं।"
        } else {
            "जी $bossLabel, मैं अपनी पूरी समझ और विवेक से आपके हालात देखकर सही राह बताने के लिए तैयार हूँ — चलिए सही राह कम्पास खोलते हैं।"
        }
        return AliBrainResult(
            replyText = reply,
            spokenLanguage = AssistantLanguage.HINDI,
            actions = listOf(DeviceAction(ActionType.OPEN_LIFE_COMPASS, target = extractedDilemma)),
            suggestions = listOf("मेरे लिए सही रास्ता सोचो", "मेरी यादें बताओ")
        )
    }

    internal fun routeCallAssistantClause(
        @Suppress("UNUSED_PARAMETER") clean: String,
        lower: String,
        isDeviceLocked: Boolean,
        ownerName: String = ""
    ): AliBrainResult? {
        val mentionsIncomingOrReceive = lower.contains("रिसीव") ||
            lower.contains("receive") ||
            lower.contains("न उठा") ||
            lower.contains("ना उठा") ||
            lower.contains("नहीं उठा") ||
            lower.contains("not pick") ||
            lower.contains("unanswered") ||
            lower.contains("receptionist") ||
            lower.contains("रिसेप्शनिस्ट") ||
            lower.contains("auto answer") ||
            lower.contains("incoming call") ||
            ((lower.contains("कॉल") || lower.contains("call")) &&
                (lower.contains("बात कर") || lower.contains("बात करे") || lower.contains("उठा") ||
                    lower.contains("assistant") || lower.contains("screen") || lower.contains("ai handle")))

        if (!mentionsIncomingOrReceive) return null

        val bossLabel = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
        val reply = if (isDeviceLocked) {
            "$bossLabel, फ़ोन लॉक है — AI Call Receptionist सेटिंग खोलने के लिए फ़ोन अनलॉक करें। चालू करने के बाद यह अनअटेंडेड कॉल्स पर अपने आप बात करेगा।"
        } else {
            "जी $bossLabel, LEON AI Call Receptionist खोल रहा हूँ — अब जब आप कोई कॉल रिसीव नहीं कर पाएँगे, तो मैं विनम्रता से कॉलर से बात करके पूरी रिपोर्ट सेव कर लूँगा!"
        }
        return AliBrainResult(
            replyText = reply,
            spokenLanguage = AssistantLanguage.HINDI,
            actions = listOf(DeviceAction(ActionType.OPEN_CALL_ASSISTANT)),
            suggestions = listOf("मेरी यादें बताओ", "सही राह कम्पास खोलो")
        )
    }
}
