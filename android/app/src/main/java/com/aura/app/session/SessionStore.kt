package com.aura.app.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aura.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "aura_session")

data class SessionState(
    val accessToken: String? = null,
    val guestMode: Boolean = true,
    val onboardingComplete: Boolean = false,
    val backgroundListeningEnabled: Boolean = BuildConfig.AURA_ENABLE_BACKGROUND_LISTENING_DEFAULT,
    val homeSettingsPrompted: Boolean = false,
    val wallpaperUri: String? = null,
    val interactionMode: String = "eyes"
) {
    val isLoggedIn: Boolean = !accessToken.isNullOrBlank()
}

class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("access_token")
    private val guestModeKey = booleanPreferencesKey("guest_mode")
    private val onboardingKey = booleanPreferencesKey("onboarding_complete")
    private val backgroundListeningKey = booleanPreferencesKey("background_listening_enabled")
    private val homeSettingsPromptedKey = booleanPreferencesKey("home_settings_prompted")
    private val wallpaperUriKey = stringPreferencesKey("wallpaper_uri")
    private val interactionModeKey = stringPreferencesKey("interaction_mode")

    val state: Flow<SessionState> = context.sessionDataStore.data.map { prefs ->
        val token = prefs[tokenKey]
        SessionState(
            accessToken = token,
            guestMode = prefs[guestModeKey] ?: token.isNullOrBlank(),
            onboardingComplete = prefs[onboardingKey] ?: false,
            backgroundListeningEnabled = prefs[backgroundListeningKey]
                ?: BuildConfig.AURA_ENABLE_BACKGROUND_LISTENING_DEFAULT,
            homeSettingsPrompted = prefs[homeSettingsPromptedKey] ?: false,
            wallpaperUri = prefs[wallpaperUriKey],
            interactionMode = prefs[interactionModeKey] ?: "eyes"
        )
    }

    suspend fun accessToken(): String? = state.first().accessToken

    suspend fun setToken(token: String?) {
        context.sessionDataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(tokenKey)
                prefs[guestModeKey] = true
            } else {
                prefs[tokenKey] = token
                prefs[guestModeKey] = false
                prefs[onboardingKey] = true
            }
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
            if (prompted) {
                prefs[onboardingKey] = true
            }
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
}
