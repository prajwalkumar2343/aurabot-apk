package com.aura.app.assistant

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmSettingsStoreTest {
    @Test
    fun legacyPlainTextSecretRemainsReadable() {
        val decoded = AndroidKeystoreSecretCodec().decode("plain-api-key")

        assertEquals("plain-api-key", decoded.value)
        assertTrue(decoded.readable)
        assertNull(decoded.errorMessage("Google Gemini"))
    }

    @Test
    fun truncatedEncryptedSecretIsUnreadableInsteadOfBlankMissingKey() {
        val payload = Base64.getEncoder().encodeToString(ByteArray(4))
        val decoded = AndroidKeystoreSecretCodec().decode("keystore:v1:$payload")

        assertEquals("", decoded.value)
        assertFalse(decoded.readable)
        assertEquals(
            "Stored Google Gemini API key could not be read. Re-enter it in Settings.",
            decoded.errorMessage("Google Gemini")
        )
    }

    @Test
    fun unavailableKeystoreEntryIsUnreadableInsteadOfBlankMissingKey() {
        val payload = Base64.getEncoder().encodeToString(ByteArray(13) { 1 })
        val decoded = AndroidKeystoreSecretCodec().decode("keystore:v1:$payload")

        assertEquals("", decoded.value)
        assertFalse(decoded.readable)
    }

    @Test
    fun selectedProviderExposesCurrentApiKeyError() {
        val settings = LlmSettingsState(
            provider = LlmProvider.OpenAI,
            openAiApiKeyError = "Stored OpenAI API key could not be read. Re-enter it in Settings."
        )

        assertEquals(
            "Stored OpenAI API key could not be read. Re-enter it in Settings.",
            settings.currentApiKeyError
        )
    }
}
