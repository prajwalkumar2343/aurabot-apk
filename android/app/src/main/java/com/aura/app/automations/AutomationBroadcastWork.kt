package com.aura.app.automations

import kotlinx.coroutines.CancellationException

internal object AutomationBroadcastWork {
    suspend fun run(
        operation: suspend () -> Unit,
        reportFailure: (Exception) -> Unit
    ) {
        try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            try {
                reportFailure(error)
            } catch (_: Exception) {
                // A receiver must still finish if its diagnostics sink is unavailable.
            }
        }
    }
}
