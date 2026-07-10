package com.aura.app.dreams

object DreamScorer {
    fun score(signal: DreamSignal): Float {
        val benefit = when (signal.kind) {
            DreamSignalKind.AutomationFailure -> 0.9f
            DreamSignalKind.RepeatedRoutine -> 0.78f
            DreamSignalKind.MiniAppEvolution -> 0.7f
            DreamSignalKind.StaleTodo -> 0.55f
        }
        val riskPenalty = when (signal.kind) {
            DreamSignalKind.AutomationFailure,
            DreamSignalKind.MiniAppEvolution -> 0.08f
            else -> 0.02f
        }
        return (signal.confidence.coerceIn(0f, 1f) * 0.65f + benefit * 0.35f - riskPenalty)
            .coerceIn(0f, 1f)
    }
}
