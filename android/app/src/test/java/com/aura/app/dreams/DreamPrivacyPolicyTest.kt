package com.aura.app.dreams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamPrivacyPolicyTest {
    @Test
    fun `attributes are restricted to the signal allowlist`() {
        val attributes = DreamPrivacyPolicy.allowlistedAttributes(
            DreamSignalKind.AutomationFailure,
            mapOf(
                "automationName" to "Send ETA",
                "failureKind" to "timeout",
                "typedText" to "private message",
                "password" to "secret"
            )
        )

        assertEquals("Send ETA", attributes["automationName"])
        assertEquals("timeout", attributes["failureKind"])
        assertFalse(attributes.containsKey("typedText"))
        assertFalse(attributes.containsKey("password"))
    }

    @Test
    fun `diagnostics redact credentials and long numbers`() {
        val sanitized = DreamPrivacyPolicy.sanitizeDiagnostic(
            "token=super-secret failed for account 123456789\nretry"
        )

        assertFalse(sanitized.contains("super-secret"))
        assertFalse(sanitized.contains("123456789"))
        assertFalse(sanitized.contains('\n'))
        assertTrue(sanitized.contains("[redacted]"))
    }

    @Test
    fun `fingerprints are deterministic and domain separated`() {
        val first = DreamPrivacyPolicy.fingerprint("kind", "subject", "revision")
        val again = DreamPrivacyPolicy.fingerprint("kind", "subject", "revision")
        val changed = DreamPrivacyPolicy.fingerprint("kind", "subject", "other")

        assertEquals(first, again)
        assertNotEquals(first, changed)
    }

    @Test
    fun `failure categories distinguish permissions and timeouts`() {
        assertEquals("permission", DreamPrivacyPolicy.classifyAutomationFailure("Permission is missing"))
        assertEquals("timeout", DreamPrivacyPolicy.classifyAutomationFailure("Timed out after 5000ms"))
        assertEquals("target_missing", DreamPrivacyPolicy.classifyAutomationFailure("Target not found"))
    }
}
