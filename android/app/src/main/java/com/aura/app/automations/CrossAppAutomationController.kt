package com.aura.app.automations

import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class CrossAppAutomationController(
    private val context: Context,
    private val accessibility: CrossAppAccessibilityBridge = AuraAutomationAccessibilityService
) {
    suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult =
        withContext(Dispatchers.IO) {
            when (action.type) {
                AutomationActionTypes.OpenApp -> openApp(action, event)
                AutomationActionTypes.TapText -> withAccessibility(action) {
                    tapText(renderedText(action, event), partialMatch = action.partialMatch())
                }
                AutomationActionTypes.TapBounds -> {
                    val bounds = action.bounds()
                    if (bounds == null) {
                        AutomationActionResult(action.type, AutomationRunStatus.Failed, "Tap bounds are invalid")
                    } else {
                        withAccessibility(action) { tapBounds(bounds.left, bounds.top, bounds.right, bounds.bottom) }
                    }
                }
                AutomationActionTypes.TypeText -> withAccessibility(action) {
                    typeText(
                        text = renderedText(action, event),
                        targetText = action.metadata[AutomationActionMetadata.TargetText],
                        viewId = action.metadata[AutomationActionMetadata.ViewId]
                    )
                }
                AutomationActionTypes.WaitForText -> waitForText(action, event)
                AutomationActionTypes.PressBack -> withAccessibility(action) { pressBack() }
                AutomationActionTypes.PressHome -> withAccessibility(action) { pressHome() }
                else -> AutomationActionResult(action.type, AutomationRunStatus.Skipped, "Unsupported cross-app action")
            }
        }

    private fun openApp(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        val packageName = action.metadata[AutomationActionMetadata.PackageName]
            ?: event.values[AutomationActionMetadata.PackageName]
        val query = action.metadata[AutomationActionMetadata.AppQuery]
        val intent = packageName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: query
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { resolveLaunchIntentByLabel(it) }
        if (intent == null) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "App could not be found")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return if (runCatching { context.startActivity(intent) }.isSuccess) {
            AutomationActionResult(action.type, AutomationRunStatus.Success, "App opened")
        } else {
            AutomationActionResult(action.type, AutomationRunStatus.Failed, "App could not be opened")
        }
    }

    private fun resolveLaunchIntentByLabel(query: String): Intent? {
        val normalized = query.lowercase()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launcherIntent, 0)
            .firstOrNull { result ->
                result.loadLabel(context.packageManager).toString().lowercase().contains(normalized) ||
                    result.activityInfo.packageName.lowercase().contains(normalized)
            }
            ?.activityInfo
            ?.let { info ->
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(info.packageName, info.name)
                }
            }
    }

    private suspend fun waitForText(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        val expected = renderedText(action, event)
        val timeout = action.metadata[AutomationActionMetadata.TimeoutMillis]?.toLongOrNull()
            ?.coerceIn(250L, 60_000L)
            ?: 5_000L
        if (!accessibility.isEnabled()) {
            return accessibilityMissing(action.type)
        }
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() <= deadline) {
            if (accessibility.hasText(expected, partialMatch = action.partialMatch())) {
                return AutomationActionResult(action.type, AutomationRunStatus.Success, "Text appeared")
            }
            delay(250L)
        }
        return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Timed out waiting for text")
    }

    private fun renderedText(action: AutomationAction, event: AutomationEvent): String =
        AutomationTemplateRenderer().render(action.metadata[AutomationActionMetadata.Text].orEmpty(), event.values)

    private fun AutomationAction.partialMatch(): Boolean =
        metadata[AutomationActionMetadata.PartialMatch]?.toBooleanStrictOrNull() ?: true

    private inline fun withAccessibility(
        action: AutomationAction,
        block: CrossAppAccessibilityBridge.() -> Boolean
    ): AutomationActionResult {
        if (!accessibility.isEnabled()) {
            return accessibilityMissing(action.type)
        }
        return if (accessibility.block()) {
            AutomationActionResult(action.type, AutomationRunStatus.Success, "Cross-app action completed")
        } else {
            AutomationActionResult(action.type, AutomationRunStatus.Failed, "Cross-app action could not find its target")
        }
    }

    private fun accessibilityMissing(actionType: String): AutomationActionResult =
        AutomationActionResult(actionType, AutomationRunStatus.Failed, "Aura Accessibility Service is not enabled")

    companion object {
        fun openAccessibilitySettingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private data class TapBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

private fun AutomationAction.bounds(): TapBounds? {
    val left = metadata[AutomationActionMetadata.BoundsLeft]?.toIntOrNull() ?: return null
    val top = metadata[AutomationActionMetadata.BoundsTop]?.toIntOrNull() ?: return null
    val right = metadata[AutomationActionMetadata.BoundsRight]?.toIntOrNull() ?: return null
    val bottom = metadata[AutomationActionMetadata.BoundsBottom]?.toIntOrNull() ?: return null
    if (right <= left || bottom <= top) return null
    return TapBounds(left, top, right, bottom)
}
