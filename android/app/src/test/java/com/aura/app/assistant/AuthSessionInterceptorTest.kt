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
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthSessionInterceptorTest {
    @Test
    fun recoversFromUnauthorizedResponseByRefreshingAndRetryingOnce() {
        val store = FakeTokenStore(accessTokenValue = "expired-access", refreshTokenValue = "refresh-1")
        val terminal = ScriptedTerminalInterceptor { request ->
            when (request.url.encodedPath) {
                "/api/auth/refresh" -> {
                    assertEquals("""{"refresh_token":"refresh-1"}""", request.bodyString())
                    TestResponse(
                        code = 200,
                        body = """{"access_token":"fresh-access","refresh_token":"refresh-2"}"""
                    )
                }
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
            .addInterceptor(AuthSessionInterceptor(store))
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
        assertEquals(
            listOf(
                "Bearer expired-access",
                null,
                "Bearer fresh-access"
            ),
            terminal.requests.map { it.header("Authorization") }
        )
    }

    @Test
    fun clearsTokensWhenRefreshFails() {
        val store = FakeTokenStore(accessTokenValue = "expired-access", refreshTokenValue = "revoked-refresh")
        val terminal = ScriptedTerminalInterceptor { request ->
            when (request.url.encodedPath) {
                "/api/auth/refresh" -> TestResponse(code = 401)
                "/api/assistant/chat" -> TestResponse(code = 401)
                else -> TestResponse(code = 404)
            }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthSessionInterceptor(store))
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
        assertEquals(2, terminal.requests.size)
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

private fun Request.bodyString(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
