package com.aura.app.dreams

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dreamSettingsDataStore by preferencesDataStore(name = "aura_dream_settings")

data class DreamSettings(
    val enabled: Boolean = false,
    val requiresCharging: Boolean = true,
    val requiresDeviceIdle: Boolean = true,
    val requireBatteryNotLow: Boolean = true,
    val maxProposals: Int = 5,
    val signalRetentionDays: Int = 7
)

class DreamSettingsStore(private val context: Context) {
    val state: Flow<DreamSettings> = context.dreamSettingsDataStore.data.map { preferences ->
        DreamSettings(
            enabled = preferences[enabledKey] ?: false,
            requiresCharging = preferences[requiresChargingKey] ?: true,
            requiresDeviceIdle = preferences[requiresDeviceIdleKey] ?: true,
            requireBatteryNotLow = preferences[requireBatteryNotLowKey] ?: true,
            maxProposals = (preferences[maxProposalsKey] ?: 5).coerceIn(1, 10),
            signalRetentionDays = (preferences[retentionDaysKey] ?: 7).coerceIn(1, 30)
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dreamSettingsDataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setRequiresCharging(required: Boolean) {
        context.dreamSettingsDataStore.edit { it[requiresChargingKey] = required }
    }

    suspend fun setRequiresDeviceIdle(required: Boolean) {
        context.dreamSettingsDataStore.edit { it[requiresDeviceIdleKey] = required }
    }

    suspend fun setRequireBatteryNotLow(required: Boolean) {
        context.dreamSettingsDataStore.edit { it[requireBatteryNotLowKey] = required }
    }

    private companion object {
        val enabledKey = booleanPreferencesKey("enabled")
        val requiresChargingKey = booleanPreferencesKey("requires_charging")
        val requiresDeviceIdleKey = booleanPreferencesKey("requires_device_idle")
        val requireBatteryNotLowKey = booleanPreferencesKey("require_battery_not_low")
        val maxProposalsKey = intPreferencesKey("max_proposals")
        val retentionDaysKey = intPreferencesKey("signal_retention_days")
    }
}
