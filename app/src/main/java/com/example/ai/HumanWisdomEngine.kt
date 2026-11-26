package com.example.ai

import com.example.data.ActionType
import com.example.data.AliBrainResult
import com.example.data.AssistantLanguage
import com.example.data.CapturedNotification
import com.example.data.DailyTask
import com.example.data.DeviceAction
import com.example.data.DeviceTelemetry
import com.example.data.LifeCompassAnalysis
import com.example.data.MemoryItem
import com.example.data.ProactiveLifeGuidance
import java.util.Calendar
import java.util.Locale

/**
 * Human-like empathy, ethical reflection, and proactive right-path guidance engine.
 *
 * Works both autonomously on-device (observing time of day, pending responsibilities,
 * saved life goals/memories, and stress load) and as an empathetic offline fallback
 * whenever cloud AI is unreachable.
 */
object HumanWisdomEngine {

    private enum class DilemmaTheme {
        ANGER_OR_IMPULSE,
        STRESS_OR_SADNESS,
        CAREER_OR_STUDY,
        RELATIONSHIP_OR_FAMILY,
        HABIT_OR_DISCIPLINE,
        ETHICAL_DILEMMA,
        GENERAL_CLARITY
    }

    fun buildProactiveGuidance(
        ownerName: String,
        tasks: List<DailyTask>,
        memories: List<MemoryItem>,
        notifications: List<CapturedNotification>,
        telemetry: DeviceTelemetry,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): ProactiveLifeGuidance {
        val boss = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
        val pending = tasks.filter { !it.completed }
        val highPriorityPending = pending.filter { it.priority.equals("high", ignoreCase = true) }
        val topTask = highPriorityPending.firstOrNull() ?: pending.firstOrNull()
        val goalMemory = memories.firstOrNull { mem ->
            val blob = "${mem.memoryKey} ${mem.memoryValue}".lowercase(Locale.ROOT)
            blob.contains("goal") || blob.contains("लक्ष्य") || blob.contains("सपना") ||
                blob.contains("dream") || blob.contains("exam") || blob.contains("job") ||
                blob.contains("काम") || blob.contains("business")
        }

        // 1. Late-night well-being check (23:00 - 04:29)
        if (hourOfDay >= 23 || hourOfDay < 4) {
            val taskNote = if (topTask != null) {
                "कल सुबह के लिए '${topTask.title}' पहले से सुरक्षित है — अभी दिमाग़ को आराम देना ज़्यादा ज़रूरी है।"
            } else {
                "कल नई ऊर्जा के साथ शुरुआत करने के लिए आज की नींद पूरी करना सबसे सही फ़ैसला है।"
            }
            return ProactiveLifeGuidance(
                headline = "$boss, अब आराम और नींद को प्राथमिकता देने का समय है",
                observation = "रात के ${hourOfDay}:00 बज रहे हैं और ${pending.size} काम पेंडिंग हैं। देर रात थकान में लिए गए फ़ैसले अक्सर तनाव बढ़ाते हैं।",
                rightPathNudge = "सही राह यही है कि अभी मोबाइल स्क्रीन से दूरी बनाकर 7 घंटे की गहरी नींद लें। $taskNote",
                recommendedStep = "आज के सभी विचार शांत करके सोने की तैयारी करें और कल सुबह का अलार्म लगाएँ",
                suggestedFocusMinutes = 10,
                toneBadge = "Care & Rest",
                fromCloudAi = false
            )
        }

        // 2. Low battery / distraction overload check
        if (telemetry.batteryPercent in 1..18 && !telemetry.charging) {
            return ProactiveLifeGuidance(
                headline = "$boss, थोड़ा रुककर फ़ोन और खुद दोनों को रिचार्ज करें",
                observation = "फ़ोन की बैटरी सिर्फ़ ${telemetry.batteryPercent}% बची है और ${notifications.size} नोटिफिकेशन ध्यान भटका रहे हैं।",
                rightPathNudge = "सही राह: जब बाहर की चीज़ें जल्दी खत्म हो रही हों, तो पहले स्थिर रहें — फ़ोन चार्ज पर लगाएँ और 15 मिनट बिना स्क्रीन के शांत बैठें।",
                recommendedStep = topTask?.let { "बैटरी चार्ज होते ही सबसे ज़रूरी काम '${it.title}' पूरा करें" }
                    ?: "15 मिनट डिजिटल ब्रेक लेकर दिन का सबसे महत्वपूर्ण काम तय करें",
                suggestedFocusMinutes = 15,
                toneBadge = "Mindful Pause",
                fromCloudAi = false
            )
        }

        // 3. Pending high-priority tasks or heavy task backlog
        if (highPriorityPending.isNotEmpty() || pending.size >= 4) {
            val focusTarget = topTask?.title ?: "सबसे ज़रूरी काम"
            val goalAnchor = goalMemory?.let { " (याद रखें, आपका बड़ा लक्ष्य: ${it.memoryValue})" }.orEmpty()
            return ProactiveLifeGuidance(
                headline = "$boss, एक साथ सब नहीं — पहले सबसे सही एक कदम उठाएँ",
                observation = "आपके पास अभी ${pending.size} अधूरे काम हैं${if (highPriorityPending.isNotEmpty()) " (जिनमें ${highPriorityPending.size} हाई-प्रायोरिटी हैं)" else ""}। एक साथ कई चीज़ें सोचने से दिमाग़ थकता है।",
                rightPathNudge = "सही राह: परफेक्शन का इंतज़ार छोड़कर अभी सिर्फ़ '$focusTarget' पर 25 मिनट पूरा ध्यान लगाएँ$goalAnchor।",
                recommendedStep = "$focusTarget — अभी बिना भटकाव के 25 मिनट शुरू करें",
                suggestedFocusMinutes = 25,
                toneBadge = "Deep Focus",
                fromCloudAi = false
            )
        }

        // 4. Morning clarity (05:00 - 11:29)
        if (hourOfDay in 5..11) {
            val goalHint = goalMemory?.let { "आपके लक्ष्य '${it.memoryKey}' के लिए आज एक छोटा ठोस कदम काफी है।" }
                ?: "आज कम से कम 1 ऐसा काम चुनें जो आपके भविष्य को मज़बूत बनाए।"
            return ProactiveLifeGuidance(
                headline = "सुप्रभात $boss! आज की शुरुआत स्पष्ट और शांत मन से करें",
                observation = if (topTask != null) {
                    "सुबह का समय सबसे ताज़ा होता है और आज का पहला काम '${topTask.title}' आपका इंतज़ार कर रहा है।"
                } else {
                    "आज आपकी टास्क लिस्ट खाली और साफ़ है — यह किसी बड़े लक्ष्य पर काम शुरू करने का सुनहरा मौका है।"
                },
                rightPathNudge = "सही राह: दिन की शुरुआत दूसरों की भागदौड़ या सोशल मीडिया से नहीं, बल्कि अपने खुद के संकल्प से करें। $goalHint",
                recommendedStep = topTask?.title ?: "आज का सबसे महत्वपूर्ण लक्ष्य तय कर के 25 मिनट काम करें",
                suggestedFocusMinutes = 25,
                toneBadge = "Morning Compass",
                fromCloudAi = false
            )
        }

        // 5. General steady evening / afternoon reflection
        val notifNote = if (notifications.size >= 5) {
            "अभी ${notifications.size} नोटिफिकेशन जमा हैं — पर हर आवाज़ पर तुरंत प्रतिक्रिया देना ज़रूरी नहीं।"
        } else {
            "अभी आपका माहौल शांत और संतुलित दिख रहा है।"
        }
        return ProactiveLifeGuidance(
            headline = "$boss, धैर्य और सही दिशा ही असली ताक़त है",
            observation = notifNote,
            rightPathNudge = "सही राह: जो बात आपके हाथ में नहीं है उसकी चिंता छोड़कर, आज अपने कौशल, सेहत और रिश्तों को बेहतर बनाने पर ध्यान दें।",
            recommendedStep = topTask?.let { "आज '${it.title}' को शांति से पूरा करें" }
                ?: "अपने लिए 20 मिनट सीखने, टहलने या परिवार से बात करने के लिए निकालें",
            suggestedFocusMinutes = 20,
            toneBadge = "Balanced Wisdom",
            fromCloudAi = false
        )
    }

