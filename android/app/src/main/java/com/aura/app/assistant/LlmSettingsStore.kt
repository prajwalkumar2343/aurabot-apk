package com.aura.app.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.llmSettingsDataStore by preferencesDataStore(name = "aura_llm_settings")
const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"

data class LlmSettingsState(
    val provider: LlmProvider = LlmProvider.Gemini,
    val googleApiKey: String = "",
    val googleModel: String = DEFAULT_GEMINI_MODEL,
    val googleApiKeyError: String? = null,
    val openAiApiKey: String = "",
    val openAiModel: String = "gpt-4.1-mini",
    val openAiApiKeyError: String? = null,
    val openRouterApiKey: String = "",
    val openRouterModel: String = "",
    val openRouterApiKeyError: String? = null
) {
    val currentApiKey: String
        get() = when (provider) {
            LlmProvider.Gemini -> googleApiKey
            LlmProvider.OpenAI -> openAiApiKey
            LlmProvider.OpenRouter -> openRouterApiKey
        }.trim()

    val currentApiKeyError: String?
        get() = when (provider) {
            LlmProvider.Gemini -> googleApiKeyError
            LlmProvider.OpenAI -> openAiApiKeyError
            LlmProvider.OpenRouter -> openRouterApiKeyError
        }

    val currentModel: String
        get() = when (provider) {
            LlmProvider.Gemini -> googleModel
            LlmProvider.OpenAI -> openAiModel
            LlmProvider.OpenRouter -> openRouterModel
        }.trim()
}

class LlmSettingsStore(private val context: Context) {
    private val secretCodec = AndroidKeystoreSecretCodec()
    private val providerKey = stringPreferencesKey("provider")
    private val googleApiKeyKey = stringPreferencesKey("google_api_key")
    private val googleModelKey = stringPreferencesKey("google_model")
    private val openAiApiKeyKey = stringPreferencesKey("openai_api_key")
    private val openAiModelKey = stringPreferencesKey("openai_model")
    private val openRouterApiKeyKey = stringPreferencesKey("openrouter_api_key")
    private val openRouterModelKey = stringPreferencesKey("openrouter_model")

    val state: Flow<LlmSettingsState> = context.llmSettingsDataStore.data.map { prefs ->
        val googleApiKey = secretCodec.decode(prefs[googleApiKeyKey] ?: "")
        val openAiApiKey = secretCodec.decode(prefs[openAiApiKeyKey] ?: "")
        val openRouterApiKey = secretCodec.decode(prefs[openRouterApiKeyKey] ?: "")
        LlmSettingsState(
            provider = LlmProvider.fromWireValue(prefs[providerKey]),
            googleApiKey = googleApiKey.value,
            googleModel = prefs[googleModelKey] ?: DEFAULT_GEMINI_MODEL,
            googleApiKeyError = googleApiKey.errorMessage("Google Gemini"),
            openAiApiKey = openAiApiKey.value,
            openAiModel = prefs[openAiModelKey] ?: "gpt-4.1-mini",
            openAiApiKeyError = openAiApiKey.errorMessage("OpenAI"),
            openRouterApiKey = openRouterApiKey.value,
            openRouterModel = prefs[openRouterModelKey] ?: "",
            openRouterApiKeyError = openRouterApiKey.errorMessage("OpenRouter")
        )
    }

    suspend fun setProvider(provider: LlmProvider) {
        context.llmSettingsDataStore.edit { prefs ->
            prefs[providerKey] = provider.wireValue
        }
    }

    suspend fun setGoogleApiKey(value: String) = setSecretString(googleApiKeyKey, value)

    suspend fun setGoogleModel(value: String) = setString(googleModelKey, value)

    suspend fun setOpenAiApiKey(value: String) = setSecretString(openAiApiKeyKey, value)

    suspend fun setOpenAiModel(value: String) = setString(openAiModelKey, value)

    suspend fun setOpenRouterApiKey(value: String) = setSecretString(openRouterApiKeyKey, value)

    suspend fun setOpenRouterModel(value: String) = setString(openRouterModelKey, value)

    private suspend fun setString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.llmSettingsDataStore.edit { prefs ->
            prefs[key] = value
        }
    }

    private suspend fun setSecretString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.llmSettingsDataStore.edit { prefs ->
            if (value.isBlank()) {
                prefs.remove(key)
            } else {
                prefs[key] = secretCodec.encode(value.trim())
            }
        }
    }
}

internal data class DecodedSecret(val value: String, val readable: Boolean) {
    fun errorMessage(providerLabel: String): String? =
        if (readable) null else "Stored $providerLabel API key could not be read. Re-enter it in Settings."
}

internal class AndroidKeystoreSecretCodec {
    fun encode(value: String): String {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(createIfMissing = true))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val packed = cipher.iv + encrypted
        return Prefix + Base64.getEncoder().encodeToString(packed)
    }

    fun decode(value: String): DecodedSecret {
        if (value.isBlank()) return DecodedSecret("", readable = true)
        if (!value.startsWith(Prefix)) return DecodedSecret(value, readable = true)
        return runCatching {
            val packed = Base64.getDecoder().decode(value.removePrefix(Prefix))
            require(packed.size > GcmIvBytes) { "Encrypted secret payload is too short" }
            val iv = packed.copyOfRange(0, GcmIvBytes)
            val encrypted = packed.copyOfRange(GcmIvBytes, packed.size)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(createIfMissing = false), GCMParameterSpec(GcmTagBits, iv))
            DecodedSecret(String(cipher.doFinal(encrypted), StandardCharsets.UTF_8), readable = true)
        }.getOrElse {
            DecodedSecret("", readable = false)
        }
    }

    private fun secretKey(createIfMissing: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        check(createIfMissing) { "Android Keystore entry is unavailable" }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val Prefix = "keystore:v1:"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "aura_llm_settings_api_keys"
        const val Transformation = "AES/GCM/NoPadding"
        const val GcmIvBytes = 12
        const val GcmTagBits = 128
    }
}
