package com.aura.app.dreams

import org.junit.Assert.assertTrue
import org.junit.Test

class DreamScorerTest {
    @Test
    fun `repeated automation failure outranks equally confident stale task`() {
        val automation = signal(DreamSignalKind.AutomationFailure, confidence = 0.8f)
        val task = signal(DreamSignalKind.StaleTodo, confidence = 0.8f)

        assertTrue(DreamScorer.score(automation) > DreamScorer.score(task))
    }

    @Test
    fun `score is bounded for invalid confidence input`() {
        assertTrue(DreamScorer.score(signal(DreamSignalKind.RepeatedRoutine, 9f)) <= 1f)
        assertTrue(DreamScorer.score(signal(DreamSignalKind.StaleTodo, -4f)) >= 0f)
    }

    private fun signal(kind: DreamSignalKind, confidence: Float) = DreamSignal(
        id = "signal-${kind.name}",
        runId = "run",
        kind = kind,
        subjectId = "subject",
        fingerprint = "fingerprint-${kind.name}",
        summary = "summary",
        attributes = emptyMap(),
        occurredAt = 1L,
        confidence = confidence,
        expiresAt = 2L
    )
}
