package com.aura.app.ui

import android.content.ComponentName
import com.aura.app.apps.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherUiStateTest {
    @Test
    fun filteredAppsMatchesLabelAndPackageName() {
        val state = LauncherUiState(
            appQuery = "clock",
            apps = listOf(
                AppInfo("Clock", "com.android.deskclock", ComponentName("com.android.deskclock", "Clock"), null),
                AppInfo("Camera", "com.android.camera", ComponentName("com.android.camera", "Camera"), null)
            )
        )

        assertEquals(listOf("Clock"), state.filteredApps.map { it.label })
    }

    @Test
    fun settingsShortcutCannotBeMistakenForAnExternalApp() {
        val settings = AppInfo(
            "Aura Settings",
            AppInfo.AURA_SETTINGS_SHORTCUT_PACKAGE,
            ComponentName("com.aura.app", "missing.SettingsActivity"),
            null
        )
        val clock = AppInfo(
            "Clock",
            "com.android.deskclock",
            ComponentName("com.android.deskclock", "Clock"),
            null
        )

        assertTrue(settings.isAuraSettingsShortcut)
        assertFalse(clock.isAuraSettingsShortcut)
    }
}