    fun analyzeUserSituationOffline(
        query: String,
        ownerName: String = "",
        tasks: List<DailyTask> = emptyList(),
        memories: List<MemoryItem> = emptyList(),
        language: AssistantLanguage = AssistantLanguage.HINDI
    ): LifeCompassAnalysis {
        val clean = query.trim()
        val lower = clean.lowercase(Locale.ROOT)
        val boss = if (ownerName.isNotBlank()) "$ownerName बॉस" else "दोस्त"
        val theme = classifyDilemma(lower)

        if (language == AssistantLanguage.ENGLISH) {
            return buildEnglishOfflineAnalysis(clean, boss, theme, tasks, memories)
        }

        val personalContextNote = memories.firstOrNull()?.let {
            " आपकी याद की गई बात (${it.memoryKey}) को ध्यान में रखते हुए भी यही संतुलन सही बैठता है।"
        }.orEmpty()

        return when (theme) {
            DilemmaTheme.ANGER_OR_IMPULSE -> LifeCompassAnalysis(
                query = clean,
                emotionalTone = "गुस्सा / जल्दबाज़ी (Impulse)",
                emotionalUnderstanding = "$boss, मैं समझ सकता हूँ कि इस वक्त आपका मन आहत या क्रोधित है। जब कोई बात दिल पर लगती है, तो तुरंत जवाब देने का मन होना बिल्कुल मानवीय है।",
                prosConsAnalysis = "• अभी गुस्से में कदम उठाने पर: पल भर की भड़ास तो निकल सकती है, लेकिन बाद में रिश्ते, सम्मान या काम को ऐसा नुकसान हो सकता है जिसे सुधारना कठिन हो।\n• 24 घंटे रुककर सोचने पर: आप स्थिति को साफ देख पाएंगे और मज़बूती के साथ शालीन फ़ैसला ले पाएंगे।",
                rightPathDecision = "सही राह: अभी गुस्से या आवेश में कोई मैसेज, कॉल या बड़ा फ़ैसला न लें। कम से कम 1 घंटा (या आज की रात) रुकें, पानी पिएँ और शांत मन से अपनी बात तथ्य के साथ रखें।$personalContextNote",
                actionableSteps = listOf(
                    "अभी फ़ोन रखकर 10 गहरी साँसें लें और तुरंत प्रतिक्रिया रोकें",
                    "जो कहना चाहते हैं उसे पहले नोट्स में लिखें, भेजें नहीं",
                    "मन शांत होने पर सम्मान और स्पष्टता के साथ बात सुलझाएँ"
                ),
                gentleWarning = "क्रोध में बोले गए शब्द वापस नहीं आते — अपनी शांति को किसी दूसरे के व्यवहार के हवाले न करें।",
                spokenSummary = "$boss, अभी दिल थोड़ा भारी या गुस्से में है, इसलिए अभी कोई भी तुरंत फ़ैसला या मैसेज न करें। थोड़ा शांत होकर सोचें — धैर्य और सम्मान के साथ बात रखना ही अभी सबसे सही राह है।",
                fromCloudAi = false
            )

            DilemmaTheme.STRESS_OR_SADNESS -> LifeCompassAnalysis(
                query = clean,
                emotionalTone = "तनाव / उदासी (Emotional Weight)",
                emotionalUnderstanding = "$boss, आप अंदर से थकान, चिंता या अकेलापन महसूस कर रहे हैं — और हर इंसान की ज़िंदगी में ऐसा दौर आता है। खुद पर सख्ती करने की कोई ज़रूरत नहीं है।",
                prosConsAnalysis = "• सब कुछ अकेले बोझ की तरह ढोने पर: थकान और निराशा बढ़ती है, छोटे काम भी पहाड़ लगने लगते हैं।\n• मन की बात बाँटने और छोटा कदम लेने पर: दिमाग़ का बोझ हल्का होता है और रास्ता फिर से साफ़ दिखने लगता है।",
                rightPathDecision = "सही राह: आज पूरी ज़िंदगी की समस्या एक साथ हल करने की कोशिश छोड़ दें। अभी सिर्फ़ अगले 1 घंटे का ख्याल रखें — थोड़ा टहलें, किसी भरोसेमंद इंसान से बात करें, और सिर्फ़ एक छोटा अच्छा काम पूरा करें।$personalContextNote",
                actionableSteps = listOf(
                    "10 मिनट खुली हवा में टहलें और गिलास भर पानी पिएँ",
                    "अपने किसी करीबी दोस्त या परिवार के सदस्य से दिल की बात साझा करें",
                    "सिर्फ़ एक छोटा 15-मिनट का आसान काम चुनकर पूरा करें"
                ),
                gentleWarning = "बुरे दिन हमेशा नहीं रहते — उदासी के वक्त खुद के बारे में कभी कठोर निष्कर्ष न निकालें।",
                spokenSummary = "$boss, परेशान मत होइए — हर कठिन दौर गुजर जाता है। अभी बड़े नतीजों की चिंता छोड़कर बस थोड़ा आराम करें, किसी अपने से बात करें और आज के लिए बस एक छोटा कदम उठाएँ।",
                fromCloudAi = false
            )

            DilemmaTheme.CAREER_OR_STUDY -> LifeCompassAnalysis(
                query = clean,
                emotionalTone = "करियर / भविष्य की उलझन",
                emotionalUnderstanding = "$boss, करियर, पढ़ाई या पैसों का फ़ैसला लेते समय असमंजस होना स्वाभाविक है, क्योंकि इसमें आपके भविष्य और मेहनत दोनों का सवाल जुड़ा है।",
                prosConsAnalysis = "• भावनाओं या दूसरों की देखा-देखी में निर्णय लेने पर: अस्थायी चमक के पीछे लंबे समय की स्थिरता छूट सकती है।\n• तैयारी और विकल्प सुरक्षित रखकर कदम बढ़ाने पर: जोखिम कम रहता है और आत्मविश्वास लगातार बढ़ता है।",
                rightPathDecision = "सही राह: बिना बैकअप या ठोस योजना के अचानक सब कुछ न छोड़ें। पहले अगले 30 दिनों तक अपनी स्किल, बचत और विकल्पों का कागज़ पर आकलन करें, फिर अनुशासित कदम आगे बढ़ाएँ।$personalContextNote",
                actionableSteps = listOf(
                    "दोनों विकल्पों के फायदे, नुकसान और अगले 6 महीने का खर्च/समय लिखें",
                    "इस क्षेत्र के किसी अनुभवी व्यक्ति से 15 मिनट की ईमानदार सलाह लें",
                    "रोज़ 45 मिनट अपनी मुख्य स्किल को बेहतर बनाने का पक्का समय तय करें"
                ),
                gentleWarning = "शॉर्टकट या जल्दी अमीर/सफल बनने के लालच से बचें — टिकाऊ सफलता लगातार सीखने से ही आती है।",
                spokenSummary = "$boss, करियर या पैसों के मामले में जल्दबाज़ी के बजाय तैयारी के साथ आगे बढ़ना सही राह है। पहले बैकअप और अगले 6 महीने का प्लान साफ़ करें, फिर आत्मविश्वास से कदम उठाएँ।",
                fromCloudAi = false
            )

            DilemmaTheme.RELATIONSHIP_OR_FAMILY -> LifeCompassAnalysis(
                query = clean,
                emotionalTone = "रिश्ते / परिवार की संवेदनशीलता",
                emotionalUnderstanding = "$boss, रिश्तों में जब गलतफहमी या दूरी आती है, तो दिमाग़ से ज़्यादा दिल उलझता है। आप दोनों तरफ़ सही संतुलन चाहते हैं।",
                prosConsAnalysis = "• आरोप-प्रत्यारोप या चुप रहकर दूरी बढ़ाने पर: गलतफहमियाँ और गहरी होती जाती हैं।\n• शांत होकर सामने वाले की बात भी सुनने पर: अहंकार पिघलता है और रिश्ते की सच्चाई साफ़ दिखती है।",
                rightPathDecision = "सही राह: जीतने की नहीं, बात समझने और सुलझाने की कोशिश करें। अपनी बात 'आपने ऐसा किया' की जगह 'मुझे ऐसा महसूस हुआ' कहकर विनम्रता से रखें — पर अपने आत्मसम्मान की सीमा भी स्पष्ट रखें।$personalContextNote",
                actionableSteps = listOf(
                    "शिकायत के बजाय शांत संवाद के लिए आमने-सामने या कॉल का समय तय करें",
                    "पहले सामने वाले की पूरी बात बिना टोके ध्यान से सुनें",
                    "सम्मानजनक सीमा और उम्मीद स्पष्ट शब्दों में बताएँ"
                ),
                gentleWarning = "अहंकार कुछ पल जीत दिला सकता है, पर लंबे समय में केवल विनम्रता और ईमानदारी ही रिश्ते बचाती है।",
                spokenSummary = "$boss, रिश्तों में बहस जीतने से ज़्यादा ज़रूरी एक-दूसरे को समझना है। शांत समय चुनकर बिना अहंकार अपनी भावना ईमानदारी से रखें, यही सबसे सही राह है।",
                fromCloudAi = false
            )

            DilemmaTheme.HABIT_OR_DISCIPLINE -> LifeCompassAnalysis(
                query = clean,
                emotionalTone = "आदत और अनुशासन सुधार",
                emotionalUnderstanding = "$boss, यह बहुत अच्छी बात है कि आप अपनी आदतों और समय को लेकर ईमानदारी से सोच रहे हैं। खुद की कमज़ोरी को पहचान लेना ही सुधार का आधा रास्ता है।",
                prosConsAnalysis = "• कल पर टालने या अचानक बहुत कठिन नियम बनाने पर: 2-3 दिन में जोश ठंडा पड़ जाता है और अपराधबोध होता है।\n• छोटे लेकिन रोज़ के नियम बनाने पर: दिमाग़ विरोध नहीं करता और आदत स्थायी बनती है।",
                rightPathDecision = "सही राह: बड़े बदलाव के बजाय 15-मिनट का 'न टूटने वाला नियम' आज से शुरू करें। जो आदत रोकनी है, उसके ट्रिगर (जैसे रात को मोबाइल पास रखना) को माहौल से हटा दें।$personalContextNote",
                actionableSteps = listOf(
                    "आज ध्यान भटकाने वाली चीज़ (मोबाइल/ऐप) को काम के समय दूसरे कमरे में रखें",
                    "सिर्फ़ 15 मिनट की छोटी आदत से आज ही शुरुआत करें",
                    "आज रात सोने से पहले अपनी प्रगति ईमानदारी से जांचें"
                ),
                gentleWarning = "मूड बनने का इंतज़ार न करें — काम शुरू करने के 5 मिनट बाद प्रेरणा अपने आप आती है।",
                spokenSummary = "$boss, बड़ी शुरुआत के बजाय आज से 15 मिनट का छोटा पक्का नियम बनाइए और भटकाव दूर रखिए — रोज़ की छोटी निरंतरता ही आपको मंज़िल तक ले जाएगी।",
                fromCloudAi = false
            )

            DilemmaTheme.ETHICAL_DILEMMA -> LifeCompassAnalysis(
                query = clean,
                emotionalTone = "नैतिकता / सही-गलत का चुनाव",
                emotionalUnderstanding = "$boss, जब आसान गलत रास्ता और कठिन सही रास्ता सामने हों, तो मन डगमगाना स्वाभाविक है — पर आपका अंतर्मन पहले ही सच जानता है।",
                prosConsAnalysis = "• गलत या अनैतिक शॉर्टकट चुनने पर: तुरंत फायदा दिख सकता है, लेकिन भीतर का डर, अपराधबोध और भविष्य का बड़ा नुकसान पीछा नहीं छोड़ता।\n• ईमानदार और सही रास्ता चुनने पर: शुरुआत थोड़ी कठिन हो सकती है, पर चैन की नींद और स्थायी सम्मान मिलता है।",
                rightPathDecision = "सही राह: ऐसा कोई काम न करें जिसे आपको अपनों या अपनी ही नज़रों से छिपाना पड़े। सच्चाई, ईमानदारी और किसी को नुकसान न पहुँचाने का रास्ता ही अंत में जीतता है।$personalContextNote",
                actionableSteps = listOf(
                    "खुद से पूछें: क्या 1 साल बाद मुझे इस फ़ैसले पर गर्व होगा?",
                    "जो गलत या अन्यायपूर्ण लग रहा है, उससे आज ही स्पष्ट दूरी बनाएँ",
                    "कठिन होने पर भी ईमानदार और पारदर्शी विकल्प चुनें"
                ),
                gentleWarning = "थोड़े से लाभ के लिए अपनी ज़मीर और साख का सौदा कभी न करें।",
                spokenSummary = "$boss, सही राह हमेशा वही है जिसमें आप निडर होकर सिर ऊँचा रख सकें। अस्थायी फायदे के लिए कभी गलत या अनैतिक रास्ता न चुनें — ईमानदारी ही सबसे बड़ी सुरक्षा है।",
                fromCloudAi = false
            )

            DilemmaTheme.GENERAL_CLARITY -> LifeCompassAnalysis(
                query = clean,
                emotionalTone = "स्पष्ट सोच व मार्गदर्शन",
                emotionalUnderstanding = "$boss, आपने रुककर सोचने और सही दिशा खोजने का फ़ैसला किया — यही एक समझदार इंसान की निशानी है।",
                prosConsAnalysis = "• बिना सोचे भीड़ के पीछे चलने पर: ऊर्जा तो खर्च होती है, पर मन को संतोष नहीं मिलता।\n• अपने मूल्यों और लंबे लक्ष्य के हिसाब से चुनने पर: जीवन में शांति, स्पष्टता और गति तीनों आते हैं।",
                rightPathDecision = "सही राह: अभी सबसे ज़्यादा ज़रूरी चीज़ को सबसे आगे रखें — सच बोलें, सेहत संभालें, और अपने अगले एक ठोस कदम पर पूरा ध्यान लगाएँ।$personalContextNote",
                actionableSteps = listOf(
                    "अपनी मुख्य समस्या को एक साफ़ वाक्य में लिखकर 2 विकल्प तय करें",
                    "दोनों के 1 साल बाद के असर का शांत मन से आकलन करें",
                    tasks.firstOrNull { !it.completed }?.let { "अपना अधूरा काम '${it.title}' पूरा करके गति बनाएँ" }
                        ?: "आज एक छोटा सकारात्मक कदम अभी उठाएँ"
                ),
                gentleWarning = "अति-चिंतन (Overthinking) से सिर्फ़ उलझन बढ़ती है — स्पष्ट सोच के बाद छोटा एक्शन लेना सबसे ज़रूरी है।",
                spokenSummary = "$boss, मैंने आपकी बात गहराई से समझी है। अभी ज़्यादा उलझने के बजाय शांत मन से लंबे समय के असर को देखें और आज का एक सही, छोटा कदम उठाएँ।",
                fromCloudAi = false
            )
        }
    }

