package com.aura.app.ui

/**
 * The finite emotion vocabulary shared with the LLM response contract.
 *
 * Profiles are deliberately data-only so the Canvas renderer can animate
 * between emotions without introducing one-off branches for each feeling.
 */
enum class AuraEmotion(
    val wireValue: String,
    val label: String,
    val profile: EyeEmotionProfile
) {
    Neutral("neutral", "Neutral", EyeEmotionProfile(0.82f, 0f, 0f, 0f, 0.5f, 0.05f, 0.02f, 0f, 0f)),
    Happy("happy", "Happy", EyeEmotionProfile(0.72f, 2f, 0f, -0.02f, 0.58f, 0.2f, 0.08f, 0.02f, -0.12f)),
    Joyful("joyful", "Joyful", EyeEmotionProfile(0.88f, 0f, 0f, -0.06f, 0.66f, 0.36f, 0.14f, 0.04f, -0.18f, sparkleStrength = 0.34f)),
    Excited("excited", "Excited", EyeEmotionProfile(1f, 0f, 0f, -0.04f, 0.68f, 0.5f, 0.2f, 0.02f, 0f, sparkleStrength = 0.44f)),
    Playful("playful", "Playful", EyeEmotionProfile(0.78f, -8f, 0.16f, -0.02f, 0.72f, 0.3f, 0.12f, 0.08f, -0.25f, winkStrength = 0.82f, sparkleStrength = 0.2f)),
    Cute("cute", "Cute", EyeEmotionProfile(0.98f, 0f, 0f, 0.08f, 0.88f, 0.42f, 0.1f, 0.09f, -0.05f, sparkleStrength = 0.38f)),
    Adoring("adoring", "Adoring", EyeEmotionProfile(0.76f, 0f, 0f, 0.16f, 0.92f, 0.55f, 0.04f, 0.13f, -0.08f, sparkleStrength = 0.44f)),
    Affectionate("affectionate", "Affectionate", EyeEmotionProfile(0.68f, 2f, 0f, 0.14f, 0.86f, 0.42f, 0.04f, 0.1f, -0.12f)),
    Grateful("grateful", "Grateful", EyeEmotionProfile(0.7f, 3f, 0f, 0.12f, 0.7f, 0.3f, 0.03f, 0.06f, -0.2f)),
    Proud("proud", "Proud", EyeEmotionProfile(0.66f, -3f, 0f, -0.14f, 0.58f, 0.16f, 0.04f, 0.02f, -0.35f)),
    Relieved("relieved", "Relieved", EyeEmotionProfile(0.57f, 0f, 0f, 0.08f, 0.5f, 0.12f, 0.02f, 0.04f, -0.15f)),
    Hopeful("hopeful", "Hopeful", EyeEmotionProfile(0.76f, 3f, 0f, 0.12f, 0.62f, 0.24f, 0.05f, 0.05f, -0.3f)),
    Encouraging("encouraging", "Encouraging", EyeEmotionProfile(0.84f, 0f, 0f, -0.03f, 0.64f, 0.28f, 0.1f, 0.02f, -0.2f)),
    Curious("curious", "Curious", EyeEmotionProfile(0.94f, -5f, 0.12f, -0.02f, 0.72f, 0.28f, 0.07f, 0.18f, -0.1f)),
    Interested("interested", "Interested", EyeEmotionProfile(0.9f, 0f, 0.08f, 0f, 0.68f, 0.18f, 0.05f, 0.04f, -0.05f)),
    Focused("focused", "Focused", EyeEmotionProfile(0.8f, 0f, 0f, 0f, 0.42f, 0.08f, 0.03f, 0f, 0.2f)),
    Thinking("thinking", "Thinking", EyeEmotionProfile(0.64f, -7f, 0.22f, -0.08f, 0.4f, 0.08f, 0.04f, 0.08f, 0.16f)),
    Confused("confused", "Confused", EyeEmotionProfile(0.72f, 7f, -0.08f, 0.04f, 0.5f, 0.08f, 0.05f, 0.2f, 0.14f)),
    Surprised("surprised", "Surprised", EyeEmotionProfile(1f, 0f, 0f, 0f, 0.7f, 0.3f, 0.12f, 0.02f, 0.42f)),
    Amazed("amazed", "Amazed", EyeEmotionProfile(1f, 0f, 0f, -0.04f, 0.9f, 0.55f, 0.15f, 0.03f, 0.5f, sparkleStrength = 0.5f)),
    Sleepy("sleepy", "Sleepy", EyeEmotionProfile(0.2f, 0f, 0f, 0.08f, 0.32f, 0f, 0f, 0f, -0.08f)),
    Calm("calm", "Calm", EyeEmotionProfile(0.6f, 0f, 0f, 0.04f, 0.46f, 0.06f, 0.01f, 0.02f, -0.08f)),
    Empathetic("empathetic", "Empathetic", EyeEmotionProfile(0.54f, 5f, 0f, 0.16f, 0.5f, 0.12f, 0.02f, 0.08f, -0.24f)),
    Sad("sad", "Sad", EyeEmotionProfile(0.5f, 8f, 0f, 0.18f, 0.42f, 0.02f, 0f, 0.03f, -0.34f)),
    Lonely("lonely", "Lonely", EyeEmotionProfile(0.42f, 10f, 0.16f, 0.2f, 0.34f, 0f, 0f, 0.05f, -0.4f)),
    Worried("worried", "Worried", EyeEmotionProfile(0.58f, -5f, 0f, 0.08f, 0.44f, 0.04f, 0.03f, 0.24f, 0.3f)),
    Concerned("concerned", "Concerned", EyeEmotionProfile(0.54f, 5f, 0f, 0.08f, 0.42f, 0.04f, 0.04f, 0.18f, 0.22f)),
    Afraid("afraid", "Afraid", EyeEmotionProfile(0.92f, 0f, 0f, 0.08f, 0.3f, 0.1f, 0.16f, 0.1f, 0.48f)),
    Embarrassed("embarrassed", "Embarrassed", EyeEmotionProfile(0.48f, 6f, 0.18f, 0.16f, 0.62f, 0.12f, 0.03f, 0.16f, 0.02f)),
    Shy("shy", "Shy", EyeEmotionProfile(0.38f, 8f, 0.2f, 0.14f, 0.58f, 0.1f, 0.02f, 0.12f, -0.16f)),
    Bashful("bashful", "Bashful", EyeEmotionProfile(0.52f, -5f, -0.18f, 0.15f, 0.72f, 0.16f, 0.04f, 0.18f, -0.08f, winkStrength = 0.38f)),
    Doubtful("doubtful", "Doubtful", EyeEmotionProfile(0.62f, -8f, 0.2f, 0.02f, 0.42f, 0.04f, 0.03f, 0.22f, 0.18f)),
    Skeptical("skeptical", "Skeptical", EyeEmotionProfile(0.56f, -12f, 0.24f, -0.02f, 0.36f, 0.02f, 0.04f, 0.28f, 0.34f)),
    Annoyed("annoyed", "Annoyed", EyeEmotionProfile(0.48f, 14f, 0f, -0.04f, 0.36f, 0.04f, 0.08f, 0.06f, 0.4f)),
    Frustrated("frustrated", "Frustrated", EyeEmotionProfile(0.42f, 18f, 0f, 0.02f, 0.32f, 0.08f, 0.14f, 0.04f, 0.48f)),
    Determined("determined", "Determined", EyeEmotionProfile(0.54f, 16f, 0f, -0.08f, 0.3f, 0.1f, 0.1f, 0f, 0.58f)),
    Mischievous("mischievous", "Mischievous", EyeEmotionProfile(0.62f, -10f, 0.2f, -0.04f, 0.62f, 0.2f, 0.09f, 0.12f, 0.42f, winkStrength = 0.72f)),
    Smug("smug", "Smug", EyeEmotionProfile(0.46f, -9f, 0.16f, -0.14f, 0.34f, 0.08f, 0.04f, 0.08f, 0.5f, winkStrength = 0.58f)),
    Angry("angry", "Angry", EyeEmotionProfile(0.5f, 20f, 0f, -0.1f, 0.3f, 0.08f, 0.12f, 0f, 0.7f)),
    Furious("furious", "Furious", EyeEmotionProfile(0.64f, 26f, 0f, -0.12f, 0.28f, 0.22f, 0.22f, 0f, 0.85f)),
    Enraged("enraged", "Enraged", EyeEmotionProfile(0.74f, 32f, 0f, -0.08f, 0.24f, 0.42f, 0.3f, 0f, 1f)),

    ;

    companion object {
        private val byWireValue = entries.associateBy { it.wireValue }
        private val tagPattern = "\\{([a-zA-Z0-9_-]+)\\}".toRegex()

        fun fromWireValue(value: String?): AuraEmotion =
            byWireValue[value?.trim()?.lowercase()] ?: Neutral

        fun isKnownWireValue(value: String?): Boolean =
            value?.trim()?.lowercase() in byWireValue

        fun fromReplyTag(reply: String): AuraEmotion =
            fromWireValue(tagPattern.find(reply)?.groupValues?.getOrNull(1))

        fun resolve(wireValue: String?, createdEmotion: String?): ResolvedAuraEmotion {
            val directive = normalizeCreatedEmotion(createdEmotion ?: wireValue)
            if (directive != null) {
                val description = directive.removePrefix("create ")
                return ResolvedAuraEmotion(
                    wireValue = "custom:${description.replace(' ', '-')}",
                    label = description.split(' ').joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    },
                    profile = eyeEmotionProfileFromDescription(description),
                    createdEmotion = directive,
                )
            }
            val known = fromWireValue(wireValue)
            return ResolvedAuraEmotion(known.wireValue, known.label, known.profile)
        }
    }
}

