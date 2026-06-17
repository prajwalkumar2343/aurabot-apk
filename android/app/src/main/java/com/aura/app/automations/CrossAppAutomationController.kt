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
                AutomationActionTypes.WaitForApp -> waitForApp(action, event)
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
                AutomationActionTypes.WaitForTarget -> waitThen(action, event) {
                    accessibility.has(action.selector(event))
                }
                AutomationActionTypes.WaitUntilGone -> waitUntilGone(action, event)
                AutomationActionTypes.WaitForIdle -> waitForIdle(action)
                AutomationActionTypes.Scroll -> waitThen(action, event, selectorRequired = false) {
                    accessibility.scroll(
                        selector = action.optionalSelector(event),
                        direction = action.metadata[AutomationActionMetadata.Direction] ?: "down"
                    )
                }
                AutomationActionTypes.ScrollUntilTarget -> scrollUntilTarget(action, event)
                AutomationActionTypes.Swipe -> swipe(action)
                AutomationActionTypes.InspectScreen -> inspectScreen(action)
                AutomationActionTypes.PressBack -> requireAccessibility(action.type) { accessibility.pressBack() }
                AutomationActionTypes.PressHome -> requireAccessibility(action.type) { accessibility.pressHome() }
                else -> AutomationActionResult(action.type, AutomationRunStatus.Skipped, "Unsupported cross-app action")
            }
            val diagnosed = result.withFailureDiagnostics(action)
            settle(action)
            diagnosed
        }

    private suspend fun openApp(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        val packageName = action.metadata[AutomationActionMetadata.PackageName]
            ?: event.values[AutomationActionMetadata.PackageName]
        val query = action.metadata[AutomationActionMetadata.AppQuery]
        val target = packageName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { packageId ->
                context.packageManager.getLaunchIntentForPackage(packageId)
                    ?.let { LaunchTarget(it, packageId) }
            }
            ?: query
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { resolveLaunchIntentByLabel(it) }
        if (target == null) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "App could not be found")
        }
        val intent = target.intent
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (runCatching { context.startActivity(intent) }.isFailure) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "App could not be opened")
        }
        return if (target.packageName != null && accessibility.isEnabled()) {
            waitForPackage(action.type, target.packageName, action.timeoutMillis(), successPrefix = "App opened")
        } else {
            AutomationActionResult(action.type, AutomationRunStatus.Success, "App opened")
        }
    }

    private fun resolveLaunchIntentByLabel(query: String): LaunchTarget? {
        val normalized = query.lowercase()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launcherIntent, 0)
            .firstOrNull { result ->
                result.loadLabel(context.packageManager).toString().lowercase().contains(normalized) ||
                    result.activityInfo.packageName.lowercase().contains(normalized)
            }
            ?.activityInfo
            ?.let { info ->
                LaunchTarget(
                    intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setClassName(info.packageName, info.name)
                    },
                    packageName = info.packageName
                )
            }
    }

    private suspend fun waitForApp(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        if (!accessibility.isEnabled()) return accessibilityMissing(action.type)
        val packageName = action.packageName(event)
            ?: return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Package name is missing")
        return waitForPackage(action.type, packageName, action.timeoutMillis(), successPrefix = "App visible")
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

    private suspend fun waitUntilGone(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        if (!accessibility.isEnabled()) return accessibilityMissing(action.type)
        if (!action.hasSelector()) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Cross-app selector is missing")
        }
        val selector = action.selector(event)
        val timeout = action.timeoutMillis()
        val deadline = System.currentTimeMillis() + timeout
        var last = CrossAppUiResult(false, "Target still visible")
        while (System.currentTimeMillis() <= deadline) {
            last = accessibility.has(selector)
            if (!last.success) {
                return AutomationActionResult(action.type, AutomationRunStatus.Success, "Target is gone")
            }
            delay(POLL_INTERVAL_MILLIS)
        }
        return AutomationActionResult(
            action.type,
            AutomationRunStatus.Failed,
            "Timed out after ${timeout}ms waiting for ${action.describeTarget(event)} to disappear: ${last.message}"
        )
    }

    private suspend fun waitForIdle(action: AutomationAction): AutomationActionResult {
        if (!accessibility.isEnabled()) return accessibilityMissing(action.type)
        val timeout = action.timeoutMillis()
        val deadline = System.currentTimeMillis() + timeout
        val stableSamples = action.stableSamples()
        var lastSnapshot: String? = null
        var consecutiveMatches = 0
        var last = CrossAppUiResult(false, "No screen snapshot yet")
        while (System.currentTimeMillis() <= deadline) {
            last = accessibility.inspect(
                packageName = action.metadata[AutomationActionMetadata.PackageName]?.ifBlank { null },
                maxNodes = action.metadata[AutomationActionMetadata.MaxNodes]?.toIntOrNull()?.coerceIn(1, 80) ?: DEFAULT_IDLE_MAX_NODES
            )
            if (last.success) {
                val snapshot = last.message
                consecutiveMatches = if (snapshot == lastSnapshot) consecutiveMatches + 1 else 1
                lastSnapshot = snapshot
                if (consecutiveMatches >= stableSamples) {
                    return AutomationActionResult(
                        action.type,
                        AutomationRunStatus.Success,
                        "Screen idle after $consecutiveMatches stable sample(s)"
                    )
                }
            }
            delay(POLL_INTERVAL_MILLIS)
        }
        return AutomationActionResult(
            action.type,
            AutomationRunStatus.Failed,
            "Timed out after ${timeout}ms waiting for screen idle: ${last.message}"
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

    private suspend fun scrollUntilTarget(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        if (!accessibility.isEnabled()) return accessibilityMissing(action.type)
        if (!action.hasSelector()) {
            return AutomationActionResult(action.type, AutomationRunStatus.Failed, "Cross-app selector is missing")
        }
        val selector = action.selector(event)
        val direction = action.metadata[AutomationActionMetadata.Direction] ?: "down"
        val maxScrolls = action.metadata[AutomationActionMetadata.MaxScrolls]?.toIntOrNull()?.coerceIn(1, 50)
            ?: DEFAULT_MAX_SCROLLS
        var last = CrossAppUiResult(false, "No matching UI target")
        for (scrollCount in 0..maxScrolls) {
            last = accessibility.has(selector)
            if (last.success) {
                return AutomationActionResult(
                    action.type,
                    AutomationRunStatus.Success,
                    "Found target after $scrollCount scroll(s): ${last.message}"
                )
            }
            if (scrollCount == maxScrolls) break
            val scrollResult = accessibility.scroll(selector = null, direction = direction)
            if (!scrollResult.success) {
                return AutomationActionResult(
                    action.type,
                    AutomationRunStatus.Failed,
                    "Could not scroll while looking for ${action.describeTarget(event)}: ${scrollResult.message}"
                )
            }
            delay(action.scrollSettleMillis())
        }
        return AutomationActionResult(
            action.type,
            AutomationRunStatus.Failed,
            "Target ${action.describeTarget(event)} not found after $maxScrolls scroll(s): ${last.message}"
        )
    }

    private fun inspectScreen(action: AutomationAction): AutomationActionResult =
        requireAccessibility(action.type) {
            accessibility.inspect(
                packageName = action.metadata[AutomationActionMetadata.PackageName]?.ifBlank { null },
                maxNodes = action.metadata[AutomationActionMetadata.MaxNodes]?.toIntOrNull()?.coerceIn(1, 80) ?: 40
            )
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

    private suspend fun waitForPackage(
        actionType: String,
        packageName: String,
        timeoutMillis: Long,
        successPrefix: String
    ): AutomationActionResult {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last = CrossAppUiResult(false, "Package $packageName is not visible")
        while (System.currentTimeMillis() <= deadline) {
            last = accessibility.hasPackage(packageName)
            if (last.success) {
                return AutomationActionResult(actionType, AutomationRunStatus.Success, "$successPrefix: $packageName")
            }
            delay(POLL_INTERVAL_MILLIS)
        }
        return AutomationActionResult(
            actionType,
            AutomationRunStatus.Failed,
            "Timed out after ${timeoutMillis}ms waiting for app $packageName: ${last.message}"
        )
    }

    private fun AutomationActionResult.withFailureDiagnostics(action: AutomationAction): AutomationActionResult {
        if (status != AutomationRunStatus.Failed) return this
        if (action.type == AutomationActionTypes.InspectScreen) return this
        if (!action.includeDiagnostics()) return this
        if (!accessibility.isEnabled()) return this
        val snapshot = accessibility.inspect(
            packageName = action.metadata[AutomationActionMetadata.PackageName]?.ifBlank { null },
            maxNodes = action.diagnosticMaxNodes()
        )
        if (!snapshot.success || snapshot.message.isBlank()) return this
        return copy(message = "$message\nScreen snapshot:\n${snapshot.message}")
    }

    private suspend fun settle(action: AutomationAction) {
        val delayMillis = action.metadata[AutomationActionMetadata.SettleMillis]?.toLongOrNull()
            ?.coerceIn(0L, 10_000L)
            ?: DEFAULT_SETTLE_MILLIS
        if (delayMillis > 0L) delay(delayMillis)
    }

    private fun rendered(value: String, event: AutomationEvent): String =
        renderer.render(value, event.values)

    private fun AutomationAction.packageName(event: AutomationEvent): String? =
        (metadata[AutomationActionMetadata.PackageName] ?: event.values[AutomationActionMetadata.PackageName])
            ?.trim()
            ?.takeIf { it.isNotBlank() }

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

    private fun AutomationAction.includeDiagnostics(): Boolean =
        metadata[AutomationActionMetadata.IncludeDiagnostics]?.toBooleanStrictOrNull() ?: true

    private fun AutomationAction.diagnosticMaxNodes(): Int =
        metadata[AutomationActionMetadata.DiagnosticMaxNodes]?.toIntOrNull()?.coerceIn(1, 40)
            ?: DEFAULT_DIAGNOSTIC_MAX_NODES

    private fun AutomationAction.stableSamples(): Int =
        metadata[AutomationActionMetadata.StableSamples]?.toIntOrNull()?.coerceIn(2, 6)
            ?: DEFAULT_STABLE_SAMPLES

    private fun AutomationAction.scrollSettleMillis(): Long =
        metadata[AutomationActionMetadata.SettleMillis]?.toLongOrNull()?.coerceIn(0L, 5_000L)
            ?: DEFAULT_SCROLL_SETTLE_MILLIS

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
        private const val DEFAULT_SCROLL_SETTLE_MILLIS = 250L
        private const val DEFAULT_GESTURE_MILLIS = 300L
        private const val DEFAULT_MAX_SCROLLS = 8
        private const val DEFAULT_DIAGNOSTIC_MAX_NODES = 12
        private const val DEFAULT_IDLE_MAX_NODES = 20
        private const val DEFAULT_STABLE_SAMPLES = 2

        fun openAccessibilitySettingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private data class TapBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
private data class LaunchTarget(val intent: Intent, val packageName: String?)
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
