package com.aura.app.voice

data class ListeningStatus(
    val running: Boolean = false,
    val speechDetected: Boolean = false,
    val rmsLevel: Int = 0,
    val speechEvents: Int = 0,
    val lastSpeechAt: Long = 0
)
