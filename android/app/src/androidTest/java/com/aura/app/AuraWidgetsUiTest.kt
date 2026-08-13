package com.aura.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import com.aura.app.ui.AuraAssistantTile
import com.aura.app.ui.AuraDynamicWidgetSection
import com.aura.app.ui.AuraPresenceMode
import com.aura.app.ui.theme.AuraTheme
import com.aura.app.widgets.AuraWidget
import com.aura.app.widgets.AuraWidgetAction
import com.aura.app.widgets.AuraWidgetActionType
import com.aura.app.widgets.AuraWidgetKind
import com.aura.app.widgets.AuraWidgetContentFormat
import com.aura.app.widgets.AuraWidgetPresentation
import com.aura.app.widgets.AuraWidgetRisk
import com.aura.app.widgets.AuraWidgetStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuraWidgetsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun assistantTileIsAccessibleAndActivates() {
        var activations = 0
        composeRule.setContent {
            AuraTheme {
                AuraAssistantTile(
                    mode = AuraPresenceMode.Idle,
                    voiceLevel = 0,
                    isSpeaking = false,
                    runState = null,
                    runPhase = null,
                    activeSubagents = 0,
                    needsApproval = false,
                    assistantText = "",
                    onActivate = { activations += 1 }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Aura assistant")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, activations)
    }

    @Test
    fun assistantTileAnnouncesActiveSubagents() {
        composeRule.setContent {
            AuraTheme {
                AuraAssistantTile(
                    mode = AuraPresenceMode.Thinking,
                    voiceLevel = 0,
                    isSpeaking = false,
                    runState = "running",
                    runPhase = "delegating",
                    activeSubagents = 2,
                    needsApproval = false,
                    assistantText = "",
                    onActivate = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Aura assistant")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Working with 2 subagents"
                )
            )
    }

    @Test
    fun confirmationUsesDedicatedCallbackAndShowsExactAction() {
        var requested = 0
        var confirmed = 0
        composeRule.setContent {
            AuraTheme {
                AuraDynamicWidgetSection(
                    widgets = listOf(widget(AuraWidgetStatus.AwaitingConfirmation)),
                    onAction = { _, _ -> requested += 1 },
                    onConfirm = { _, _ -> confirmed += 1 },
                    onCancelConfirmation = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Aura will ask: “Review my lunch order”")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Confirm Review").performClick()

        assertEquals(0, requested)
        assertEquals(1, confirmed)
    }

    @Test
    fun executingWidgetCannotBeDismissed() {
        composeRule.setContent {
            AuraTheme {
                AuraDynamicWidgetSection(
                    widgets = listOf(widget(AuraWidgetStatus.Executing)),
                    onAction = { _, _ -> },
                    onConfirm = { _, _ -> },
                    onCancelConfirmation = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Dismiss Lunch").assertCountEquals(0)
        composeRule.onNodeWithText("Working…").assertIsDisplayed()
    }

    @Test
    fun fullscreenReportOpensThroughDedicatedSurfaceCallback() {
        var openedId: String? = null
        composeRule.setContent {
            AuraTheme {
                AuraDynamicWidgetSection(
                    widgets = listOf(
                        widget(AuraWidgetStatus.Visible).copy(
                            kind = AuraWidgetKind.Report,
                            title = "Weekly report",
                            presentation = AuraWidgetPresentation.Fullscreen,
                            contentFormat = AuraWidgetContentFormat.Html,
                            content = "<h1>Weekly report</h1>"
                        )
                    ),
                    onOpenSurface = { openedId = it },
                    onAction = { _, _ -> },
                    onConfirm = { _, _ -> },
                    onCancelConfirmation = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Open report").performClick()
        assertEquals("lunch", openedId)
    }

    @Test
    fun assistantRunProgressIsVisibleOnHomeCanvas() {
        composeRule.setContent {
            AuraTheme {
                AuraDynamicWidgetSection(
                    widgets = listOf(
                        widget(AuraWidgetStatus.Visible).copy(
                            kind = AuraWidgetKind.Progress,
                            title = "Assistant run",
                            message = "Working — Delegating",
                            details = listOf("Managed run", "2 subagents active"),
                            actions = emptyList()
                        )
                    ),
                    onAction = { _, _ -> },
                    onConfirm = { _, _ -> },
                    onCancelConfirmation = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Working — Delegating").assertIsDisplayed()
        composeRule.onNodeWithText("2 subagents active").assertIsDisplayed()
    }

    private fun widget(status: AuraWidgetStatus) = AuraWidget(
        id = "lunch",
        kind = AuraWidgetKind.FoodOrder,
        title = "Lunch",
        message = "Review the order",
        details = emptyList(),
        actions = listOf(
            AuraWidgetAction(
                id = "review",
                label = "Review",
                type = AuraWidgetActionType.AssistantMessage,
                payload = mapOf("message" to "Review my lunch order"),
                requiresConfirmation = true
            )
        ),
        status = status,
        risk = AuraWidgetRisk.High,
        priority = 80,
        source = "assistant",
        dedupeKey = "lunch",
        pendingActionId = "review",
        createdAt = 1,
        updatedAt = 1,
        expiresAt = Long.MAX_VALUE,
        lastError = null
    )
}