    fun tryBuildOfflineHumanGuidanceResult(
        rawPrompt: String,
        defaultLanguage: AssistantLanguage,
        ownerName: String,
        tasks: List<DailyTask>,
        memories: List<MemoryItem>
    ): AliBrainResult? {
        val lower = rawPrompt.trim().lowercase(Locale.ROOT)
        val isHumanAdvicePrompt =
            lower.contains("सही राह") || lower.contains("सही रास्ता") || lower.contains("कन्फ्यूज") ||
                lower.contains("कंफ्यूज") || lower.contains("confus") || lower.contains("परेशान") ||
                lower.contains("टेंशन") || lower.contains("tension") || lower.contains("stress") ||
                lower.contains("तनाव") || lower.contains("उदास") || lower.contains("गुस्सा") ||
                lower.contains("angry") || lower.contains("क्या करूँ") || lower.contains("क्या करूं") ||
                lower.contains("kya karu") || lower.contains("what should i do") ||
                lower.contains("रास्ता बताओ") || lower.contains("मदद करो सोचने") ||
                lower.contains("गलत") || lower.contains("धोखा") || lower.contains("डिसीजन") ||
                lower.contains("decision") || lower.contains("advice") || lower.contains("सलाह")

        if (!isHumanAdvicePrompt) return null

        val analysis = analyzeUserSituationOffline(
            query = rawPrompt,
            ownerName = ownerName,
            tasks = tasks,
            memories = memories,
            language = defaultLanguage
        )
        return AliBrainResult(
            replyText = "${analysis.spokenSummary}\n\n• ${analysis.rightPathDecision}\n• अगला कदम: ${analysis.actionableSteps.firstOrNull().orEmpty()}",
            spokenLanguage = defaultLanguage,
            actions = listOf(DeviceAction(ActionType.SPEAK_ONLY)),
            suggestions = listOf("सही राह कम्पास खोलो", "मेरे लिए सही रास्ता सोचो", "25 मिनट फोकस टाइमर लगाओ"),
            emotionalTone = analysis.emotionalTone,
            innerThought = analysis.emotionalUnderstanding,
            rightPathAdvice = analysis.rightPathDecision
        )
    }

