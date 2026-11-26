package com.example.vision

import androidx.compose.runtime.Immutable
import java.util.Locale

@Immutable
data class VisionLabel(
    val rawLabel: String,
    val hindiName: String,
    val meaningHindi: String,
    val confidence: Float
)

@Immutable
data class VisionBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String,
    val confidence: Float
)

@Immutable
data class VisionBarcodeInfo(
    val rawValue: String,
    val kindHindi: String,
    val explanationHindi: String
)

@Immutable
data class OfflineVisionAnalysis(
    val labels: List<VisionLabel> = emptyList(),
    val boxes: List<VisionBoundingBox> = emptyList(),
    val recognizedText: String = "",
    val barcodes: List<VisionBarcodeInfo> = emptyList(),
    val frameWidth: Int = 1,
    val frameHeight: Int = 1,
    val summaryHindi: String = "कैमरा किसी चीज़ की तरफ़ रखें — बिना इंटरनेट के तुरंत पहचान होगी।",
    val contextualInsightHindi: String = "",
    val analyzedAtMillis: Long = System.currentTimeMillis()
) {
    val hasDetections: Boolean
        get() = labels.isNotEmpty() || boxes.isNotEmpty() || recognizedText.isNotBlank() || barcodes.isNotEmpty()

    fun buildSpokenSummary(ownerName: String = ""): String {
        val boss = if (ownerName.isNotBlank()) "$ownerName बॉस" else "बॉस"
        if (!hasDetections) {
            return "$boss, अभी कैमरे के सामने कोई साफ़ चीज़ या लिखावट नज़र नहीं आ रही — थोड़ा पास या रोशनी में लाएं।"
        }
        return buildString {
            append("$boss, ")
            append(summaryHindi)
            if (contextualInsightHindi.isNotBlank()) {
                append(" ")
                append(contextualInsightHindi)
            }
        }.trim()
    }
}

/**
 * 100% offline bilingual understanding layer for LEON Eye.
 *
 * Combines on-device object/image labels, Devanagari/Latin OCR text, and QR/Barcodes
 * into natural Hindi recognition + practical understanding with zero internet dependency.
 */
object OfflineVisionInterpreter {

    private data class HindiConcept(
        val hindiName: String,
        val explanation: String,
        val domain: String
    )

