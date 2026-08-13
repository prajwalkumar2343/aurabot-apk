package com.aura.app.assistant

import java.net.UnknownHostException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AssistantErrorsTest {
    @Test
    fun networkFailureHasAnActionableMessage() {
        assertEquals(
            "Aura can't reach its backend. Check your connection and try again.",
            UnknownHostException("internal-host").userFacingMessage("Failed")
        )
    }

    @Test
    fun backendDetailIsPreservedWithoutShowingRawHttpStatus() {
        val response = Response.error<Any>(
            401,
            "{\"detail\":\"Invalid email or password\"}".toResponseBody()
        )

        assertEquals(
            "Invalid email or password",
            HttpException(response).userFacingMessage("Could not sign in")
        )
    }

    @Test
    fun malformedServerErrorFallsBackToStableCopy() {
        val response = Response.error<Any>(503, "not-json".toResponseBody())

        assertEquals(
            "Aura's backend is temporarily unavailable.",
            HttpException(response).userFacingMessage("Assistant failed")
        )
    }
}