data class ResolvedAuraEmotion(
    val wireValue: String,
    val label: String,
    val profile: EyeEmotionProfile,
    val createdEmotion: String? = null,
)

data class EyeEmotionProfile(
    val openness: Float,
    val tiltDegrees: Float,
    val gazeX: Float,
    val gazeY: Float,
    val pupilScale: Float,
    val glow: Float,
    val bounce: Float,
    val asymmetry: Float,
    val browAngle: Float,
    val winkStrength: Float = 0f,
    val sparkleStrength: Float = 0f,
)

private val emotionWordPattern = Regex("[a-zA-Z0-9_-]+")

private fun normalizeCreatedEmotion(value: String?): String? {
    val candidate = value?.trim() ?: return null
    if (!candidate.startsWith("create ", ignoreCase = true)) return null
    val raw = candidate.substringAfter(' ').trim()
    val words = emotionWordPattern.findAll(raw.lowercase()).map { it.value }.toList()
    if (words.isEmpty() || words.size > 6) return null
    val description = words.joinToString(" ")
    if (description.length !in 2..64) return null
    return "create $description"
}

private fun hasEmotionWord(description: String, vararg words: String): Boolean =
    words.any { description.contains(it) }

private fun EyeEmotionProfile.withAdjustments(
    openness: Float = 0f,
    tiltDegrees: Float = 0f,
    gazeX: Float = 0f,
    gazeY: Float = 0f,
    pupilScale: Float = 0f,
    glow: Float = 0f,
    bounce: Float = 0f,
    asymmetry: Float = 0f,
    browAngle: Float = 0f,
    winkStrength: Float = 0f,
    sparkleStrength: Float = 0f,
): EyeEmotionProfile = copy(
    openness = this.openness + openness,
    tiltDegrees = this.tiltDegrees + tiltDegrees,
    gazeX = this.gazeX + gazeX,
    gazeY = this.gazeY + gazeY,
    pupilScale = this.pupilScale + pupilScale,
    glow = this.glow + glow,
    bounce = this.bounce + bounce,
    asymmetry = this.asymmetry + asymmetry,
    browAngle = this.browAngle + browAngle,
    winkStrength = this.winkStrength + winkStrength,
    sparkleStrength = this.sparkleStrength + sparkleStrength,
)

