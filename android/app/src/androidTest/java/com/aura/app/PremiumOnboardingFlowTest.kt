package com.aura.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aura.app.ui.LauncherUiState
import com.aura.app.ui.OnboardingConfiguration
import com.aura.app.ui.OnboardingMode
import com.aura.app.ui.OnboardingScreen
import com.aura.app.ui.theme.AuraTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumOnboardingFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun localSetupTraversesEveryChapterWithFakeBoundaries() {
        val completedConfiguration = AtomicReference<OnboardingConfiguration?>()
        composeRule.setContent {
            AuraTheme {
                OnboardingScreen(
                    state = LauncherUiState(),
                    onRequestPermissions = {},
                    onGoogleChallenge = { callback ->
                        callback(Result.failure(IllegalStateException("Not used in local test")))
                    },
                    onGoogleSignIn = { _, _, callback ->
                        callback(Result.failure(IllegalStateException("Not used in local test")))
                    },
                    onVerifyLocalDatabase = { _, _, callback -> callback(Result.success(Unit)) },
                    onFinishOnboarding = completedConfiguration::set
                )
            }
        }

        continueFrom(1)
        composeRule.onNodeWithTag("aura-onboarding-local-option").performClick()
        composeRule.onNodeWithTag("aura-onboarding-next").performClick()
        composeRule.onNodeWithTag("aura-onboarding-step-3").assertIsDisplayed()

        composeRule.onNodeWithTag("aura-onboarding-mongo-uri")
            .performTextInput("mongodb://aura:secret@localhost:27017/?tls=true")
        composeRule.onNodeWithTag("aura-onboarding-next").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("aura-onboarding-step-4").assertIsDisplayed()

        continueFrom(4)
        composeRule.onNodeWithTag("aura-onboarding-api-key").performTextInput("test-key")
        composeRule.onNodeWithTag("aura-onboarding-next").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("aura-onboarding-step-6").assertIsDisplayed()

        composeRule.onNodeWithTag("aura-onboarding-next").performClick()
        composeRule.onNodeWithTag("aura-onboarding-step-7").assertIsDisplayed()
        composeRule.onNodeWithTag("aura-onboarding-next").performClick()
        composeRule.onNodeWithTag("aura-onboarding-step-8").assertIsDisplayed()
        composeRule.onNodeWithText("INITIALIZE SYSTEM").performClick()

        val configuration = completedConfiguration.get()
        assertNotNull(configuration)
        assertEquals(OnboardingMode.Local, configuration?.mode)
        assertEquals("test-key", configuration?.apiKey)
    }

    private fun continueFrom(step: Int) {
        composeRule.onNodeWithTag("aura-onboarding-step-$step").assertIsDisplayed()
        composeRule.onNodeWithText("CONTINUE").performClick()
    }
}