    private val conceptDictionary: Map<String, HindiConcept> = mapOf(
        "mobile phone" to HindiConcept("मोबाइल फ़ोन", "स्मार्टफ़ोन / कॉल और इंटरनेट चलाने का उपकरण", "tech"),
        "smartphone" to HindiConcept("स्मार्टफ़ोन", "टचस्क्रीन फ़ोन", "tech"),
        "phone" to HindiConcept("फ़ोन", "बातचीत और डिजिटल काम का उपकरण", "tech"),
        "telephone" to HindiConcept("टेलीफ़ोन", "कॉल करने का उपकरण", "tech"),
        "laptop" to HindiConcept("लैपटॉप", "पोर्टेबल कंप्यूटर — पढ़ाई, ऑफिस और कोडिंग का साधन", "tech"),
        "computer" to HindiConcept("कंप्यूटर", "डेस्कटॉप या लैपटॉप सिस्टम", "tech"),
        "desktop computer" to HindiConcept("डेस्कटॉप कंप्यूटर", "कंप्यूटर सिस्टम", "tech"),
        "computer keyboard" to HindiConcept("कीबोर्ड", "कंप्यूटर पर टाइप करने का यंत्र", "tech"),
        "keyboard" to HindiConcept("कीबोर्ड", "टाइपिंग के लिए कुंजीपटल", "tech"),
        "computer mouse" to HindiConcept("माउस", "कंप्यूटर कर्सर कंट्रोल करने का यंत्र", "tech"),
        "mouse" to HindiConcept("माउस", "कंप्यूटर पॉइंटर डिवाइस", "tech"),
        "computer monitor" to HindiConcept("मॉनिटर / स्क्रीन", "कंप्यूटर डिस्प्ले स्क्रीन", "tech"),
        "monitor" to HindiConcept("मॉनिटर स्क्रीन", "डिस्प्ले स्क्रीन", "tech"),
        "television" to HindiConcept("टीवी (टेलीविज़न)", "वीडियो और समाचार देखने की स्क्रीन", "tech"),
        "tablet computer" to HindiConcept("टैबलेट", "बड़ी स्क्रीन वाला पोर्टेबल डिवाइस", "tech"),
        "headphones" to HindiConcept("हेडफ़ोन", "आवाज़ और म्यूजिक सुनने का उपकरण", "tech"),
        "earphones" to HindiConcept("ईयरफ़ोन", "ऑडियो सुनने का यंत्र", "tech"),
        "microphone" to HindiConcept("माइक्रोफ़ोन", "आवाज़ रिकॉर्ड या बोलने का यंत्र", "tech"),
        "camera" to HindiConcept("कैमरा", "फ़ोटो और वीडियो खींचने का उपकरण", "tech"),
        "remote control" to HindiConcept("रिमोट कंट्रोल", "टीवी या AC कंट्रोल करने का रिमोट", "tech"),
        "charger" to HindiConcept("चार्जर", "बैटरी चार्ज करने का केबल/अडैप्टर", "tech"),
        "cable" to HindiConcept("केबल / तार", "कनेक्शन या चार्जिंग केबल", "tech"),
        "watch" to HindiConcept("घड़ी", "समय देखने की कलाई घड़ी या स्मार्टवॉच", "accessory"),
        "clock" to HindiConcept("घड़ी", "समय बताने वाली घड़ी", "home"),
        "glasses" to HindiConcept("चश्मा", "आँखों का चश्मा या धूप का चश्मा", "accessory"),
        "sunglasses" to HindiConcept("धूप का चश्मा", "आँखों को धूप से बचाने वाला चश्मा", "accessory"),
        "eyewear" to HindiConcept("चश्मा", "आँखों पर पहनने वाला चश्मा", "accessory"),
        "book" to HindiConcept("किताब / कॉपी", "पढ़ने, लिखने या नोट्स रखने की पुस्तक", "study"),
        "notebook" to HindiConcept("नोटबुक / कॉपी", "नोट्स लिखने की कॉपी", "study"),
        "paper" to HindiConcept("कागज़ / दस्तावेज़", "लिखने या प्रिंट किया हुआ कागज़", "study"),
        "document" to HindiConcept("दस्तावेज़", "जरूरी कागज़ात या पेज", "study"),
        "pen" to HindiConcept("पेन (कलम)", "लिखने का उपकरण", "study"),
        "pencil" to HindiConcept("पेंसिल", "लिखने या ड्रॉइंग बनाने का उपकरण", "study"),
        "bottle" to HindiConcept("बोतल", "पानी या तरल पदार्थ रखने की बोतल", "daily"),
        "water bottle" to HindiConcept("पानी की बोतल", "पीने का पानी रखने की बोतल", "daily"),
        "plastic bottle" to HindiConcept("प्लास्टिक की बोतल", "पानी या पेय रखने की बोतल", "daily"),
        "cup" to HindiConcept("कप / मग", "चाय, कॉफ़ी या पानी पीने का कप", "kitchen"),
        "mug" to HindiConcept("मग", "चाय या कॉफ़ी पीने का बड़ा कप", "kitchen"),
        "coffee cup" to HindiConcept("चाय/कॉफ़ी का कप", "गर्म पेय पीने का कप", "kitchen"),
        "glass" to HindiConcept("गिलास / काँच", "पानी पीने का गिलास या काँच की वस्तु", "kitchen"),
        "plate" to HindiConcept("थाली / प्लेट", "खाना परोसने की प्लेट", "kitchen"),
        "bowl" to HindiConcept("कटोरा / बाउल", "खाना या सूप रखने का बर्तन", "kitchen"),
        "spoon" to HindiConcept("चम्मच", "खाना खाने या मिलाने का चम्मच", "kitchen"),
        "fork" to HindiConcept("काँटा चम्मच", "खाना खाने का फोर्क", "kitchen"),
        "knife" to HindiConcept("चाकू", "सब्जी या फल काटने का उपकरण", "kitchen"),
        "tableware" to HindiConcept("बर्तन", "खाने-पीने के बर्तन", "kitchen"),
        "food" to HindiConcept("खाना / भोजन", "खाने योग्य सामग्री", "food"),
        "fruit" to HindiConcept("फल", "ताज़ा और स्वास्थ्यवर्धक फल", "food"),
        "apple" to HindiConcept("सेब", "ताज़ा फल", "food"),
        "banana" to HindiConcept("केला", "ऊर्जा देने वाला फल", "food"),
        "vegetable" to HindiConcept("सब्जी", "खाने की ताज़ी सब्जी", "food"),
        "bread" to HindiConcept("ब्रेड / रोटी", "नाश्ते या खाने की सामग्री", "food"),
        "drink" to HindiConcept("पेय पदार्थ", "पीने की चीज़", "food"),
        "chair" to HindiConcept("कुर्सी", "बैठने की कुर्सी", "furniture"),
        "table" to HindiConcept("मेज़ (टेबल)", "सामान रखने या काम करने की टेबल", "furniture"),
        "desk" to HindiConcept("डेस्क / स्टडी टेबल", "पढ़ाई या कंप्यूटर रखने की मेज़", "furniture"),
        "couch" to HindiConcept("सोफ़ा", "बैठने का आरामदायक सोफ़ा", "furniture"),
        "sofa" to HindiConcept("सोफ़ा", "बैठने का सोफ़ा", "furniture"),
        "bed" to HindiConcept("बिस्तर (बेड)", "सोने और आराम करने का बिस्तर", "furniture"),
        "pillow" to HindiConcept("तकिया", "सिर के नीचे रखने का तकिया", "home"),
        "door" to HindiConcept("दरवाज़ा", "कमरे या घर का प्रवेश द्वार", "home"),
        "window" to HindiConcept("खिड़की", "हवा और रोशनी के लिए खिड़की", "home"),
        "lamp" to HindiConcept("लैंप / लाइट", "रोशनी देने वाला लैंप", "home"),
        "fan" to HindiConcept("पंखा", "हवा देने वाला पंखा", "home"),
        "mirror" to HindiConcept("आईना (शीशा)", "चेहरा देखने का दर्पण", "home"),
        "bag" to HindiConcept("बैग", "सामान या किताबें रखने का थैला/बैग", "accessory"),
        "backpack" to HindiConcept("पीठ का बैग (बैकपैक)", "स्कूल, कॉलेज या यात्रा का बैग", "accessory"),
        "handbag" to HindiConcept("हैंडबैग", "जरूरी सामान रखने का बैग", "accessory"),
        "wallet" to HindiConcept("बटुआ (पर्स)", "पैसे और कार्ड रखने का पर्स", "accessory"),
        "shoe" to HindiConcept("जूता", "पैर में पहनने वाला जूता", "clothing"),
        "footwear" to HindiConcept("जूते-चप्पल", "पैर में पहनने का फ़ुटवियर", "clothing"),
        "sneakers" to HindiConcept("स्नीकर्स / स्पोर्ट्स शूज", "आरामदायक जूते", "clothing"),
        "shirt" to HindiConcept("शर्ट / टी-शर्ट", "पहनने का वस्त्र", "clothing"),
        "t-shirt" to HindiConcept("टी-शर्ट", "ऊपर पहनने का कपड़ा", "clothing"),
        "jacket" to HindiConcept("जैकेट", "सर्दी या स्टाइल के लिए जैकेट", "clothing"),
        "hat" to HindiConcept("टोपी", "सिर पर पहनने की टोपी", "clothing"),
        "cap" to HindiConcept("टोपी (कैप)", "धूप से बचाने वाली टोपी", "clothing"),
        "key" to HindiConcept("चाबी", "ताला या गाड़ी खोलने की चाबी", "daily"),
        "umbrella" to HindiConcept("छाता", "बारिश और धूप से बचाने वाला छाता", "daily"),
        "toothbrush" to HindiConcept("टूथब्रश", "दाँत साफ़ करने का ब्रश", "daily"),
        "person" to HindiConcept("इंसान / व्यक्ति", "कैमरे के सामने कोई व्यक्ति मौजूद है", "person"),
        "face" to HindiConcept("चेहरा", "किसी व्यक्ति का चेहरा दिख रहा है", "person"),
        "hand" to HindiConcept("हाथ", "व्यक्ति का हाथ या उँगलियाँ", "person"),
        "smile" to HindiConcept("मुस्कान", "ख़ुश चेहरा / मुस्कान", "person"),
        "car" to HindiConcept("कार", "चार पहिया वाहन", "vehicle"),
        "vehicle" to HindiConcept("वाहन (गाड़ी)", "यातायात का साधन", "vehicle"),
        "bicycle" to HindiConcept("साइकिल", "दो पहिया सवारी साइकिल", "vehicle"),
        "motorcycle" to HindiConcept("मोटरसाइकिल / बाइक", "दो पहिया वाहन", "vehicle"),
        "bus" to HindiConcept("बस", "सवारी बस", "vehicle"),
        "dog" to HindiConcept("कुत्ता", "पालतू या घरेलू पशु (डॉग)", "nature"),
        "cat" to HindiConcept("बिल्ली", "पालतू जानवर (कैट)", "nature"),
        "bird" to HindiConcept("पक्षी", "उड़ने वाला पक्षी", "nature"),
        "plant" to HindiConcept("पौधा", "हरा-भरा पौधा या गमला", "nature"),
        "houseplant" to HindiConcept("घर का पौधा (गमला)", "सजावटी हरा पौधा", "nature"),
        "flower" to HindiConcept("फूल", "ताज़ा और सुंदर फूल", "nature"),
        "tree" to HindiConcept("पेड़", "प्रकृति का हरा पेड़", "nature"),
        "sky" to HindiConcept("आसमान", "ऊपर खुला आकाश", "nature"),
        "building" to HindiConcept("इमारत / घर", "भवन या मकान", "place"),
        "fashion good" to HindiConcept("फ़ैशन / पहनने की वस्तु", "कपड़े, बैग, जूते या चश्मा जैसा सामान", "accessory"),
        "home good" to HindiConcept("घरेलू वस्तु", "घर या कमरे में इस्तेमाल होने वाली चीज़", "home"),
        "place" to HindiConcept("स्थान / दृश्य", "आसपास की जगह", "place"),
        "electronics" to HindiConcept("इलेक्ट्रॉनिक सामान", "बिजली या बैटरी से चलने वाला उपकरण", "tech"),
        "gadget" to HindiConcept("गैजेट / डिवाइस", "तकनीकी उपकरण", "tech"),
        "product" to HindiConcept("पैकेट / प्रोडक्ट", "दुकान या इस्तेमाल का कोई पैक्ड सामान", "daily"),
        "packaging" to HindiConcept("पैकेट / डिब्बा", "पैकिंग किया हुआ सामान", "daily"),
        "text" to HindiConcept("लिखावट (टेक्स्ट)", "कागज़ या स्क्रीन पर कुछ लिखा हुआ है", "study")
    )

