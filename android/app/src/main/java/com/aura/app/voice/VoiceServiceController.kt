package com.aura.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aura.app.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceServiceController(
    private val context: Context,
    private val sessionStore: SessionStore
) {
    private val _status = MutableStateFlow(AuraListeningService.status())
    val status: StateFlow<ListeningStatus> = _status.asStateFlow()

    init {
        AuraListeningService.setEventSink { _status.value = AuraListeningService.status() }
    }

    fun hasRequiredPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun setBackgroundListening(enabled: Boolean) {
        sessionStore.setBackgroundListeningEnabled(enabled)
        if (enabled) start() else stop()
    }

    fun start(): Boolean {
        if (!hasRequiredPermissions()) return false
        val started = AuraListeningService.start(context)
        _status.value = AuraListeningService.status().copy(running = started)
        return started
    }

    fun stop() {
        AuraListeningService.stop(context)
        _status.value = AuraListeningService.status().copy(running = false, speechDetected = false)
    }

    fun clearLastRecordedAudio(expectedBase64: String? = null) {
        AuraListeningService.clearLastRecordedAudio(expectedBase64)
        _status.value = AuraListeningService.status()
    }

    fun refresh() {
        _status.value = AuraListeningService.status()
    }
}
