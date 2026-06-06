package com.aura.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.clearElement
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.DriverAtoms.webKeys
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FieldNotesReactRuntimeSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun resetMiniAppStorage() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("aura_mini_apps.db")
    }

    @Test
    fun fieldNotesReactRuntimeSupportsNavigationCreateEditAndDelete() {
        ActivityScenario.launch(LauncherActivity::class.java).use { _ ->
            composeRule.onNodeWithTag("aura-home-screen").assertIsDisplayed()
            composeRule.onNodeWithTag("aura-home-screen").performTouchInput { swipeLeft() }
            composeRule.onNodeWithText("Field Notes").assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("field-notes-react-webview").assertIsDisplayed()

            waitForWebText("Field Notes")

            clickWebButton("New")
            waitForWebText("Create note")
            clickWebButton("Board")
            waitForWebText("No notes yet")

            clickWebButton("Seed smoke note")
            waitForWebText("Runtime smoke")
            onWebView()
                .withElement(findElement(Locator.TAG_NAME, "body"))
                .check(webMatches(getText(), containsString("Created locally through the React bridge.")))

            clickWebButton("Edit")
            replaceWebField("title", "Edited runtime smoke")
            replaceWebField("note", "Edited through the Android runtime smoke test.")
            clickWebButton("Update note")

            waitForWebText("Edited runtime smoke")
            onWebView()
                .withElement(findElement(Locator.TAG_NAME, "body"))
                .check(webMatches(getText(), containsString("Edited through the Android runtime smoke test.")))

            clickWebButton("Delete")
            waitForWebText("No notes yet")
            onWebView()
                .withElement(findElement(Locator.TAG_NAME, "body"))
                .check(webMatches(getText(), not(containsString("Edited runtime smoke"))))
        }
    }

    private fun clickWebButton(text: String) {
        onWebView()
            .withElement(findElement(Locator.XPATH, "//button[normalize-space()='$text']"))
            .perform(webClick())
    }

    private fun replaceWebField(name: String, value: String) {
        onWebView()
            .withElement(findElement(Locator.NAME, name))
            .perform(clearElement())
            .perform(webKeys(value))
    }

    private fun waitForWebText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onWebView()
                    .withElement(findElement(Locator.TAG_NAME, "body"))
                    .check(webMatches(getText(), containsString(text)))
                true
            }.getOrDefault(false)
        }
    }
}
