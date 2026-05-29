package com.aura.app.ui

import android.content.ComponentName
import com.aura.app.apps.AppInfo
import org.junit.Assert.assertEquals
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
}
