package com.aura.app.apps

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.appBlocksDataStore by preferencesDataStore(name = "aura_app_blocks")

data class AppBlockRule(
    val id: String,
    val packageName: String,
    val label: String,
    val blockedUntilMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean = blockedUntilMillis > nowMillis

    fun remainingMinutes(nowMillis: Long = System.currentTimeMillis()): Long =
        ((blockedUntilMillis - nowMillis).coerceAtLeast(0L) + 59_999L) / 60_000L
}

class AppBlockStore(private val context: Context) {
    private val gson = Gson()
    private val rulesKey = stringPreferencesKey("rules")

    val activeRules: Flow<List<AppBlockRule>> = context.appBlocksDataStore.data.map { prefs ->
        readRules(prefs[rulesKey]).filter { it.isActive() }
    }

    suspend fun blockApp(app: AppInfo, durationMinutes: Int): AppBlockRule {
        val now = System.currentTimeMillis()
        val rule = AppBlockRule(
            id = UUID.randomUUID().toString(),
            packageName = app.packageName,
            label = app.label,
            blockedUntilMillis = now + durationMinutes.coerceAtLeast(1) * 60_000L,
            createdAtMillis = now
        )
        context.appBlocksDataStore.edit { prefs ->
            val current = readRules(prefs[rulesKey]).filter {
                it.isActive(now) && it.packageName != app.packageName
            }
            prefs[rulesKey] = gson.toJson(listOf(rule) + current, rulesType)
        }
        return rule
    }

    suspend fun unblockPackage(packageName: String) {
        context.appBlocksDataStore.edit { prefs ->
            val current = readRules(prefs[rulesKey]).filter {
                it.isActive() && it.packageName != packageName
            }
            prefs[rulesKey] = gson.toJson(current, rulesType)
        }
    }

    private fun readRules(raw: String?): List<AppBlockRule> =
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            gson.fromJson(raw, rulesType) ?: emptyList()
        }

    private companion object {
        val rulesType = object : TypeToken<List<AppBlockRule>>() {}.type
    }
}
