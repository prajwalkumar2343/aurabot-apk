package com.aura.app.automations

import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class CrossAppAutomationController(
    private val context: Context,
    private val accessibility: CrossAppAccessibilityBridge = AuraAutomationAccessibilityService,
    private val renderer: AutomationTemplateRenderer = AutomationTemplateRenderer()
) {
    suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult =
        withContext(Dispatchers.IO) {
            val result = when (action.type) {
                AutomationActionTypes.OpenApp -> openApp(action, event)
                AutomationActionTypes.TapText -> waitThen(action, event) {
                    accessibility.tap(action.selector(event, fallbackTextKey = AutomationActionMetadata.Text))
                }
                AutomationActionTypes.TapTarget -> waitThen(action, event) {
                    accessibility.tap(action.selector(event))
                }
                AutomationActionTypes.LongPressTarget -> waitThen(action, event) {
                    accessibility.longPress(action.selector(event))
                }
                AutomationActionTypes.TapBounds -> tapBounds(action)
                AutomationActionTypes.TypeText -> waitThen(action, event, selectorRequired = false) {
                    accessibility.typeText(
                        text = rendered(action.metadata[AutomationActionMetadata.Text].orEmpty(), event),
                        selector = action.optionalSelector(event, fallbackTextKey = AutomationActionMetadata.TargetText)
                    )
                }
                AutomationActionTypes.ClearText -> waitThen(action, event) {
                    accessibility.clearText(action.selector(event, fallbackTextKey = AutomationActionMetadata.TargetText))
                }
                AutomationActionTypes.WaitForText -> waitThen(action, event) {
                    accessibility.has(action.selector(event, fallbackTextKey = AutomationActionMetadata.Text))
                }
                AutomationActionTypes.Scroll -> waitThen(action, event, selectorRequired = false) {
                    accessibility.scroll(
                        selector = action.optionalSelector(event),
                        direction = action.metadata[AutomationActionMetadata.Direction] ?: "down"
                    )
                }
                AutomationActionTypes.Swipe -> swipe(action)
                AutomationActionTypes.PressBack -> requireAccessibility(action.type) { accessibility.pressBack() }
                AutomationActionTypes.PressHome -> requireAccessibility(action.type) { accessibility.pressHome() }
                else -> AutomationActionResult(action.type, AutomationRunStatus.Skipped, "Unsupported cross-app action")
            }
            settle(action)
            result
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

    private suspend fun waitThen(
        action: AutomationAction,
        event: AutomationEvent,
        selectorRequired: Boolean = true,
        block: () -> CrossAppUiResult
    ): AutomationActionResult {
        if (!accessibility.isEnabled()) return accessibilityMissing(action.type)
        if (selectorRequired && !action.hasSelector()) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Cross-app selector is missing")
        }
        val timeout = action.timeoutMillis()
        val deadline = System.currentTimeMillis() + timeout
        var last = CrossAppUiResult(false, "No matching UI target")
        while (System.currentTimeMillis() <= deadline) {
            last = block()
            if (last.success) {
                return AutomationActionResult(action.type, AutomationRunStatus.Success, last.message)
            }
            delay(POLL_INTERVAL_MILLIS)
        }
        val renderedTarget = action.describeTarget(event)
        return AutomationActionResult(
            action.type,
            AutomationRunStatus.Failed,
            "Timed out after ${timeout}ms waiting for $renderedTarget: ${last.message}"
        )
    }

    private fun tapBounds(action: AutomationAction): AutomationActionResult {
        val bounds = action.bounds()
            ?: return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Tap bounds are invalid")
        return requireAccessibility(action.type) {
            if (accessibility.tapBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)) {
                CrossAppUiResult(true, "Tapped bounds")
            } else {
                CrossAppUiResult(false, "Could not tap bounds")
            }
        }
    }

    private fun swipe(action: AutomationAction): AutomationActionResult {
        val points = action.swipePoints()
            ?: return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Swipe points are invalid")
        return requireAccessibility(action.type) {
            if (accessibility.swipe(points.startX, points.startY, points.endX, points.endY, action.durationMillis())) {
                CrossAppUiResult(true, "Swiped")
            } else {
                CrossAppUiResult(false, "Could not dispatch swipe")
            }
        }
    }

    private fun requireAccessibility(
        actionType: String,
        block: () -> CrossAppUiResult
    ): AutomationActionResult {
        if (!accessibility.isEnabled()) return accessibilityMissing(actionType)
        val result = block()
        return AutomationActionResult(
            actionType,
            if (result.success) AutomationRunStatus.Success else AutomationRunStatus.Failed,
            result.message
        )
    }

    private suspend fun settle(action: AutomationAction) {
        val delayMillis = action.metadata[AutomationActionMetadata.SettleMillis]?.toLongOrNull()
            ?.coerceIn(0L, 10_000L)
            ?: DEFAULT_SETTLE_MILLIS
        if (delayMillis > 0L) delay(delayMillis)
    }

    private fun rendered(value: String, event: AutomationEvent): String =
        renderer.render(value, event.values)

    private fun AutomationAction.selector(
        event: AutomationEvent,
        fallbackTextKey: String? = null
    ): CrossAppUiSelector {
        val selectorText = fallbackTextKey
            ?.let { metadata[it] }
            ?: metadata[AutomationActionMetadata.Text]
        return CrossAppUiSelector(
            text = rendered(selectorText.orEmpty(), event).ifBlank { null },
            contentDescription = rendered(metadata[AutomationActionMetadata.ContentDescription].orEmpty(), event).ifBlank { null },
            viewId = metadata[AutomationActionMetadata.ViewId]?.ifBlank { null },
            className = metadata[AutomationActionMetadata.ClassName]?.ifBlank { null },
            packageName = metadata[AutomationActionMetadata.PackageName]?.ifBlank { null },
            partialMatch = metadata[AutomationActionMetadata.PartialMatch]?.toBooleanStrictOrNull() ?: true,
            clickableOnly = metadata[AutomationActionMetadata.ClickableOnly]?.toBooleanStrictOrNull() ?: false,
            editableOnly = metadata[AutomationActionMetadata.EditableOnly]?.toBooleanStrictOrNull() ?: false,
            enabledOnly = metadata[AutomationActionMetadata.EnabledOnly]?.toBooleanStrictOrNull() ?: true,
            occurrence = metadata[AutomationActionMetadata.Occurrence]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        )
    }

    private fun AutomationAction.optionalSelector(
        event: AutomationEvent,
        fallbackTextKey: String? = null
    ): CrossAppUiSelector? =
        takeIf { it.hasSelector(fallbackTextKey) }?.selector(event, fallbackTextKey)

    private fun AutomationAction.hasSelector(fallbackTextKey: String? = null): Boolean =
        listOfNotNull(
            fallbackTextKey ?: AutomationActionMetadata.Text,
            AutomationActionMetadata.ContentDescription,
            AutomationActionMetadata.ViewId,
            AutomationActionMetadata.ClassName
        ).any { key -> metadata[key]?.isNotBlank() == true }

    private fun AutomationAction.timeoutMillis(): Long =
        metadata[AutomationActionMetadata.TimeoutMillis]?.toLongOrNull()?.coerceIn(250L, 120_000L)
            ?: DEFAULT_TIMEOUT_MILLIS

    private fun AutomationAction.durationMillis(): Long =
        metadata[AutomationActionMetadata.DurationMillis]?.toLongOrNull()?.coerceIn(50L, 2_000L)
            ?: DEFAULT_GESTURE_MILLIS

    private fun AutomationAction.describeTarget(event: AutomationEvent): String {
        val target = listOf(
            AutomationActionMetadata.Text,
            AutomationActionMetadata.TargetText,
            AutomationActionMetadata.ContentDescription,
            AutomationActionMetadata.ViewId,
            AutomationActionMetadata.ClassName
        ).firstNotNullOfOrNull { key -> metadata[key]?.takeIf { it.isNotBlank() } }
            ?.let { rendered(it, event) }
        return target ?: "target"
    }

    private fun accessibilityMissing(actionType: String): AutomationActionResult =
        AutomationActionResult(actionType, AutomationRunStatus.Failed, "Aura Accessibility Service is not enabled")

    companion object {
        private const val POLL_INTERVAL_MILLIS = 250L
        private const val DEFAULT_TIMEOUT_MILLIS = 5_000L
        private const val DEFAULT_SETTLE_MILLIS = 150L
        private const val DEFAULT_GESTURE_MILLIS = 300L

        fun openAccessibilitySettingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private data class TapBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
private data class SwipePoints(val startX: Int, val startY: Int, val endX: Int, val endY: Int)

private fun AutomationAction.bounds(): TapBounds? {
    val left = metadata[AutomationActionMetadata.BoundsLeft]?.toIntOrNull() ?: return null
    val top = metadata[AutomationActionMetadata.BoundsTop]?.toIntOrNull() ?: return null
    val right = metadata[AutomationActionMetadata.BoundsRight]?.toIntOrNull() ?: return null
    val bottom = metadata[AutomationActionMetadata.BoundsBottom]?.toIntOrNull() ?: return null
    if (right <= left || bottom <= top) return null
    return TapBounds(left, top, right, bottom)
}

private fun AutomationAction.swipePoints(): SwipePoints? {
    val startX = metadata[AutomationActionMetadata.StartX]?.toIntOrNull() ?: return null
    val startY = metadata[AutomationActionMetadata.StartY]?.toIntOrNull() ?: return null
    val endX = metadata[AutomationActionMetadata.EndX]?.toIntOrNull() ?: return null
    val endY = metadata[AutomationActionMetadata.EndY]?.toIntOrNull() ?: return null
    return SwipePoints(startX, startY, endX, endY)
}
