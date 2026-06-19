package com.aura.app.voice

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.aura.app.AuraApplication
import com.aura.app.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (!SystemRestoreActions.handles(action)) return
        val applicationContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                BootRestoreCoordinator.handle(
                    restoreListening = action == Intent.ACTION_BOOT_COMPLETED,
                    canRecord = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED,
                    canStartListeningFromBoot = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                    readListeningEnabled = {
                        SessionStore(applicationContext).state.first().backgroundListeningEnabled
                    },
                    startListening = { AuraListeningService.start(applicationContext) },
                    restoreAutomations = {
                        val app = applicationContext as AuraApplication
                        app.container.automationRuntime.restoreTriggers()
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "System recovery did not complete cleanly", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}

internal object SystemRestoreActions {
    fun handles(action: String?): Boolean = action in setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED
    )
}

internal object BootRestoreCoordinator {
    suspend fun handle(
        restoreListening: Boolean = true,
        canRecord: Boolean,
        canStartListeningFromBoot: Boolean,
        readListeningEnabled: suspend () -> Boolean,
        startListening: () -> Unit,
        restoreAutomations: suspend () -> Unit
    ) {
        var firstFailure: Exception? = null
        val shouldRestoreListening = if (restoreListening && canRecord) {
            try {
                readListeningEnabled()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                firstFailure = error
                false
            }
        } else {
            false
        }
        if (shouldRestoreListening && canStartListeningFromBoot) {
            try {
                startListening()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                firstFailure = firstFailure.aggregate(error)
            }
        }
        try {
            restoreAutomations()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            firstFailure = firstFailure.aggregate(error)
        }
        firstFailure?.let { throw it }
    }

    private fun Exception?.aggregate(next: Exception): Exception =
        this?.also { it.addSuppressed(next) } ?: next
}
