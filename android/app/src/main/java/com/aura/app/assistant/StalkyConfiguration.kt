package com.aura.app.assistant

import com.aura.app.BuildConfig
import java.net.URI

/**
 * Build-time configuration for the managed Stalky path.
 *
 * The publishable key is intentionally the only Supabase credential accepted
 * here. It is public client configuration; service-role or JWT signing secrets
 * must never be placed in the APK.
 */
data class StalkyCloudConfiguration(
    val apiUrl: String,
    val supabaseUrl: String,
    val publishableKey: String
) {
    companion object {
        internal fun fromBuildConfig(): StalkyCloudConfiguration? = runCatching {
            fromRaw(
                apiUrl = BuildConfig.STALKY_API_URL,
                supabaseUrl = BuildConfig.STALKY_SUPABASE_URL,
                publishableKey = BuildConfig.STALKY_SUPABASE_PUBLISHABLE_KEY,
                release = !BuildConfig.DEBUG
            )
        }.getOrNull()

        internal fun fromRaw(
            apiUrl: String,
            supabaseUrl: String,
            publishableKey: String,
            release: Boolean
        ): StalkyCloudConfiguration? {
            val normalizedApiUrl = apiUrl.trim()
            val normalizedSupabaseUrl = supabaseUrl.trim()
            val normalizedPublishableKey = publishableKey.trim()
            if (normalizedApiUrl.isEmpty() &&
                normalizedSupabaseUrl.isEmpty() &&
                normalizedPublishableKey.isEmpty()
            ) {
                return null
            }
            require(normalizedApiUrl.isNotEmpty() && normalizedSupabaseUrl.isNotEmpty()) {
                "Stalky Cloud configuration is incomplete."
            }
            require(normalizedPublishableKey.isNotEmpty()) {
                "Supabase publishable key is required for managed mode."
            }
            return StalkyCloudConfiguration(
                apiUrl = validateBaseUrl(normalizedApiUrl, "Stalky API URL", release),
                supabaseUrl = validateBaseUrl(normalizedSupabaseUrl, "Supabase URL", release),
                publishableKey = validatePublishableKey(normalizedPublishableKey)
            )
        }
    }
}

internal fun validateStalkyBaseUrl(raw: String, fieldName: String, release: Boolean): String =
    validateBaseUrl(raw.trim(), fieldName, release)

private fun validateBaseUrl(raw: String, fieldName: String, release: Boolean): String {
    require(raw.isNotEmpty()) { "$fieldName is required." }
    val parsed = runCatching { URI(raw) }
        .getOrElse { throw IllegalArgumentException("$fieldName is invalid.", it) }
    val host = parsed.host?.lowercase()?.removePrefix("[")?.removeSuffix("]")
    require(parsed.scheme in setOf("http", "https") && !host.isNullOrBlank()) {
        "$fieldName must use an http(s) URL."
    }
    require(parsed.userInfo == null && parsed.query == null && parsed.fragment == null) {
        "$fieldName must not contain credentials, query parameters, or fragments."
    }
    require(parsed.path.isEmpty() || parsed.path == "/") {
        "$fieldName must point to a host root."
    }
    if (release) {
        require(parsed.scheme == "https") { "$fieldName must use HTTPS in release builds." }
    } else if (parsed.scheme == "http") {
        require(host in LOCAL_DEVELOPMENT_HOSTS) {
            "$fieldName may use HTTP only for a local development host."
        }
    }
    return raw.trimEnd('/')
}

private fun validatePublishableKey(raw: String): String {
    require(raw.length <= MAX_PUBLISHABLE_KEY_LENGTH && raw.none(Char::isWhitespace)) {
        "Supabase publishable key is invalid."
    }
    return raw
}

private val LOCAL_DEVELOPMENT_HOSTS = setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")
private const val MAX_PUBLISHABLE_KEY_LENGTH = 4_096