    fun interpret(
        rawLabels: List<Pair<String, Float>>,
        rawBoxes: List<VisionBoundingBox> = emptyList(),
        recognizedText: String = "",
        rawBarcodes: List<String> = emptyList(),
        frameWidth: Int = 1,
        frameHeight: Int = 1
    ): OfflineVisionAnalysis {
        val cleanedLabels = rawLabels
            .filter { it.first.isNotBlank() && it.second >= 0.45f }
            .distinctBy { it.first.lowercase(Locale.ROOT) }
            .sortedByDescending { it.second }
            .take(5)
            .map { (raw, conf) ->
                val translated = translateConcept(raw)
                VisionLabel(
                    rawLabel = raw,
                    hindiName = translated.hindiName,
                    meaningHindi = translated.explanation,
                    confidence = conf
                )
            }

        val cleanedText = recognizedText
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(280)

        val barcodeInfos = rawBarcodes
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
            .map { interpretBarcode(it) }

        val translatedBoxes = rawBoxes.map { box ->
            val translatedLabel = if (box.label.isNotBlank()) {
                translateConcept(box.label).hindiName
            } else {
                "पहचानी गई वस्तु"
            }
            box.copy(label = translatedLabel)
        }

        val summary = buildSummaryHindi(cleanedLabels, cleanedText, barcodeInfos, translatedBoxes)
        val insight = buildContextualInsight(cleanedLabels, cleanedText, barcodeInfos)

        return OfflineVisionAnalysis(
            labels = cleanedLabels,
            boxes = translatedBoxes,
            recognizedText = cleanedText,
            barcodes = barcodeInfos,
            frameWidth = frameWidth.coerceAtLeast(1),
            frameHeight = frameHeight.coerceAtLeast(1),
            summaryHindi = summary,
            contextualInsightHindi = insight
        )
    }

