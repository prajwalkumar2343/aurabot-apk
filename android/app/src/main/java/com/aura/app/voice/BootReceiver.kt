package com.aura.app.voice

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aura.app.session.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val canRecord = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!canRecord) return

        val shouldRestore = runBlocking {
            SessionStore(context.applicationContext).state.first().backgroundListeningEnabled
        }
        if (shouldRestore) {
            AuraListeningService.start(context.applicationContext)
        }
    }
}