    private fun buildEnglishOfflineAnalysis(
        clean: String,
        boss: String,
        theme: DilemmaTheme,
        tasks: List<DailyTask>,
        memories: List<MemoryItem>
    ): LifeCompassAnalysis {
        val label = when (theme) {
            DilemmaTheme.ANGER_OR_IMPULSE -> "Patience Over Impulse"
            DilemmaTheme.STRESS_OR_SADNESS -> "Emotional Relief & Grounding"
            DilemmaTheme.CAREER_OR_STUDY -> "Career & Long-Term Growth"
            DilemmaTheme.RELATIONSHIP_OR_FAMILY -> "Empathy & Healthy Boundaries"
            DilemmaTheme.HABIT_OR_DISCIPLINE -> "Small Daily Discipline"
            DilemmaTheme.ETHICAL_DILEMMA -> "Integrity & Conscience"
            DilemmaTheme.GENERAL_CLARITY -> "Clear Mindful Direction"
        }
        val memNote = memories.firstOrNull()?.let { " Keeping your goal (${it.memoryKey}) in mind," }.orEmpty()
        return LifeCompassAnalysis(
            query = clean,
            emotionalTone = label,
            emotionalUnderstanding = "$boss, I hear the weight behind what you're navigating. Taking a breath to think carefully already puts you ahead of impulsive choices.",
            prosConsAnalysis = "• Reacting fast on emotion: brings short-term release but risks regret or strained trust later.\n• Choosing steady principle: feels slower today, yet protects your peace, dignity, and long-term future.",
            rightPathDecision = "Right Path:$memNote pause before making any irreversible move, choose integrity and calm dialogue, and focus on the next constructive step within your control.",
            actionableSteps = listOf(
                "Take 5 minutes away from noise to clear your head before acting",
                "Write down the honest long-term impact 6 months from now",
                tasks.firstOrNull { !it.completed }?.let { "Build momentum by completing '${it.title}'" }
                    ?: "Take one small disciplined step forward today"
            ),
            gentleWarning = "Never trade long-term peace of mind for temporary relief or ego.",
            spokenSummary = "$boss, pause and protect your peace first. Think about where each option leads six months from now, and choose the honest, calm step you can stand behind proudly.",
            fromCloudAi = false
        )
    }

