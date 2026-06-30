package com.aura.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidManifestCompatibilityTest {
    @Test
    fun launcherUiIsSplitIntoFocusedSourceFiles() {
        val uiDir = File("src/main/java/com/aura/app/ui")
        val launcherLines = File(uiDir, "AuraLauncherApp.kt").readLines().size
        val expectedFiles = listOf(
            "LauncherHomeUi.kt",
            "LauncherAppsScreen.kt",
            "LauncherAppsMiniAppsUi.kt",
            "LauncherFeatureScreens.kt",
            "LauncherAutomationScreen.kt",
            "LauncherSettingsUi.kt",
            "LauncherSharedUi.kt"
        )

        assertTrue(
            "AuraLauncherApp.kt should remain a route shell, not a giant all-in-one UI file",
            launcherLines < 1_000
        )
        expectedFiles.forEach { fileName ->
            assertTrue("$fileName should own a focused launcher UI boundary", File(uiDir, fileName).isFile)
        }
    }

    @Test
    fun appBackupIsDisabledForSecretBearingStores() {
        val application = readManifest().getElementsByTagName("application").item(0)

        assertEquals("false", application.attributes.getNamedItem("android:allowBackup")?.nodeValue)
    }

    @Test
    fun releaseNetworkStackDoesNotIncludeHttpBodyLoggingInterceptor() {
        val buildFile = File("build.gradle.kts").readText()

        assertFalse(
            "The app sends provider API keys in request bodies, so OkHttp logging must not be bundled",
            "logging-interceptor" in buildFile
        )
    }

    @Test
    fun launcherActivitiesDoNotLockPhoneOrientation() {
        val document = readManifest()
        val activities = document.getElementsByTagName("activity")

        for (index in 0 until activities.length) {
            val activity = activities.item(index)
            val attributes = activity.attributes
            val name = attributes.getNamedItem("android:name")?.nodeValue
            if (name == ".LauncherActivity" || name == ".MainActivity") {
                assertFalse(
                    "$name must adapt instead of locking orientation",
                    attributes.getNamedItem("android:screenOrientation") != null
                )
                assertEquals("true", attributes.getNamedItem("android:resizeableActivity")?.nodeValue)
            }
        }
    }

    @Test
    fun appDiscoveryUsesScopedLauncherQueries() {
        val document = readManifest()
        val permissions = document.getElementsByTagName("uses-permission")
        for (index in 0 until permissions.length) {
            val permission = permissions.item(index)
            val name = permission.attributes.getNamedItem("android:name")?.nodeValue
            assertFalse(
                "Launcher app discovery should use scoped queries instead of QUERY_ALL_PACKAGES",
                name == "android.permission.QUERY_ALL_PACKAGES"
            )
        }

        val queries = document.getElementsByTagName("queries")
        assertTrue("Manifest must declare scoped package visibility queries", queries.length > 0)
    }

    @Test
    fun automationAlarmsRestoreAfterBootAndWallClockChanges() {
        val document = readManifest()
        val receivers = document.getElementsByTagName("receiver")
        val bootReceiver = (0 until receivers.length)
            .map { receivers.item(it) }
            .first { it.attributes.getNamedItem("android:name")?.nodeValue == ".voice.BootReceiver" }
        val actions = bootReceiver.childNodes
            .let { children ->
                (0 until children.length)
                    .map { children.item(it) }
                    .flatMap { node ->
                        val descendants = node.childNodes
                        (0 until descendants.length).mapNotNull { index ->
                            descendants.item(index).attributes?.getNamedItem("android:name")?.nodeValue
                        }
                    }
                    .toSet()
            }

        assertEquals("false", bootReceiver.attributes.getNamedItem("android:exported")?.nodeValue)
        assertTrue("Boot restore action is required", "android.intent.action.BOOT_COMPLETED" in actions)
        assertTrue("App updates must restore triggers", "android.intent.action.MY_PACKAGE_REPLACED" in actions)
        assertTrue("Time changes must recalculate alarms", "android.intent.action.TIME_SET" in actions)
        assertTrue("Timezone changes must recalculate alarms", "android.intent.action.TIMEZONE_CHANGED" in actions)
    }

    private fun readManifest() =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
}
