package com.aura.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StalkyConfigurationTest {
    @Test
    fun absentConfigurationDisablesManagedTransport() {
        assertNull(
            StalkyCloudConfiguration.fromRaw(
                apiUrl = "",
                supabaseUrl = "",
                publishableKey = "",
                release = false
            )
        )
    }

    @Test
    fun configuredUrlsAreNormalizedAndLocalHttpIsLimitedToDevelopmentHosts() {
        val configuration = StalkyCloudConfiguration.fromRaw(
            apiUrl = "http://10.0.2.2:8080/",
            supabaseUrl = "http://127.0.0.1:54321/",
            publishableKey = "sb_publishable_test",
            release = false
        )

        assertEquals("http://10.0.2.2:8080", configuration?.apiUrl)
        assertEquals("http://127.0.0.1:54321", configuration?.supabaseUrl)
        assertThrows(IllegalArgumentException::class.java) {
            StalkyCloudConfiguration.fromRaw(
                apiUrl = "http://api.stalky.app",
                supabaseUrl = "https://project.supabase.co",
                publishableKey = "sb_publishable_test",
                release = false
            )
        }
    }

    @Test
    fun releaseRequiresHttpsAndCompletePublicConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            StalkyCloudConfiguration.fromRaw(
                apiUrl = "http://api.stalky.app",
                supabaseUrl = "https://project.supabase.co",
                publishableKey = "sb_publishable_test",
                release = true
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StalkyCloudConfiguration.fromRaw(
                apiUrl = "https://api.stalky.app",
                supabaseUrl = "",
                publishableKey = "sb_publishable_test",
                release = true
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StalkyCloudConfiguration.fromRaw(
                apiUrl = "https://api.stalky.app",
                supabaseUrl = "https://project.supabase.co",
                publishableKey = "sb publishable test",
                release = true
            )
        }
    }

    @Test
    fun baseUrlRejectsCredentialsQueriesAndNonRootPaths() {
        listOf(
            "https://user:password@api.stalky.app",
            "https://api.stalky.app?token=secret",
            "https://api.stalky.app/v1"
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                validateStalkyBaseUrl(url, "Stalky API URL", release = true)
            }
        }
    }
}
