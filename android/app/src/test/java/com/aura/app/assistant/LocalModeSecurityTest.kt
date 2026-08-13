package com.aura.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalModeSecurityTest {
    @Test
    fun mongoSettingsRequireCredentialsAndVerifiedTls() {
        val settings = validateLocalMongoSettings(
            "mongodb://aura_user:secret@db.example.com:27017/?tls=true",
            "aura_private"
        )

        assertEquals("aura_private", settings.databaseName)
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb://db.example.com:27017/?tls=true",
                "aura_private"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb://aura_user:secret@db.example.com:27017/",
                "aura_private"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb://aura_user:secret@db.example.com:27017/?tls=true&tlsAllowInvalidCertificates=true",
                "aura_private"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb://aura_user:secret@db.example.com:27017/?tls=true&tlsAllowInvalidHostnames=true",
                "aura_private"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb://aura_user@db.example.com:27017/?tls=true",
                "aura_private"
            )
        }
    }

    @Test
    fun mongoSettingsRejectSrvBecauseCurrentAndroidTransportCannotResolveItSafely() {
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb+srv://aura_user:secret@cluster.example.com/?tls=true",
                "aura_private"
            )
        }
    }

    @Test
    fun localAssistantResponseBoundsAndParsesActions() {
        val response = parseLocalAssistantResponse(
            """{"reply":"{neutral} Ready","emotion":"neutral","actions":[{"type":"open_app","package_name":"com.example"}]}""",
            "session-1"
        )

        assertEquals("{neutral} Ready", response.reply)
        assertEquals("session-1", response.session_id)
        assertEquals("open_app", response.actions.single().type)
        assertEquals("com.example", response.actions.single().package_name)
    }

    @Test
    fun inlinePayloadValidationRejectsUnsupportedAndMalformedAttachments() {
        assertThrows(IllegalArgumentException::class.java) {
            validateInlinePayload("AAAA", "text/html", setOf("image/png"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateInlinePayload("not base64", "image/png", setOf("image/png"))
        }
        validateInlinePayload("AAAA", "image/png; charset=binary", setOf("image/png"))
    }
}
