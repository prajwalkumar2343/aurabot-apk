package com.aura.app

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aura.app.session.SessionStore
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherResponsivePhoneSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun prepareSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            SessionStore(context).setOnboardingComplete(true)
            SessionStore(context).setHomeSettingsPrompted(true)
            SessionStore(context).setAppMode("normal")
        }
    }

    @After
    fun resetDisplayOverrides() {
        executeShellCommand("wm size reset")
        executeShellCommand("wm density reset")
    }

    @Test
    fun launcherHomeRendersAcrossPhoneWindowSizes() {
        listOf(
            "320x640",
            "390x844",
            "520x900",
            "844x390"
        ).forEach { size ->
            executeShellCommand("wm size $size")

            ActivityScenario.launch(LauncherActivity::class.java).use {
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("aura-home-screen").assertIsDisplayed()
            }
        }
    }

    private fun executeShellCommand(command: String): String {
        val descriptor: ParcelFileDescriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use(InputStream::readAllBytes)
            .decodeToString()
    }
}
