package com.aura.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class VoiceSpeaker(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var initFailed = false
    private var pendingSpeech: PendingSpeech? = null
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val utteranceCounter = AtomicLong(0)
    private val currentUtteranceId = AtomicReference<String?>(null)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    finishUtterance(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    finishUtterance(utteranceId)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == currentUtteranceId.get()) {
                        finishUtterance(utteranceId)
                    } else if (utteranceId != null) {
                        callbacks.remove(utteranceId)
                    }
                }
            })
            tts?.language = Locale.US
            isInitialized = true
            pendingSpeech?.let { pending ->
                pendingSpeech = null
                speak(pending.text, pending.onDone)
            }
        } else {
            initFailed = true
            pendingSpeech?.onDone?.invoke()
            pendingSpeech = null
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null): Boolean {
        val cleanText = cleanTextForSpeech(text)
        if (cleanText.isEmpty()) {
            onDone?.invoke()
            return false
        }
        if (initFailed) {
            onDone?.invoke()
            return false
        }
        if (!isInitialized) {
            pendingSpeech = PendingSpeech(cleanText, onDone)
            return true
        }
        return speakNow(cleanText, onDone)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        callbacks.clear()
        pendingSpeech = null
        currentUtteranceId.set(null)
    }

    private fun speakNow(text: String, onDone: (() -> Unit)?): Boolean {
        val utteranceId = "aura_tts_${utteranceCounter.incrementAndGet()}"
        currentUtteranceId.set(utteranceId)
        if (onDone != null) callbacks[utteranceId] = onDone
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            callbacks.remove(utteranceId)?.invoke()
            currentUtteranceId.compareAndSet(utteranceId, null)
            return false
        }
        return true
    }

    private fun finishUtterance(utteranceId: String?) {
        if (utteranceId == null) return
        val callback = callbacks.remove(utteranceId)
        if (utteranceId == currentUtteranceId.get()) {
            currentUtteranceId.compareAndSet(utteranceId, null)
            callback?.invoke()
        }
    }

    private data class PendingSpeech(
        val text: String,
        val onDone: (() -> Unit)?
    )

    companion object {
        private val emotionTagRegex = Regex("\\{[a-zA-Z0-9_-]+\\}")
        private val whitespaceRegex = Regex("\\s+")

        internal fun cleanTextForSpeech(text: String): String =
            text.replace(emotionTagRegex, " ").replace(whitespaceRegex, " ").trim()
    }
}
