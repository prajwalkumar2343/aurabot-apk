package com.aura.app.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mongodb.ConnectionString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localModeSettingsDataStore by preferencesDataStore(name = "aura_local_mode_settings")

data class LocalMongoSettingsState(
    val connectionUri: String = "",
    val databaseName: String = "",
    val credentialError: String? = null
) {
    val isConfigured: Boolean
        get() = connectionUri.isNotBlank() && databaseName.isNotBlank() && credentialError == null
}

internal interface LocalMongoSettingsReader {
    val state: Flow<LocalMongoSettingsState>
}

class LocalModeSettingsStore(context: Context) : LocalMongoSettingsReader {
    private val appContext = context.applicationContext
    private val secretCodec = AndroidKeystoreSecretCodec("aura_local_mongodb_credentials")
    private val connectionUriKey = stringPreferencesKey("mongodb_connection_uri")
    private val databaseNameKey = stringPreferencesKey("mongodb_database_name")

    override val state: Flow<LocalMongoSettingsState> = appContext.localModeSettingsDataStore.data.map { prefs ->
        val decodedUri = secretCodec.decode(prefs[connectionUriKey] ?: "")
        LocalMongoSettingsState(
            connectionUri = decodedUri.value,
            databaseName = prefs[databaseNameKey].orEmpty(),
            credentialError = if (decodedUri.readable) {
                null
            } else {
                "Stored MongoDB credentials could not be read. Re-enter them in setup."
            }
        )
    }

    suspend fun setMongoConnection(connectionUri: String, databaseName: String) {
        val normalized = validateLocalMongoSettings(connectionUri, databaseName)
        appContext.localModeSettingsDataStore.edit { prefs ->
            prefs[connectionUriKey] = secretCodec.encode(normalized.connectionUri)
            prefs[databaseNameKey] = normalized.databaseName
        }
    }
}

internal fun validateLocalMongoSettings(
    connectionUri: String,
    databaseName: String
): LocalMongoSettingsState {
    val uri = connectionUri.trim()
    require(uri.length <= MAX_MONGO_URI_LENGTH) { "MongoDB connection URI is too long." }
    require(uri.startsWith("mongodb://")) {
        "Use a standard mongodb:// seed-list URI. mongodb+srv:// is not supported on Android."
    }
    val normalizedUri = uri.lowercase()
    require(!normalizedUri.contains("tlsallowinvalidcertificates=true") &&
        !normalizedUri.contains("tlsallowinvalidhostnames=true") &&
        !normalizedUri.contains("tlsinsecure=true") &&
        !normalizedUri.contains("sslinvalidhostnameallowed=true")) {
        "MongoDB certificate or hostname verification cannot be disabled."
    }
    val parsed = runCatching { ConnectionString(uri) }
        .getOrElse { throw IllegalArgumentException("MongoDB connection URI is invalid", it) }
    val credential = parsed.credential
    require(credential != null && !credential.userName.isNullOrBlank() &&
        credential.password?.isNotEmpty() == true) {
        "MongoDB username and password are required."
    }
    require(parsed.sslEnabled == true) { "MongoDB TLS must be enabled with tls=true or ssl=true." }

    val db = databaseName.trim()
    require(db.isNotEmpty()) { "MongoDB database name is required." }
    require(db.length <= 64 && db.none { it in setOf('/', '\\', ' ', '"', '$', '*', '<', '>', ':', '|', '?') }) {
        "MongoDB database name contains unsupported characters."
    }
    return LocalMongoSettingsState(connectionUri = uri, databaseName = db)
}

private const val MAX_MONGO_URI_LENGTH = 4_096
