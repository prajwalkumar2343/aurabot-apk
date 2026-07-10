package com.aura.app.dreams

import java.security.MessageDigest

object DreamPrivacyPolicy {
    private val allowedAttributeKeys = mapOf(
        DreamSignalKind.AutomationFailure to setOf(
            "automationName", "revision", "stepId", "actionType", "failureKind", "count", "timeoutMillis"
        ),
        DreamSignalKind.StaleTodo to setOf("ageDays"),
        DreamSignalKind.MiniAppEvolution to setOf(
            "miniAppName", "miniAppVersion", "suggestionTitle", "revisionInstruction"
        ),
        DreamSignalKind.RepeatedRoutine to setOf(
            "automationName", "occurrenceCount", "localTime", "sourceAutomationId", "revision"
        )
    )

    fun allowlistedAttributes(kind: DreamSignalKind, attributes: Map<String, String>): Map<String, String> {
        val allowed = allowedAttributeKeys[kind].orEmpty()
        return attributes.asSequence()
            .filter { (key, _) -> key in allowed }
            .take(16)
            .associate { (key, value) -> key.take(60) to value.take(500) }
    }

    fun fingerprint(vararg parts: String): String {
        val source = parts.joinToString("\u001f")
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    fun sanitizeDiagnostic(value: String): String = value
        .replace(Regex("(?i)(api[_ -]?key|token|password|authorization)\\s*[:=]\\s*\\S+"), "$1=[redacted]")
        .replace(Regex("\\b\\d{6,}\\b"), "[number]")
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .trim()
        .take(500)

    fun classifyAutomationFailure(message: String): String = when {
        message.contains("permission", ignoreCase = true) -> "permission"
        message.contains("timed out", ignoreCase = true) || message.contains("timeout", ignoreCase = true) -> "timeout"
        message.contains("not found", ignoreCase = true) || message.contains("missing", ignoreCase = true) -> "target_missing"
        message.contains("interrupted", ignoreCase = true) || message.contains("cancel", ignoreCase = true) -> "interrupted"
        else -> "execution"
    }
}
