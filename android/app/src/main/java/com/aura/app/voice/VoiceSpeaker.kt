package com.aura.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceSpeaker(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isInitialized = true
        }
    }

    fun speak(text: String) {
        if (!isInitialized) return
        // Strip emotion tags like {happy} before speaking
        val cleanText = text.replace(Regex("\\{[a-zA-Z0-9_-]+\\}"), "").trim()
        if (cleanText.isEmpty()) return
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "aura_tts")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
