package com.aura.app.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.llmSettingsDataStore by preferencesDataStore(name = "aura_llm_settings")
const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"

data class LlmSettingsState(
    val provider: LlmProvider = LlmProvider.Gemini,
    val googleApiKey: String = "",
    val googleModel: String = DEFAULT_GEMINI_MODEL,
    val openAiApiKey: String = "",
    val openAiModel: String = "gpt-4.1-mini",
    val openRouterApiKey: String = "",
    val openRouterModel: String = ""
) {
    val currentApiKey: String
        get() = when (provider) {
            LlmProvider.Gemini -> googleApiKey
            LlmProvider.OpenAI -> openAiApiKey
            LlmProvider.OpenRouter -> openRouterApiKey
        }.trim()

    val currentModel: String
        get() = when (provider) {
            LlmProvider.Gemini -> googleModel
            LlmProvider.OpenAI -> openAiModel
            LlmProvider.OpenRouter -> openRouterModel
        }.trim()
}

class LlmSettingsStore(private val context: Context) {
    private val providerKey = stringPreferencesKey("provider")
    private val googleApiKeyKey = stringPreferencesKey("google_api_key")
    private val googleModelKey = stringPreferencesKey("google_model")
    private val openAiApiKeyKey = stringPreferencesKey("openai_api_key")
    private val openAiModelKey = stringPreferencesKey("openai_model")
    private val openRouterApiKeyKey = stringPreferencesKey("openrouter_api_key")
    private val openRouterModelKey = stringPreferencesKey("openrouter_model")

    val state: Flow<LlmSettingsState> = context.llmSettingsDataStore.data.map { prefs ->
        LlmSettingsState(
            provider = LlmProvider.fromWireValue(prefs[providerKey]),
            googleApiKey = prefs[googleApiKeyKey] ?: "",
            googleModel = prefs[googleModelKey] ?: DEFAULT_GEMINI_MODEL,
            openAiApiKey = prefs[openAiApiKeyKey] ?: "",
            openAiModel = prefs[openAiModelKey] ?: "gpt-4.1-mini",
            openRouterApiKey = prefs[openRouterApiKeyKey] ?: "",
            openRouterModel = prefs[openRouterModelKey] ?: ""
        )
    }

    suspend fun setProvider(provider: LlmProvider) {
        context.llmSettingsDataStore.edit { prefs ->
            prefs[providerKey] = provider.wireValue
        }
    }

    suspend fun setGoogleApiKey(value: String) = setString(googleApiKeyKey, value)

    suspend fun setGoogleModel(value: String) = setString(googleModelKey, value)

    suspend fun setOpenAiApiKey(value: String) = setString(openAiApiKeyKey, value)

    suspend fun setOpenAiModel(value: String) = setString(openAiModelKey, value)

    suspend fun setOpenRouterApiKey(value: String) = setString(openRouterApiKeyKey, value)

    suspend fun setOpenRouterModel(value: String) = setString(openRouterModelKey, value)

    private suspend fun setString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.llmSettingsDataStore.edit { prefs ->
            prefs[key] = value
        }
    }
}
