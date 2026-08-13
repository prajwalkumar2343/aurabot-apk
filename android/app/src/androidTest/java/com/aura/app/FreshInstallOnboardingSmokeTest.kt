package com.aura.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aura.app.session.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreshInstallOnboardingSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun prepareFreshSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            SessionStore(context).clearTokens()
            SessionStore(context).setOnboardingComplete(false)
            SessionStore(context).setHomeSettingsPrompted(true)
            SessionStore(context).setAppMode("launcher")
        }
    }

    @Test
    fun optionalCloudApiAndPermissionsCanBeSkippedIntoLocalHome() {
        ActivityScenario.launch(LauncherActivity::class.java).use {
            composeRule.onNodeWithTag("aura-onboarding-step-1").assertIsDisplayed()
            composeRule.onNodeWithText("AURA WIDGET CANVAS").assertIsDisplayed()
            continueOnboarding()

            composeRule.onNodeWithTag("aura-onboarding-step-2").assertIsDisplayed()
            composeRule.onNodeWithTag("aura-onboarding-local-option").performClick()
            composeRule.onNodeWithTag("aura-onboarding-next").performClick()

            composeRule.onNodeWithTag("aura-onboarding-step-3").assertIsDisplayed()
            composeRule.onNodeWithTag("aura-onboarding-mongo-uri")
                .performTextInput("mongodb://aura:secret@localhost:27017/?tls=true")
            composeRule.onNodeWithTag("aura-onboarding-mongo-database").performTextInput("_test")
            composeRule.onNodeWithTag("aura-onboarding-next").assertIsEnabled().performClick()

            composeRule.onNodeWithTag("aura-onboarding-step-4").assertIsDisplayed()
            continueOnboarding()

            composeRule.onNodeWithTag("aura-onboarding-step-5").assertIsDisplayed()
            composeRule.onNodeWithTag("aura-onboarding-api-key").performTextInput("test-api-key")
            composeRule.onNodeWithTag("aura-onboarding-next").assertIsEnabled().performClick()

            composeRule.onNodeWithTag("aura-onboarding-step-6").assertIsDisplayed()
            composeRule.onNodeWithText("SKIP STEP").performClick()

            composeRule.onNodeWithTag("aura-onboarding-step-7").assertIsDisplayed()
            composeRule.onNodeWithText("SKIP STEP").performClick()

            composeRule.onNodeWithTag("aura-onboarding-step-8").assertIsDisplayed()
            composeRule.onNodeWithText("INITIALIZE SYSTEM").performClick()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("aura-home-screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("aura-home-screen").assertIsDisplayed()
        }
    }

    private fun continueOnboarding() {
        composeRule.onNodeWithText("CONTINUE").performClick()
    }
}
