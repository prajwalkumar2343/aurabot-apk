package com.aura.app.assistant

import com.aura.app.session.AuthTokenStore
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthSessionInterceptorTest {
    @Test
    fun recoversFromUnauthorizedResponseByRefreshingAndRetryingOnce() {
        val store = FakeTokenStore(accessTokenValue = "expired-access", refreshTokenValue = "refresh-1")
        val auth = FakeSupabaseAuthTransport(
            refreshedSession = SupabaseAuthSession(
                access_token = "fresh-access",
                refresh_token = "refresh-2"
            )
        )
        val terminal = ScriptedTerminalInterceptor { request ->
            when (request.url.encodedPath) {
                "/api/assistant/chat" -> {
                    when (request.header("Authorization")) {
                        "Bearer expired-access" -> TestResponse(code = 401)
                        "Bearer fresh-access" -> TestResponse(code = 200, body = """{"ok":true}""")
                        else -> TestResponse(code = 500)
                    }
                }
                else -> TestResponse(code = 404)
            }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthSessionInterceptor(store, auth))
            .addInterceptor(terminal)
            .build()

        val response = client.newCall(
            Request.Builder()
                .url("http://localhost/api/assistant/chat")
                .post("""{"message":"hi"}""".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        assertEquals(200, response.code)
        assertEquals("fresh-access", store.accessTokenValue)
        assertEquals("refresh-2", store.refreshTokenValue)
        assertEquals(listOf("refresh-1"), auth.refreshTokens)
        assertEquals(
            listOf(
                "Bearer expired-access",
                "Bearer fresh-access"
            ),
            terminal.requests.map { it.header("Authorization") }
        )
    }

    @Test
    fun clearsTokensWhenRefreshFails() {
        val store = FakeTokenStore(accessTokenValue = "expired-access", refreshTokenValue = "revoked-refresh")
        val auth = FakeSupabaseAuthTransport(refreshError = IllegalStateException("revoked"))
        val terminal = ScriptedTerminalInterceptor { request ->
            if (request.url.encodedPath == "/api/assistant/chat") TestResponse(code = 401)
            else TestResponse(code = 404)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthSessionInterceptor(store, auth))
            .addInterceptor(terminal)
            .build()

        val response = client.newCall(
            Request.Builder()
                .url("http://localhost/api/assistant/chat")
                .get()
                .build()
        ).execute()

        assertEquals(401, response.code)
        assertNull(store.accessTokenValue)
        assertNull(store.refreshTokenValue)
        assertEquals(listOf("revoked-refresh"), auth.refreshTokens)
        assertEquals(1, terminal.requests.size)
    }
}

private data class TestResponse(
    val code: Int,
    val body: String = """{"detail":"test"}"""
)

private class ScriptedTerminalInterceptor(
    private val handler: (Request) -> TestResponse
) : Interceptor {
    val requests = mutableListOf<Request>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += request
        val response = handler(request)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(response.code)
            .message(response.code.toString())
            .body(response.body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

private class FakeTokenStore(
    var accessTokenValue: String?,
    var refreshTokenValue: String?
) : AuthTokenStore {
    override suspend fun accessToken(): String? = accessTokenValue

    override suspend fun refreshToken(): String? = refreshTokenValue

    override suspend fun setTokens(accessToken: String?, refreshToken: String?) {
        accessTokenValue = accessToken
        refreshTokenValue = refreshToken
    }

    override suspend fun clearTokens() {
        accessTokenValue = null
        refreshTokenValue = null
    }
}

private class FakeSupabaseAuthTransport(
    private val refreshedSession: SupabaseAuthSession? = null,
    private val refreshError: Throwable? = null
) : SupabaseAuthTransport {
    val refreshTokens = mutableListOf<String>()

    override suspend fun exchangeGoogleIdToken(idToken: String, nonce: String): SupabaseAuthSession =
        error("Not used in this test")

    override suspend fun signInWithPassword(email: String, password: String): SupabaseAuthSession =
        error("Not used in this test")

    override suspend fun signUp(email: String, password: String, name: String?): SupabaseAuthSession =
        error("Not used in this test")

    override suspend fun refresh(refreshToken: String): SupabaseAuthSession {
        refreshTokens += refreshToken
        refreshError?.let { throw it }
        return requireNotNull(refreshedSession)
    }

    override suspend fun logout(accessToken: String) = Unit
}
