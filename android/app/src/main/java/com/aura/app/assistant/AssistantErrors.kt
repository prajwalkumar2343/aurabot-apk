package com.aura.app.assistant

import com.google.gson.JsonParser
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import retrofit2.HttpException

internal fun Throwable.userFacingMessage(fallback: String): String = when (this) {
    is UnknownHostException, is ConnectException ->
        "Aura can't reach its backend. Check your connection and try again."
    is SocketTimeoutException ->
        "Aura's backend took too long to respond. Please try again."
    is SSLException ->
        "Aura couldn't establish a secure backend connection."
    is HttpException -> backendDetail()
        ?: when (code()) {
            401 -> "Your session is no longer valid. Sign in again."
            403 -> "Your account doesn't have permission to do that."
            404 -> "That Aura service is not available on this backend."
            408, 429 -> "Aura is receiving too many requests. Please try again shortly."
            in 500..599 -> "Aura's backend is temporarily unavailable."
            else -> fallback
        }
    else -> message?.takeIf { it.isNotBlank() } ?: fallback
}

private fun HttpException.backendDetail(): String? {
    val body = response()?.errorBody()?.string()?.take(8_192) ?: return null
    return runCatching {
        JsonParser.parseString(body)
            .takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("detail")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(300)
    }.getOrNull()
}