private fun eyeEmotionProfileFromDescription(description: String): EyeEmotionProfile {
    val normalized = description.lowercase().trim()
    var profile = EyeEmotionProfile(
        openness = 0.78f,
        tiltDegrees = 0f,
        gazeX = 0f,
        gazeY = 0f,
        pupilScale = 0.5f,
        glow = 0.08f,
        bounce = 0.03f,
        asymmetry = 0f,
        browAngle = 0f,
    )
    if (hasEmotionWord(normalized, "cute", "adorable", "sweet", "precious", "soft", "kawaii")) {
        profile = profile.withAdjustments(openness = 0.18f, gazeY = 0.06f, pupilScale = 0.28f, glow = 0.28f, asymmetry = 0.08f, sparkleStrength = 0.3f)
    }
    if (hasEmotionWord(normalized, "love", "adoring", "affection", "warm", "tender")) {
        profile = profile.withAdjustments(gazeY = 0.14f, pupilScale = 0.24f, glow = 0.3f, asymmetry = 0.1f, browAngle = -0.1f, sparkleStrength = 0.28f)
    }
    if (hasEmotionWord(normalized, "happy", "joy", "joyful", "delight", "cheerful")) {
        profile = profile.withAdjustments(openness = 0.1f, pupilScale = 0.12f, glow = 0.24f, bounce = 0.08f, browAngle = -0.15f, sparkleStrength = 0.24f)
    }
    if (hasEmotionWord(normalized, "excited", "energetic", "hype", "thrilled")) {
        profile = profile.withAdjustments(openness = 0.18f, pupilScale = 0.12f, glow = 0.3f, bounce = 0.15f, sparkleStrength = 0.3f)
    }
    if (hasEmotionWord(normalized, "playful", "mischief", "silly", "teasing", "cheeky", "wink")) {
        profile = profile.withAdjustments(tiltDegrees = -9f, gazeX = 0.16f, asymmetry = 0.1f, browAngle = 0.35f, winkStrength = 0.7f, sparkleStrength = 0.2f)
    }
    if (hasEmotionWord(normalized, "curious", "wonder", "inquisitive")) {
        profile = profile.withAdjustments(openness = 0.14f, gazeX = 0.12f, asymmetry = 0.16f, browAngle = -0.1f)
    }
    if (hasEmotionWord(normalized, "sleepy", "dreamy", "drowsy", "sleep")) {
        profile = profile.withAdjustments(openness = -0.45f, gazeY = 0.1f, pupilScale = -0.1f, bounce = -0.02f)
    }
    if (hasEmotionWord(normalized, "sad", "lonely", "sorrow", "melancholy")) {
        profile = profile.withAdjustments(openness = -0.25f, tiltDegrees = 6f, gazeY = 0.15f, browAngle = -0.3f)
    }
    if (hasEmotionWord(normalized, "worried", "anxious", "nervous", "concern")) {
        profile = profile.withAdjustments(openness = -0.18f, gazeY = 0.08f, asymmetry = 0.2f, browAngle = 0.3f)
    }
    if (hasEmotionWord(normalized, "confused", "puzzled", "unsure")) {
        profile = profile.withAdjustments(tiltDegrees = 7f, gazeX = -0.08f, asymmetry = 0.2f, browAngle = 0.15f)
    }
    if (hasEmotionWord(normalized, "surprised", "astonished", "amazed", "wonderstruck")) {
        profile = profile.withAdjustments(openness = 0.22f, pupilScale = 0.22f, glow = 0.2f, bounce = 0.08f, browAngle = 0.45f, sparkleStrength = 0.22f)
    }
    if (hasEmotionWord(normalized, "angry", "mad", "furious", "rage", "enraged", "villain")) {
        profile = profile.withAdjustments(openness = -0.2f, tiltDegrees = 22f, pupilScale = -0.2f, browAngle = 0.65f, bounce = 0.12f)
    }
    if (hasEmotionWord(normalized, "smug", "confident", "proud")) {
        profile = profile.withAdjustments(openness = -0.25f, tiltDegrees = -8f, gazeX = 0.16f, browAngle = 0.4f, winkStrength = 0.45f)
    }
    if (hasEmotionWord(normalized, "calm", "peaceful", "serene")) {
        profile = profile.withAdjustments(openness = -0.12f, bounce = -0.01f)
    }
    if (hasEmotionWord(normalized, "focused", "determined", "serious")) {
        profile = profile.withAdjustments(openness = -0.15f, pupilScale = -0.1f, tiltDegrees = 10f, browAngle = 0.5f)
    }

    val hash = normalized.hashCode()
    val gazeJitter = ((hash and 0xFF) / 255f - 0.5f) * 0.08f
    val tiltJitter = (((hash ushr 8) and 0xFF) / 255f - 0.5f) * 6f
    return profile.copy(
        openness = (profile.openness + (((hash ushr 16) and 0xFF) / 255f - 0.5f) * 0.06f).coerceIn(0.12f, 1.05f),
        tiltDegrees = (profile.tiltDegrees + tiltJitter).coerceIn(-24f, 34f),
        gazeX = (profile.gazeX + gazeJitter).coerceIn(-0.3f, 0.3f),
        gazeY = profile.gazeY.coerceIn(-0.25f, 0.25f),
        pupilScale = profile.pupilScale.coerceIn(0.18f, 0.95f),
        glow = profile.glow.coerceIn(0f, 0.7f),
        bounce = profile.bounce.coerceIn(0f, 0.35f),
        asymmetry = profile.asymmetry.coerceIn(0f, 0.3f),
        browAngle = profile.browAngle.coerceIn(-0.5f, 1f),
        winkStrength = profile.winkStrength.coerceIn(0f, 1f),
        sparkleStrength = profile.sparkleStrength.coerceIn(0f, 0.8f),
    )
}
