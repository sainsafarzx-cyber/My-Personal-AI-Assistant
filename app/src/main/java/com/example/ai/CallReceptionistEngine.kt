package com.example.ai

import com.example.data.AssistantLanguage
import com.example.data.BossAvailabilityMode
import com.example.data.CallReceptionistConfig
import com.example.data.CallReceptionistResponse
import com.example.data.CallTranscriptTurn
import com.example.data.HandledCallRecord
import java.util.Locale

/**
 * Intelligent human-like offline & hybrid Call Receptionist dialogue engine for LEON AI.
 * Handles multi-turn Hindi / Hinglish / English conversations with incoming callers when
 * the Boss cannot pick up, extracts caller identity, purpose, and urgency, and crafts a
 * concise report + callback recommendation for the Boss.
 */
object CallReceptionistEngine {

    fun buildInitialGreeting(
        ownerName: String,
        callerDisplay: String,
        config: CallReceptionistConfig,
        language: AssistantLanguage = AssistantLanguage.HINDI
    ): String {
        val bossTitle = ownerName.trim().ifBlank { "बॉस" }
        val callerClean = callerDisplay.trim()
        val hasKnownCaller = callerClean.isNotBlank() &&
            !callerClean.equals("Unknown Caller", ignoreCase = true) &&
            !callerClean.equals("अज्ञात कॉलर", ignoreCase = true) &&
            !callerClean.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }

        val statusHindi = if (config.availabilityMode == BossAvailabilityMode.CUSTOM &&
            config.customBossNote.isNotBlank()
        ) {
            config.customBossNote.trim()
        } else {
            config.availabilityMode.hindiPhrase
        }
        val statusEnglish = if (config.availabilityMode == BossAvailabilityMode.CUSTOM &&
            config.customBossNote.isNotBlank()
        ) {
            config.customBossNote.trim()
        } else {
            config.availabilityMode.englishPhrase
        }

