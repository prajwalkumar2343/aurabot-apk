package com.aura.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidManifestCompatibilityTest {
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

    private fun readManifest() =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
}
