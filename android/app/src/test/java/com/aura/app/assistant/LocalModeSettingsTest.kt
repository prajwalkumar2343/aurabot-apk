package com.aura.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalModeSettingsTest {
    @Test
    fun acceptsDedicatedTlsMongoConnection() {
        val settings = validateLocalMongoSettings(
            " mongodb://aura:secret@db.example.com:27017/?tls=true ",
            " aura_local "
        )

        assertEquals("mongodb://aura:secret@db.example.com:27017/?tls=true", settings.connectionUri)
        assertEquals("aura_local", settings.databaseName)
    }

    @Test
    fun rejectsSrvAndUnencryptedConnections() {
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb+srv://aura:secret@cluster.example.com/aura",
                "aura"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb://aura:secret@db.example.com:27017/aura",
                "aura"
            )
        }
    }

    @Test
    fun rejectsMissingCredentialsAndTlsBypasses() {
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb://db.example.com:27017/?tls=true",
                "aura"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateLocalMongoSettings(
                "mongodb://aura:secret@db.example.com:27017/?tls=true&tlsInsecure=true",
                "aura"
            )
        }
    }
}
