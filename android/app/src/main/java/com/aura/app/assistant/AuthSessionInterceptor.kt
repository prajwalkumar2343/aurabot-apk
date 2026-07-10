package com.aura.app.assistant

import com.aura.app.session.AuthTokenStore
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class AuthSessionInterceptor(
    private val sessionStore: AuthTokenStore,
    private val gson: Gson = Gson()
) : Interceptor {
    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accessToken = runBlocking { sessionStore.accessToken() }
        val authenticatedRequest = originalRequest.withBearer(accessToken)
        val response = chain.proceed(authenticatedRequest)
        if (response.code != 401 || originalRequest.isAuthSessionRequest()) {
            return response
        }

        val retryAccessToken = synchronized(refreshLock) {
            runBlocking {
                val currentAccessToken = sessionStore.accessToken()
                if (!currentAccessToken.isNullOrBlank() && currentAccessToken != accessToken) {
                    currentAccessToken
                } else {
                    refreshAccessToken(chain, originalRequest)
                }
            }
        } ?: return response

        response.close()
        return chain.proceed(originalRequest.withBearer(retryAccessToken))
    }

    private suspend fun refreshAccessToken(chain: Interceptor.Chain, originalRequest: Request): String? {
        val refreshToken = sessionStore.refreshToken()
        if (refreshToken.isNullOrBlank()) {
            sessionStore.clearTokens()
            return null
        }

        val refreshBody = gson.toJson(RefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val refreshRequest = originalRequest.newBuilder()
            .url(originalRequest.url.newBuilder().encodedPath("/api/auth/refresh").build())
            .post(refreshBody)
            .removeHeader("Authorization")
            .build()

        val refreshResponse = chain.proceed(refreshRequest)
        refreshResponse.use { response ->
            if (!response.isSuccessful) {
                sessionStore.clearTokens()
                return null
            }
            val body = response.body?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(body, RefreshResponse::class.java) }.getOrNull()
            val newAccessToken = parsed?.access_token
            val newRefreshToken = parsed?.refresh_token
            if (newAccessToken.isNullOrBlank() || newRefreshToken.isNullOrBlank()) {
                sessionStore.clearTokens()
                return null
            }
            sessionStore.setTokens(newAccessToken, newRefreshToken)
            return newAccessToken
        }
    }

    private fun Request.withBearer(token: String?): Request =
        if (token.isNullOrBlank()) {
            this
        } else {
            newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

    private fun Request.isAuthSessionRequest(): Boolean {
        val path = url.encodedPath
        return path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/logout")
    }
}
