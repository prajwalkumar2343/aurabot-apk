package com.aura.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.aura.app.miniapps.BuiltInMiniApps
import com.aura.app.miniapps.MiniAppWidget
import com.aura.app.miniapps.MiniAppWidgetSnapshot
import com.aura.app.ui.MiniAppHomeWidgetSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MiniAppHomeWidgetUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quickActionRunsWithoutOpeningAndCardTapOpensFullMiniApp() {
        val bundle = BuiltInMiniApps.habitTracker.copy(
            widget = MiniAppWidget(
                type = "quick_actions",
                title = "Habits",
                description = "Daily check-ins",
                metric = "today_count",
                actionIds = listOf("check_water")
            )
        )
        var openedId: String? = null
        var action: Pair<String, String>? = null
        composeRule.setContent {
            MaterialTheme {
                MiniAppHomeWidgetSection(
                    widgets = listOf(MiniAppWidgetSnapshot(bundle, todayCount = 2)),
                    unavailableCount = 0,
                    onOpenMiniApp = { openedId = it },
                    onRunAction = { miniAppId, actionId -> action = miniAppId to actionId }
                )
            }
        }

        composeRule.onNodeWithTag("mini-app-widget-action-${bundle.id}-check_water").performClick()
        composeRule.runOnIdle {
            assertEquals(bundle.id to "check_water", action)
            assertNull(openedId)
        }
        composeRule.onNodeWithTag("mini-app-widget-${bundle.id}").performClick()
        composeRule.runOnIdle { assertEquals(bundle.id, openedId) }
    }

    @Test
    fun progressWidgetRendersProgressSurface() {
        val bundle = BuiltInMiniApps.habitTracker.copy(
            widget = MiniAppWidget(
                type = "progress",
                title = "Weekly goal",
                description = "Build momentum",
                metric = "weekly_count",
                goal = 7
            )
        )
        composeRule.setContent {
            MaterialTheme {
                MiniAppHomeWidgetSection(
                    widgets = listOf(MiniAppWidgetSnapshot(bundle, weeklyCount = 4)),
                    unavailableCount = 0,
                    onOpenMiniApp = {},
                    onRunAction = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("mini-app-widget-progress-${bundle.id}").assertIsDisplayed()
    }
}