        return when (language) {
            AssistantLanguage.ENGLISH -> {
                val greet = if (hasKnownCaller) "Hello $callerClean, " else "Hello, "
                "${greet}I am LEON AI, $bossTitle's personal assistant. $bossTitle $statusEnglish so I answered the call. May I ask your name and the purpose of your call so I can pass it along?"
            }
            AssistantLanguage.HINGLISH -> {
                val greet = if (hasKnownCaller) "Namaste $callerClean, " else "Namaste, "
                "${greet}main LEON AI bol raha hoon — $bossTitle ka personal assistant. $bossTitle $statusHindi isliye maine call receive kiya hai. Kripya apna naam aur kis zaroori kaam se call kiya hai batayein?"
            }
            AssistantLanguage.HINDI -> {
                val greet = if (hasKnownCaller) "नमस्ते $callerClean जी, " else "नमस्ते, "
                "${greet}मैं LEON AI बोल रहा हूँ — $bossTitle का पर्सनल असिस्टेंट। वे $statusHindi, इसलिए मैंने आपकी कॉल रिसीव की है। कृपया बताइए, आपका शुभ नाम क्या है और किस ज़रूरी काम से कॉल किया है?"
            }
        }
    }

    fun generateOfflineTurn(
        callerDisplay: String,
        callerUtterance: String,
        priorTurns: List<CallTranscriptTurn>,
        ownerName: String,
        config: CallReceptionistConfig,
        language: AssistantLanguage,
        existingCallerName: String = "",
        existingPurpose: String = ""
    ): CallReceptionistResponse {
        val cleanInput = callerUtterance.trim()
        val lower = cleanInput.lowercase(Locale.ROOT)
        val bossTitle = ownerName.trim().ifBlank { "बॉस" }
        val callerTurnsCount = priorTurns.count { it.speaker == "CALLER" } + 1

        val extractedName = extractCallerName(cleanInput)
            .ifBlank { existingCallerName }
            .ifBlank {
                val dc = callerDisplay.trim()
                if (dc.isNotBlank() && !dc.startsWith("+") && !dc.all { it.isDigit() || it == ' ' }) dc else ""
            }

        val urgency = detectUrgency(lower)
        val isUrgent = urgency == "HIGH"
        val isGoodbye = containsGoodbye(lower)
        val isOtpOrVerification = containsAny(lower, "otp", "ओटीपी", "verification", "कोड", "bank pin", "cvv", "password")
        val isSpamOrPromo = containsAny(lower, "loan offer", "credit card", "insurance", "लोन", "क्रेडिट कार्ड", "पर्सनल लोन", "offer")

        val extractedPurpose = when {
            isOtpOrVerification -> "OTP / सिक्योरिटी वेरिफिकेशन कॉल (अस्वीकृत)"
            isSpamOrPromo -> "प्रमोशनल / सेल्स ऑफर कॉल"
            cleanInput.length >= 6 -> summarizePurposeSnippet(cleanInput, existingPurpose)
            else -> existingPurpose.ifBlank { "कॉल पर संदेश देना चाहते हैं" }
        }

        val preferEnglish = language == AssistantLanguage.ENGLISH ||
            (cleanInput.any { it in 'a'..'z' || it in 'A'..'Z' } &&
                !cleanInput.any { it in '\u0900'..'\u097F' } &&
                language != AssistantLanguage.HINDI)

        // Security guard: never handle OTP / password / sensitive financial verification on call
        if (isOtpOrVerification) {
            val reply = if (preferEnglish) {
                "For security reasons, LEON AI cannot verify OTPs or banking details over the phone. $bossTitle will review this personally if genuine. Thank you, goodbye."
            } else {
                "माफ़ कीजिए, सुरक्षा कारणों से मैं कॉल पर कोई OTP या बैंकिंग जानकारी साझा नहीं कर सकता। ज़रूरी होने पर $bossTitle स्वयं संपर्क करेंगे। धन्यवाद।"
            }
            return CallReceptionistResponse(
                replyToCaller = reply,
                capturedCallerName = extractedName.ifBlank { callerDisplay },
                capturedPurpose = extractedPurpose,
                urgency = "LOW",
                shouldEscalateToBoss = false,
                shouldEndCall = true,
                summaryForBoss = "${extractedName.ifBlank { callerDisplay }} ने OTP/वेरिफिकेशन के लिए कॉल किया था — सुरक्षा नीति से कॉल समाप्त की गई।"
            )
        }

        if (isSpamOrPromo) {
            val reply = if (preferEnglish) {
                "Thank you for calling, but $bossTitle is not looking for promotional offers right now. Have a good day."
            } else {
                "जानकारी के लिए धन्यवाद, अभी $bossTitle को किसी प्रमोशनल ऑफर की आवश्यकता नहीं है। आपका दिन शुभ हो।"
            }
            return CallReceptionistResponse(
                replyToCaller = reply,
                capturedCallerName = extractedName.ifBlank { callerDisplay },
                capturedPurpose = extractedPurpose,
                urgency = "LOW",
                shouldEscalateToBoss = false,
                shouldEndCall = true,
                summaryForBoss = "${extractedName.ifBlank { callerDisplay }} की सेल्स/प्रमोशनल कॉल को विनम्रता से मना कर दिया गया।"
            )
        }

        if (isGoodbye) {
            val nameRef = if (extractedName.isNotBlank()) "$extractedName जी" else ""
            val reply = if (preferEnglish) {
                "Thank you${if (extractedName.isNotBlank()) " $extractedName" else ""}. I have noted your message for $bossTitle and they will call you back soon. Goodbye!"
            } else {
                "धन्यवाद $nameRef। मैंने आपका पूरा संदेश $bossTitle के लिए नोट कर लिया है, वे फ्री होते ही आपसे संपर्क करेंगे। नमस्ते!"
            }
            return CallReceptionistResponse(
                replyToCaller = reply,
                capturedCallerName = extractedName.ifBlank { callerDisplay },
                capturedPurpose = extractedPurpose,
                urgency = urgency,
                shouldEscalateToBoss = isUrgent,
                shouldEndCall = true,
                summaryForBoss = buildBossSummary(extractedName.ifBlank { callerDisplay }, extractedPurpose, urgency)
            )
        }

        if (isUrgent) {
            val reply = if (preferEnglish) {
                val ackName = if (extractedName.isNotBlank()) " $extractedName" else ""
                "I understand this is urgent$ackName. I am marking this HIGH PRIORITY and alerting $bossTitle immediately with your message: \"$cleanInput\". Please stay available for a quick callback."
            } else {
                val ackName = if (extractedName.isNotBlank()) "$extractedName जी, " else ""
                "${ackName}मैं समझ गया कि यह बहुत ज़रूरी मामला है। मैं इसे हाई-प्रायोरिटी पर मार्क करके अभी $bossTitle को अलर्ट भेज रहा हूँ। कृपया उपलब्ध रहें, वे तुरंत कॉल करेंगे।"
            }
            return CallReceptionistResponse(
                replyToCaller = reply,
                capturedCallerName = extractedName.ifBlank { callerDisplay },
                capturedPurpose = extractedPurpose,
                urgency = "HIGH",
                shouldEscalateToBoss = true,
                shouldEndCall = callerTurnsCount >= 2,
                summaryForBoss = buildBossSummary(extractedName.ifBlank { callerDisplay }, extractedPurpose, "HIGH")
            )
        }

        // Normal multi-turn conversational flow
        val reply = when {
            callerTurnsCount == 1 && extractedName.isBlank() -> {
                if (preferEnglish) {
                    "Got it, I have noted that down. May I also know your good name and the best time for $bossTitle to call you back?"
                } else {
                    "जी ठीक है, मैंने आपकी बात नोट कर ली है। क्या मैं आपका शुभ नाम जान सकता हूँ और $bossTitle आपको कब कॉल बैक करें?"
                }
            }
            callerTurnsCount == 1 -> {
                if (preferEnglish) {
                    "Thank you $extractedName. I have noted your message about \"$extractedPurpose\". Should I schedule a callback when $bossTitle is free, or is there any extra detail to add?"
                } else {
                    "धन्यवाद $extractedName जी। मैंने \"$extractedPurpose\" के बारे में संदेश नोट कर लिया है। क्या आपको कोई और समय या खास जानकारी जोड़नी है, या मैं कॉलबैक शेड्यूल कर दूँ?"
                }
            }
            else -> {
                if (preferEnglish) {
                    "Perfect${if (extractedName.isNotBlank()) ", $extractedName" else ""}. Everything is saved for $bossTitle and they will reach back as soon as possible. Thank you for calling!"
                } else {
                    "जी बिल्कुल${if (extractedName.isNotBlank()) " $extractedName जी" else ""}। मैंने आपकी पूरी जानकारी $bossTitle के लिए सेव कर दी है। फ्री होते ही वे आपसे बात करेंगे। धन्यवाद, नमस्ते!"
                }
            }
        }

        val endNow = callerTurnsCount >= 3 ||
            containsAny(lower, "बस यही", "इतना ही", "that's all", "nothing else", "call back", "कॉल बैक")

        return CallReceptionistResponse(
            replyToCaller = reply,
            capturedCallerName = extractedName.ifBlank { callerDisplay },
            capturedPurpose = extractedPurpose,
            urgency = urgency,
            shouldEscalateToBoss = false,
            shouldEndCall = endNow,
            summaryForBoss = buildBossSummary(extractedName.ifBlank { callerDisplay }, extractedPurpose, urgency)
        )
    }

    fun formatBossCallAlert(record: HandledCallRecord): String {
        val urgencyWord = when (record.urgency.uppercase(Locale.ROOT)) {
            "HIGH" -> "अर्जेंट"
            "LOW" -> "सामान्य"
            else -> "मध्यम"
        }
        return "बॉस, ${record.callerDisplay} की कॉल मैंने रिसीव की ($urgencyWord प्राथमिकता)। विषय: ${record.purposeSummary}"
    }

    private fun extractCallerName(input: String): String {
        val patterns = listOf(
            Regex("(?:मैं|मेरा नाम|main|mera naam|this is|i am|i'm|my name is)\\s+([\\p{L}\\p{M}]{2,24}(?:\\s+[\\p{L}\\p{M}]{2,22})?)", RegexOption.IGNORE_CASE),
            Regex("^([\\p{L}\\p{M}]{2,20}(?:\\s+[\\p{L}\\p{M}]{2,20})?)\\s+(?:बोल रहा|बोल रही|speaking|here)", RegexOption.IGNORE_CASE)
        )
        for (regex in patterns) {
            val match = regex.find(input)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val cleaned = match
                .replace(
                    Regex(
                        "\\s+(?:बोल|रहा|रही|हूँ|हूं|speaking|here|hoon|hun|hai|from|calling|se|ji|जी)(?:\\s.*|$)",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .trim(' ', ',', '.', '।')
            val blockedTokens = setOf("call", "baat", "urgent", "meeting", "boss", "bhai", "बोल", "रहा", "रही", "speaking")
            if (cleaned.length in 2..30 && cleaned.lowercase(Locale.ROOT) !in blockedTokens) {
                return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
        return ""
    }

    private fun detectUrgency(lower: String): String {
        return when {
            containsAny(
                lower,
                "urgent", "emergency", "अर्जेंट", "इमरजेंसी", "बहुत ज़रूरी", "तुरंत",
                "hospital", "अस्पताल", "accident", "police", "police", "तबीयत", "flight", "train", "अभी बात"
            ) -> "HIGH"
            containsAny(
                lower,
                "offer", "loan", "discount", "spam", "advertisement", "कोई बात नहीं", "later", "आराम से"
            ) -> "LOW"
            else -> "MEDIUM"
        }
    }

    private fun containsGoodbye(lower: String): Boolean =
        containsAny(lower, "bye", "goodbye", "thank you", "thanks", "धन्यवाद", "शुक्रिया", "ok रख", "रखता हूँ", "रखती हूँ", "नमस्ते") &&
            lower.length <= 45

    private fun summarizePurposeSnippet(newInput: String, existingPurpose: String): String {
        val snippet = newInput.replace(Regex("\\s+"), " ").trim().take(120)
        return if (existingPurpose.isBlank() || existingPurpose.startsWith("कॉल पर")) {
            snippet
        } else if (existingPurpose.contains(snippet, ignoreCase = true)) {
            existingPurpose
        } else {
            "$existingPurpose • $snippet".take(160)
        }
    }

    private fun buildBossSummary(caller: String, purpose: String, urgency: String): String {
        val who = caller.ifBlank { "कॉलर" }
        val tag = when (urgency) {
            "HIGH" -> "अर्जेंट कॉल"
            "LOW" -> "सामान्य कॉल"
            else -> "कॉलबैक अनुरोध"
        }
        return "$tag: $who — $purpose"
    }

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it, ignoreCase = true) }
}