    private fun translateConcept(raw: String): HindiConcept {
        val key = raw.trim().lowercase(Locale.ROOT)
        conceptDictionary[key]?.let { return it }

        // Partial keyword match for compound ML Kit labels (e.g. "Drinking bottle", "Office desk")
        val partial = conceptDictionary.entries.firstOrNull { (dictKey, _) ->
            key.contains(dictKey) || dictKey.contains(key)
        }?.value
        if (partial != null) return partial

        return HindiConcept(
            hindiName = raw.trim(),
            explanation = "कैमरे द्वारा ऑफ़लाइन पहचानी गई वस्तु",
            domain = "general"
        )
    }

    internal fun interpretBarcode(rawValue: String): VisionBarcodeInfo {
        val clean = rawValue.trim()
        val lower = clean.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("upi://") -> {
                val payeeName = Regex("(?:pn)=([^&]+)").find(clean)?.groupValues?.get(1)
                    ?.replace("%20", " ")
                    ?.replace("+", " ")
                    .orEmpty()
                val payeeId = Regex("(?:pa)=([^&]+)").find(clean)?.groupValues?.get(1).orEmpty()
                val desc = if (payeeName.isNotBlank()) {
                    "UPI पेमेंट QR: $payeeName ($payeeId)"
                } else {
                    "UPI पेमेंट QR कोड ($payeeId)"
                }
                VisionBarcodeInfo(clean, "UPI पेमेंट QR", desc)
            }
            lower.startsWith("wifi:") -> {
                val ssid = Regex("S:([^;]+)").find(clean)?.groupValues?.get(1).orEmpty()
                VisionBarcodeInfo(
                    clean,
                    "Wi-Fi नेटवर्क QR",
                    if (ssid.isNotBlank()) "Wi-Fi नाम: $ssid" else "Wi-Fi कनेक्शन QR कोड"
                )
            }
            lower.startsWith("http://") || lower.startsWith("https://") -> {
                VisionBarcodeInfo(clean, "वेबसाइट लिंक QR", "वेब लिंक मिला: ${clean.take(70)}")
            }
            lower.startsWith("tel:") -> {
                VisionBarcodeInfo(clean, "फ़ोन नंबर QR", "कॉल नंबर: ${clean.removePrefix("tel:")}")
            }
            clean.all { it.isDigit() } && clean.length in 8..14 -> {
                VisionBarcodeInfo(clean, "प्रोडक्ट बारकोड", "सामान का बारकोड नंबर: $clean")
            }
            else -> {
                VisionBarcodeInfo(clean, "QR / बारकोड डेटा", "इसमें लिखा है: ${clean.take(80)}")
            }
        }
    }

    private fun buildSummaryHindi(
        labels: List<VisionLabel>,
        text: String,
        barcodes: List<VisionBarcodeInfo>,
        boxes: List<VisionBoundingBox>
    ): String {
        val parts = mutableListOf<String>()

        if (labels.isNotEmpty()) {
            val primary = labels.first()
            val extras = labels.drop(1).take(2).joinToString(", ") { it.hindiName }
            val objectPhrase = if (extras.isNotBlank()) {
                "कैमरे में मुख्य रूप से ${primary.hindiName} (${primary.rawLabel}) दिख रहा है — इसके साथ $extras भी नज़र आ रहे हैं।"
            } else {
                "कैमरे में ${primary.hindiName} (${primary.rawLabel}) दिख रहा है (${primary.meaningHindi})।"
            }
            parts.add(objectPhrase)
        } else if (boxes.isNotEmpty()) {
            parts.add("कैमरे में ${boxes.size} वस्तुएं पहचानी गई हैं: ${boxes.first().label}।")
        }

        if (barcodes.isNotEmpty()) {
            parts.add("स्कैन में ${barcodes.first().explanationHindi} मिला।")
        }

        if (text.isNotBlank()) {
            val shortText = if (text.length > 90) "${text.take(90)}..." else text
            parts.add("सामने लिखा हुआ टेक्स्ट पढ़ा: \"$shortText\"।")
        }

        if (parts.isEmpty()) {
            return "कैमरा किसी चीज़, कागज़ या QR कोड की तरफ़ रखें — बिना इंटरनेट के तुरंत पहचान होगी।"
        }
        return parts.joinToString(" ")
    }

    private fun buildContextualInsight(
        labels: List<VisionLabel>,
        text: String,
        barcodes: List<VisionBarcodeInfo>
    ): String {
        val rawLower = labels.map { it.rawLabel.lowercase(Locale.ROOT) }
        val textLower = text.lowercase(Locale.ROOT)

        // 1. Text semantic understanding without internet
        if (textLower.isNotBlank()) {
            val priceMatch = Regex("(?:rs\\.?|inr|mrp|₹)\\s*\\d+(?:[.,]\\d+)?", RegexOption.IGNORE_CASE).find(text)
            if (priceMatch != null) {
                return "समझ: यह किसी प्रोडक्ट का प्राइस टैग या बिल लग रहा है (कीमत: ${priceMatch.value})।"
            }
            if (textLower.contains("exp") || textLower.contains("mfg") || textLower.contains("mg") ||
                textLower.contains("tablet") || textLower.contains("capsule") || textLower.contains("syrup")
            ) {
                return "समझ: यह दवाई (मेडिसिन) या हेल्थ प्रोडक्ट का पैकेट लग रहा है — कृपया इस्तेमाल से पहले एक्सपायरी डेट ज़रूर देखें।"
            }
            if (textLower.contains("@") && textLower.contains(".")) {
                return "समझ: सामने विज़िटिंग कार्ड, ईमेल या डिजिटल संपर्क जानकारी लिखी है।"
            }
            if (Regex("\\b\\d{10}\\b").containsMatchIn(text)) {
                return "समझ: इसमें एक फ़ोन/मोबाइल नंबर दिखाई दे रहा है।"
            }
        }

        if (barcodes.any { it.kindHindi.contains("UPI") }) {
            return "समझ: यह दुकान या पेमेंट के लिए इस्तेमाल होने वाला UPI QR कोड है।"
        }

        // 2. Object co-occurrence scene understanding
        val hasTech = rawLower.any { it in setOf("laptop", "computer", "keyboard", "monitor", "mouse", "desktop computer") }
        val hasStudy = rawLower.any { it in setOf("book", "notebook", "pen", "pencil", "paper", "document") }
        val hasFoodDrink = rawLower.any { it in setOf("bottle", "water bottle", "cup", "mug", "food", "fruit", "plate", "bowl") }
        val hasPerson = rawLower.any { it in setOf("person", "face", "smile") }

        return when {
            hasTech && hasStudy -> "समझ: यह पढ़ाई, नोट्स या ऑफिस वर्क का सेटअप लग रहा है।"
            hasTech -> "समझ: सामने कंप्यूटर/टेक्नोलॉजी से जुड़ा कार्यस्थल या डिवाइस है।"
            hasStudy -> "समझ: यह किताब या पढ़ने-लिखने की सामग्री है — आप चाहें तो टेक्स्ट भी पढ़वा सकते हैं।"
            hasFoodDrink -> "समझ: सामने खाने-पीने की चीज़ या पानी/चाय का बर्तन रखा है।"
            hasPerson -> "समझ: कैमरे के सामने कोई व्यक्ति मौजूद है।"
            else -> ""
        }
    }
}
