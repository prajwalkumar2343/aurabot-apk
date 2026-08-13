package com.aura.app.ui

import com.aura.app.assistant.DEFAULT_GEMINI_MODEL
import com.aura.app.assistant.LlmProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class OnboardingPolicyTest {
    @Test
    fun providerDefaultsMatchSettingsDefaults() {
        assertEquals(DEFAULT_GEMINI_MODEL, defaultOnboardingModel(LlmProvider.Gemini))
        assertEquals("gpt-4.1-mini", defaultOnboardingModel(LlmProvider.OpenAI))
        assertEquals("", defaultOnboardingModel(LlmProvider.OpenRouter))
    }

    @Test
    fun microphonePermissionDoesNotEnableBackgroundListening() {
        assertFalse(ENABLE_BACKGROUND_LISTENING_AFTER_ONBOARDING)
    }

    @Test
    fun localOnboardingRequiresCompleteProviderAndDatabaseConfiguration() {
        val valid = OnboardingConfiguration(
            mode = OnboardingMode.Local,
            appMode = "launcher",
            provider = LlmProvider.Gemini,
            apiKey = "user-key",
            modelId = DEFAULT_GEMINI_MODEL,
            mongoConnectionUri = "mongodb://aura:secret@db.example.com:27017/?tls=true",
            mongoDatabaseName = "aura",
            backgroundListening = false
        )

        validateOnboardingConfiguration(valid)
        assertThrows(IllegalArgumentException::class.java) {
            validateOnboardingConfiguration(valid.copy(apiKey = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateOnboardingConfiguration(valid.copy(modelId = ""))
        }
    }

    @Test
    fun managedOnboardingDoesNotAcceptLocalCredentialsAsRequirements() {
        validateOnboardingConfiguration(
            OnboardingConfiguration(
                mode = OnboardingMode.ManagedGoogle,
                appMode = "launcher",
                provider = LlmProvider.Gemini,
                apiKey = "",
                modelId = "",
                mongoConnectionUri = "",
                mongoDatabaseName = "",
                backgroundListening = false
            )
        )
    }
}
