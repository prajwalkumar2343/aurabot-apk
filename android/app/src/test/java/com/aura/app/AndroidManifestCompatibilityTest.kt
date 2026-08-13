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
            "LauncherAuraWidgetsUi.kt",
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
        assertEquals(
            "@xml/backup_rules",
            application.attributes.getNamedItem("android:fullBackupContent")?.nodeValue
        )
        assertEquals(
            "@xml/data_extraction_rules",
            application.attributes.getNamedItem("android:dataExtractionRules")?.nodeValue
        )
        assertTrue(File("src/main/res/xml/backup_rules.xml").readText().contains("domain=\"sharedpref\""))
        assertTrue(File("src/main/res/xml/data_extraction_rules.xml").readText().contains("<device-transfer>"))
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
    fun releaseBuildRejectsDebugBackendUrlBeforeStartup() {
        val buildFile = File("build.gradle.kts").readText()
        val repositoryFile = File("src/main/java/com/aura/app/assistant/AssistantRepository.kt").readText()

        assertTrue(
            "Release builds must validate Stalky and Supabase configuration before the APK can be produced",
            "validateReleaseBackendUrl" in buildFile &&
                "preReleaseBuild" in buildFile &&
                "stalkyApiUrl" in buildFile &&
                "stalkySupabaseUrl" in buildFile &&
                "startsWith(\"https://\")" in buildFile
        )
        assertFalse(
            "AssistantRepository construction must not crash app startup for a misconfigured URL",
            "Aura backend URL must use HTTPS outside debug builds" in repositoryFile
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
    fun sideloadManifestRemovesDirectSmsAndPrivilegedAutomation() {
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/sideload/AndroidManifest.xml"))
        val permissions = manifest.getElementsByTagName("uses-permission")
        val smsPermission = (0 until permissions.length)
            .map { permissions.item(it) }
            .single { it.attributes.getNamedItem("android:name")?.nodeValue == "android.permission.SEND_SMS" }
        assertEquals("remove", smsPermission.attributes.getNamedItem("tools:node")?.nodeValue)

        val mainManifest = readManifest()
        val services = mainManifest.getElementsByTagName("service")
        assertFalse(
            "The app must not expose a privileged accessibility automation service",
            (0 until services.length).map { services.item(it) }.any {
                it.attributes.getNamedItem("android:name")?.nodeValue ==
                    ".automations.AuraAutomationAccessibilityService"
            }
        )
        assertFalse(
            "The app must not request or bind an accessibility service",
            (0 until services.length).map { services.item(it) }.any {
                it.attributes.getNamedItem("android:permission")?.nodeValue ==
                    "android.permission.BIND_ACCESSIBILITY_SERVICE"
            }
        )

        val mainPermissions = mainManifest.getElementsByTagName("uses-permission")
        assertFalse(
            "Unused privileged profile access must not be requested",
            (0 until mainPermissions.length).map { mainPermissions.item(it) }.any {
                it.attributes.getNamedItem("android:name")?.nodeValue ==
                    "android.permission.ACCESS_HIDDEN_PROFILES"
            }
        )
    }

    @Test
    fun launcherUsesUserMediatedWidgetBindingAndPinConfirmationEntryPoint() {
        val document = readManifest()
        val permissions = document.getElementsByTagName("uses-permission")
        val permissionNames = (0 until permissions.length).mapNotNull { index ->
            permissions.item(index).attributes.getNamedItem("android:name")?.nodeValue
        }
        assertFalse(
            "BIND_APPWIDGET is protected and cannot be granted to a normal installed APK",
            "android.permission.BIND_APPWIDGET" in permissionNames
        )

        val activities = document.getElementsByTagName("activity")
        val launcher = (0 until activities.length)
            .map { activities.item(it) }
            .first { it.attributes.getNamedItem("android:name")?.nodeValue == ".LauncherActivity" }
        val actions = launcher.childNodes.let { children ->
            (0 until children.length)
                .map { children.item(it) }
                .flatMap { node ->
                    val descendants = node.childNodes
                    (0 until descendants.length).mapNotNull { index ->
                        descendants.item(index).attributes
                            ?.getNamedItem("android:name")
                            ?.nodeValue
                    }
                }
                .toSet()
        }
        assertTrue(
            "The default launcher must handle app-requested pinned widgets",
            "android.content.pm.action.CONFIRM_PIN_APPWIDGET" in actions
        )
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

    @Test
    fun automationDeliveryAndApprovalReceiversArePrivate() {
        val receivers = readManifest().getElementsByTagName("receiver")
        val automationReceivers = (0 until receivers.length)
            .map { receivers.item(it) }
            .filter {
                it.attributes.getNamedItem("android:name")?.nodeValue in setOf(
                    ".automations.GeofenceTransitionReceiver",
                    ".automations.ScheduleAutomationReceiver",
                    ".automations.AutomationFlowContinuationReceiver",
                    ".automations.AutomationCheckpointDecisionReceiver"
                )
            }

        assertEquals(4, automationReceivers.size)
        assertTrue(
            "Automation receivers must reject direct calls from other apps",
            automationReceivers.all { it.attributes.getNamedItem("android:exported")?.nodeValue == "false" }
        )
    }

    private fun readManifest() =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
}