    private fun classifyDilemma(lower: String): DilemmaTheme {
        return when {
            lower.contains("गुस्सा") || lower.contains("क्रोध") || lower.contains("angry") ||
                lower.contains("anger") || lower.contains("बदला") || lower.contains("revenge") ||
                lower.contains("लड़ाई") || lower.contains("झगड़ा") || lower.contains("चिल्ला") ->
                DilemmaTheme.ANGER_OR_IMPULSE

            lower.contains("तनाव") || lower.contains("परेशान") || lower.contains("उदास") ||
                lower.contains("दुखी") || lower.contains("अकेला") || lower.contains("डर") ||
                lower.contains("stress") || lower.contains("anx") || lower.contains("depress") ||
                lower.contains("sad") || lower.contains("worried") || lower.contains("टेंशन") ->
                DilemmaTheme.STRESS_OR_SADNESS

            lower.contains("करियर") || lower.contains("नौकरी") || lower.contains("पढ़ाई") ||
                lower.contains("बिजनेस") || lower.contains("पैसा") || lower.contains("career") ||
                lower.contains("job") || lower.contains("study") || lower.contains("exam") ||
                lower.contains("business") || lower.contains("money") ->
                DilemmaTheme.CAREER_OR_STUDY

            lower.contains("रिश्त") || lower.contains("परिवार") || lower.contains("दोस्त") ||
                lower.contains("माता") || lower.contains("पिता") || lower.contains("प्यार") ||
                lower.contains("relationship") || lower.contains("family") || lower.contains("friend") ||
                lower.contains("marriage") || lower.contains("शादी") ->
                DilemmaTheme.RELATIONSHIP_OR_FAMILY

            lower.contains("आदत") || lower.contains("आलस") || lower.contains("लत") ||
                lower.contains("फोकस") || lower.contains("habit") || lower.contains("lazy") ||
                lower.contains("procrastinat") || lower.contains("focus") || lower.contains("discipline") ||
                lower.contains("addict") || lower.contains("समय बर्बाद") ->
                DilemmaTheme.HABIT_OR_DISCIPLINE

            lower.contains("गलत") || lower.contains("सही") || lower.contains("झूठ") ||
                lower.contains("चोरी") || lower.contains("धोखा") || lower.contains("ईमानदार") ||
                lower.contains("moral") || lower.contains("ethic") || lower.contains("honest") ||
                lower.contains("cheat") || lower.contains("lie") ->
                DilemmaTheme.ETHICAL_DILEMMA

            else -> DilemmaTheme.GENERAL_CLARITY
        }
    }
}
