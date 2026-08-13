package com.aura.app.assistant

import com.aura.app.session.AuthTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal class AuthSessionInterceptor(
    private val sessionStore: AuthTokenStore,
    private val authTransport: SupabaseAuthTransport? = null
) : Interceptor {
    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accessToken = runBlocking { sessionStore.accessToken() }
        val authenticatedRequest = originalRequest.withBearer(accessToken)
        val response = chain.proceed(authenticatedRequest)
        if (response.code != 401) {
            return response
        }

        val retryAccessToken = synchronized(refreshLock) {
            runBlocking {
                val currentAccessToken = sessionStore.accessToken()
                if (!currentAccessToken.isNullOrBlank() && currentAccessToken != accessToken) {
                    currentAccessToken
                } else {
                    refreshAccessToken()
                }
            }
        } ?: return response

        response.close()
        return chain.proceed(originalRequest.withBearer(retryAccessToken))
    }

    private suspend fun refreshAccessToken(): String? {
        val refreshToken = sessionStore.refreshToken()
        val transport = authTransport
        if (refreshToken.isNullOrBlank() || transport == null) {
            sessionStore.clearTokens()
            return null
        }

        val refreshed = runCatching { transport.refresh(refreshToken) }.getOrNull()
        if (refreshed == null) {
            sessionStore.clearTokens()
            return null
        }
        sessionStore.setTokens(refreshed.access_token, refreshed.refresh_token)
        return refreshed.access_token
    }

    private fun Request.withBearer(token: String?): Request =
        if (token.isNullOrBlank()) {
            this
        } else {
            newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

}
