package com.aura.app.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aura.app.assistant.AndroidKeystoreSecretCodec
import com.aura.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "aura_session")

data class SessionState(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val guestMode: Boolean = true,
    val onboardingComplete: Boolean = false,
    val backgroundListeningEnabled: Boolean = BuildConfig.AURA_ENABLE_BACKGROUND_LISTENING_DEFAULT,
    val homeSettingsPrompted: Boolean = false,
    val wallpaperUri: String? = null,
    val interactionMode: String = "eyes",
    val appMode: String = "launcher",
    val serviceMode: String = "local"
) {
    val isLoggedIn: Boolean = !accessToken.isNullOrBlank()
}

interface AuthTokenStore {
    suspend fun accessToken(): String?
    suspend fun refreshToken(): String?
    suspend fun setTokens(accessToken: String?, refreshToken: String?)
    suspend fun setAuthenticatedSession(
        accessToken: String,
        refreshToken: String?,
        serviceMode: String
    ) {
        setTokens(accessToken, refreshToken)
        setServiceMode(serviceMode)
    }
    suspend fun clearTokens()
    suspend fun serviceMode(): String = "local"
    suspend fun setServiceMode(mode: String) = Unit
}

class SessionStore(private val context: Context) : AuthTokenStore {
    private val tokenCodec = AndroidKeystoreSecretCodec("aura_session_tokens")
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val guestModeKey = booleanPreferencesKey("guest_mode")
    private val onboardingKey = booleanPreferencesKey("onboarding_complete")
    private val backgroundListeningKey = booleanPreferencesKey("background_listening_enabled")
    private val homeSettingsPromptedKey = booleanPreferencesKey("home_settings_prompted")
    private val wallpaperUriKey = stringPreferencesKey("wallpaper_uri")
    private val interactionModeKey = stringPreferencesKey("interaction_mode")
    private val appModeKey = stringPreferencesKey("app_mode")
    private val serviceModeKey = stringPreferencesKey("service_mode")

    val state: Flow<SessionState> = context.sessionDataStore.data.map { prefs ->
        val access = tokenCodec.decode(prefs[accessTokenKey] ?: "")
        val refresh = tokenCodec.decode(prefs[refreshTokenKey] ?: "")
        val token = access.value.takeIf { access.readable }
        SessionState(
            accessToken = token,
            refreshToken = refresh.value.takeIf { refresh.readable },
            guestMode = prefs[guestModeKey] ?: token.isNullOrBlank(),
            onboardingComplete = prefs[onboardingKey] ?: false,
            backgroundListeningEnabled = prefs[backgroundListeningKey]
                ?: BuildConfig.AURA_ENABLE_BACKGROUND_LISTENING_DEFAULT,
            homeSettingsPrompted = prefs[homeSettingsPromptedKey] ?: false,
            wallpaperUri = prefs[wallpaperUriKey],
            interactionMode = prefs[interactionModeKey] ?: "eyes",
            // Aura is a Home launcher. Keep reading the legacy preference store so old
            // installs migrate without losing any neighboring settings, but do not
            // resurrect the retired normal/overlay product modes.
            appMode = "launcher",
            serviceMode = prefs[serviceModeKey] ?: "local"
        )
    }

    override suspend fun accessToken(): String? = state.first().accessToken

    override suspend fun refreshToken(): String? = state.first().refreshToken

    suspend fun setToken(token: String?) {
        setTokens(token, null)
    }

    override suspend fun setTokens(accessToken: String?, refreshToken: String?) {
        context.sessionDataStore.edit { prefs ->
            if (accessToken.isNullOrBlank()) {
                prefs.remove(accessTokenKey)
                prefs.remove(refreshTokenKey)
                prefs[guestModeKey] = true
            } else {
                prefs[accessTokenKey] = tokenCodec.encode(accessToken)
                if (refreshToken.isNullOrBlank()) {
                    prefs.remove(refreshTokenKey)
                } else {
                    prefs[refreshTokenKey] = tokenCodec.encode(refreshToken)
                }
                prefs[guestModeKey] = false
            }
        }
    }

    override suspend fun setAuthenticatedSession(
        accessToken: String,
        refreshToken: String?,
        serviceMode: String
    ) {
        require(accessToken.isNotBlank()) { "Access token is required" }
        context.sessionDataStore.edit { prefs ->
            prefs[accessTokenKey] = tokenCodec.encode(accessToken)
            if (refreshToken.isNullOrBlank()) {
                prefs.remove(refreshTokenKey)
            } else {
                prefs[refreshTokenKey] = tokenCodec.encode(refreshToken)
            }
            prefs[serviceModeKey] = normalizeServiceMode(serviceMode)
            prefs[guestModeKey] = false
        }
    }

    override suspend fun clearTokens() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(accessTokenKey)
            prefs.remove(refreshTokenKey)
            prefs[serviceModeKey] = "local"
            prefs[guestModeKey] = true
        }
    }

    override suspend fun serviceMode(): String = state.first().serviceMode

    override suspend fun setServiceMode(mode: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[serviceModeKey] = normalizeServiceMode(mode)
        }
    }

    suspend fun setBackgroundListeningEnabled(enabled: Boolean) {
        context.sessionDataStore.edit { prefs ->
            prefs[backgroundListeningKey] = enabled
        }
    }

    suspend fun setHomeSettingsPrompted(prompted: Boolean) {
        context.sessionDataStore.edit { prefs ->
            prefs[homeSettingsPromptedKey] = prompted
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.sessionDataStore.edit { prefs ->
            prefs[onboardingKey] = complete
        }
    }

    suspend fun setWallpaperUri(uri: String?) {
        context.sessionDataStore.edit { prefs ->
            if (uri == null) {
                prefs.remove(wallpaperUriKey)
            } else {
                prefs[wallpaperUriKey] = uri
            }
        }
    }

    suspend fun setInteractionMode(mode: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[interactionModeKey] = mode
        }
    }

    suspend fun setAppMode(mode: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[appModeKey] = "launcher"
        }
    }

    private fun normalizeServiceMode(mode: String): String =
        if (mode == "managed") "managed" else "local"
}
