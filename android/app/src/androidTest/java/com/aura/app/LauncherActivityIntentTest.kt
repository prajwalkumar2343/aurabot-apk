package com.aura.app

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherActivityIntentTest {
    @Test
    fun resolvesAsHomeLauncher() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val matches = context.packageManager.queryIntentActivities(intent, 0)

        assertTrue(matches.any { it.activityInfo.packageName == context.packageName })
    }
}
