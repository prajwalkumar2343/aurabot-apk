package com.aura.app.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraWidgetPolicyTest {
    @Test
    fun highRiskWidgetForcesConfirmationOnEveryAction() {
        val proposal = proposal(
            risk = AuraWidgetRisk.High,
            actions = listOf(
                AuraWidgetAction(
                    id = "review-order",
                    label = "Review order",
                    type = AuraWidgetActionType.AssistantMessage,
                    payload = mapOf("message" to "Review my order")
                )
            )
        )

        val validated = AuraWidgetPolicy.validate(proposal)

        assertTrue(validated.actions.single().requiresConfirmation)
    }

    @Test
    fun lowRiskDismissActionDoesNotGainConfirmation() {
        val validated = AuraWidgetPolicy.validate(
            proposal(
                actions = listOf(
                    AuraWidgetAction(
                        id = "dismiss",
                        label = "Dismiss",
                        type = AuraWidgetActionType.Dismiss
                    )
                )
            )
        )

        assertFalse(validated.actions.single().requiresConfirmation)
    }

    @Test
    fun modelAuthoredAssistantMessageAlwaysRequiresConfirmation() {
        val validated = AuraWidgetPolicy.validate(
            proposal(
                actions = listOf(
                    AuraWidgetAction(
                        id = "ask",
                        label = "Ask Aura",
                        type = AuraWidgetActionType.AssistantMessage,
                        payload = mapOf("message" to "Review my lunch order")
                    )
                )
            )
        )

        assertTrue(validated.actions.single().requiresConfirmation)
    }

    @Test
    fun normalizesVisibleText() {
        val validated = AuraWidgetPolicy.validate(
            proposal(title = "  Lunch  ", message = "  Ready to review  ")
        )

        assertEquals("Lunch", validated.title)
        assertEquals("Ready to review", validated.message)
    }

    @Test
    fun rejectsOutOfRangePriorityAndExpiry() {
        assertThrows(AuraWidgetValidationException::class.java) {
            AuraWidgetPolicy.validate(proposal(priority = 500))
        }
        assertThrows(AuraWidgetValidationException::class.java) {
            AuraWidgetPolicy.validate(proposal(expiresInMinutes = 0))
        }
    }

    @Test
    fun rejectsDuplicateActionIds() {
        val duplicate = AuraWidgetAction(
            id = "open",
            label = "Open",
            type = AuraWidgetActionType.OpenApp,
            payload = mapOf("package_name" to "com.example")
        )

        assertThrows(AuraWidgetValidationException::class.java) {
            AuraWidgetPolicy.validate(proposal(actions = listOf(duplicate, duplicate)))
        }
    }

    @Test
    fun rejectsActionIdsThatCollideAfterNormalization() {
        val first = AuraWidgetAction(
            id = "open",
            label = "Open",
            type = AuraWidgetActionType.OpenApp,
            payload = mapOf("package_name" to "com.example")
        )

        assertThrows(AuraWidgetValidationException::class.java) {
            AuraWidgetPolicy.validate(
                proposal(actions = listOf(first, first.copy(id = " open ")))
            )
        }
    }

    @Test
    fun rejectsMissingActionSpecificPayloads() {
        assertThrows(AuraWidgetValidationException::class.java) {
            AuraWidgetPolicy.validate(
                proposal(
                    actions = listOf(
                        AuraWidgetAction(
                            id = "ask",
                            label = "Ask",
                            type = AuraWidgetActionType.AssistantMessage
                        )
                    )
                )
            )
        }
        assertThrows(AuraWidgetValidationException::class.java) {
            AuraWidgetPolicy.validate(
                proposal(
                    actions = listOf(
                        AuraWidgetAction(
                            id = "open",
                            label = "Open",
                            type = AuraWidgetActionType.OpenApp
                        )
                    )
                )
            )
        }
    }

    @Test
    fun confirmationWidgetRequiresAnAction() {
        assertThrows(AuraWidgetValidationException::class.java) {
            AuraWidgetPolicy.validate(
                proposal().copy(kind = AuraWidgetKind.Confirmation)
            )
        }
    }

    @Test
    fun missingDedupeKeyGetsStableContentIdentity() {
        val first = AuraWidgetPolicy.validate(proposal().copy(dedupeKey = ""))
        val second = AuraWidgetPolicy.validate(proposal().copy(dedupeKey = ""))

        assertTrue(first.dedupeKey.startsWith("auto:"))
        assertEquals(first.dedupeKey, second.dedupeKey)
    }

    @Test
    fun acceptsBoundedFullscreenHtmlReport() {
        val validated = AuraWidgetPolicy.validate(
            proposal().copy(
                kind = AuraWidgetKind.Report,
                presentation = AuraWidgetPresentation.Fullscreen,
                contentFormat = AuraWidgetContentFormat.Html,
                content = "  <h1>Weekly review</h1>  "
            )
        )

        assertEquals("<h1>Weekly review</h1>", validated.content)
    }

    @Test
    fun rejectsHtmlOutsideFullscreenReports() {
        assertThrows(AuraWidgetValidationException::class.java) {
            AuraWidgetPolicy.validate(
                proposal().copy(
                    kind = AuraWidgetKind.MeetingNotes,
                    contentFormat = AuraWidgetContentFormat.Html,
                    content = "<p>Notes</p>"
                )
            )
        }
    }

    @Test
    fun fullscreenSurfaceRequiresContent() {
        assertThrows(AuraWidgetValidationException::class.java) {
            AuraWidgetPolicy.validate(
                proposal().copy(presentation = AuraWidgetPresentation.Fullscreen)
            )
        }
    }

    private fun proposal(
        title: String = "Lunch",
        message: String = "Ready to review",
        risk: AuraWidgetRisk = AuraWidgetRisk.Low,
        priority: Int = 10,
        expiresInMinutes: Int = 30,
        actions: List<AuraWidgetAction> = emptyList()
    ) = AuraWidgetProposal(
        kind = AuraWidgetKind.FoodOrder,
        title = title,
        message = message,
        actions = actions,
        risk = risk,
        priority = priority,
        expiresInMinutes = expiresInMinutes,
        dedupeKey = "lunch"
    )
}
