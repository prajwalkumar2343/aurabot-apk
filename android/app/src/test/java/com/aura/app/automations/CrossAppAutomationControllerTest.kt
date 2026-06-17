package com.aura.app.automations

import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossAppAutomationControllerTest {
    @Test
    fun tapTargetBuildsCompositeSelector() = runTest {
        val bridge = RecordingAccessibilityBridge()
        val controller = CrossAppAutomationController(testContext(), bridge)
        val action = AutomationAction(
            type = AutomationActionTypes.TapTarget,
            metadata = mapOf(
                AutomationActionMetadata.Text to "Continue",
                AutomationActionMetadata.ContentDescription to "Next step",
                AutomationActionMetadata.ViewId to "com.example:id/continue",
                AutomationActionMetadata.ClassName to "Button",
                AutomationActionMetadata.PackageName to "com.example",
                AutomationActionMetadata.PartialMatch to "false",
                AutomationActionMetadata.ClickableOnly to "true",
                AutomationActionMetadata.Occurrence to "1"
            )
        )

        val result = controller.execute(action, AutomationEvent())

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals(
            CrossAppUiSelector(
                text = "Continue",
                contentDescription = "Next step",
                viewId = "com.example:id/continue",
                className = "Button",
                packageName = "com.example",
                partialMatch = false,
                clickableOnly = true,
                occurrence = 1
            ),
            bridge.tappedSelectors.single()
        )
    }

    @Test
    fun typeTextUsesTargetTextAsSelectorAndTextAsInput() = runTest {
        val bridge = RecordingAccessibilityBridge()
        val controller = CrossAppAutomationController(testContext(), bridge)
        val action = AutomationAction(
            type = AutomationActionTypes.TypeText,
            metadata = mapOf(
                AutomationActionMetadata.Text to "hello@example.com",
                AutomationActionMetadata.TargetText to "Email"
            )
        )

        val result = controller.execute(action, AutomationEvent())

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals("hello@example.com", bridge.typedText.single())
        assertEquals("Email", bridge.typedSelectors.single()?.text)
    }

    @Test
    fun waitForTextPollsUntilTargetAppears() = runTest {
        val bridge = RecordingAccessibilityBridge(hasResults = listOf(false, false, true))
        val controller = CrossAppAutomationController(testContext(), bridge)
        val action = AutomationAction(
            type = AutomationActionTypes.WaitForText,
            metadata = mapOf(
                AutomationActionMetadata.Text to "Ready",
                AutomationActionMetadata.TimeoutMillis to "1000"
            )
        )

        val result = controller.execute(action, AutomationEvent())

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals(3, bridge.hasSelectors.size)
    }

    @Test
    fun disabledAccessibilityFailsClearly() = runTest {
        val bridge = RecordingAccessibilityBridge(enabled = false)
        val controller = CrossAppAutomationController(testContext(), bridge)

        val result = controller.execute(
            AutomationAction(
                type = AutomationActionTypes.TapTarget,
                metadata = mapOf(AutomationActionMetadata.Text to "Continue")
            ),
            AutomationEvent()
        )

        assertEquals(AutomationRunStatus.Failed, result.status)
        assertTrue(result.message.contains("Accessibility Service"))
    }

    @Test
    fun inspectScreenUsesPackageScopeAndBoundedNodeLimit() = runTest {
        val bridge = RecordingAccessibilityBridge()
        val controller = CrossAppAutomationController(testContext(), bridge)

        val result = controller.execute(
            AutomationAction(
                type = AutomationActionTypes.InspectScreen,
                metadata = mapOf(
                    AutomationActionMetadata.PackageName to "com.example",
                    AutomationActionMetadata.MaxNodes to "12"
                )
            ),
            AutomationEvent()
        )

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals("com.example" to 12, bridge.inspectCalls.single())
    }

    @Test
    fun waitForAppPollsUntilPackageIsVisible() = runTest {
        val bridge = RecordingAccessibilityBridge(packageResults = listOf(false, false, true))
        val controller = CrossAppAutomationController(testContext(), bridge)

        val result = controller.execute(
            AutomationAction(
                type = AutomationActionTypes.WaitForApp,
                metadata = mapOf(
                    AutomationActionMetadata.PackageName to "com.example",
                    AutomationActionMetadata.TimeoutMillis to "1000"
                )
            ),
            AutomationEvent()
        )

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals(listOf("com.example", "com.example", "com.example"), bridge.packageChecks)
    }

    @Test
    fun waitForAppRequiresAccessibilityService() = runTest {
        val bridge = RecordingAccessibilityBridge(enabled = false)
        val controller = CrossAppAutomationController(testContext(), bridge)

        val result = controller.execute(
            AutomationAction(
                type = AutomationActionTypes.WaitForApp,
                metadata = mapOf(AutomationActionMetadata.PackageName to "com.example")
            ),
            AutomationEvent()
        )

        assertEquals(AutomationRunStatus.Failed, result.status)
        assertTrue(result.message.contains("Accessibility Service"))
    }

    @Test
    fun scrollUntilTargetScrollsUntilSelectorAppears() = runTest {
        val bridge = RecordingAccessibilityBridge(hasResults = listOf(false, false, true))
        val controller = CrossAppAutomationController(testContext(), bridge)

        val result = controller.execute(
            AutomationAction(
                type = AutomationActionTypes.ScrollUntilTarget,
                metadata = mapOf(
                    AutomationActionMetadata.Text to "Advanced",
                    AutomationActionMetadata.Direction to "down",
                    AutomationActionMetadata.MaxScrolls to "5",
                    AutomationActionMetadata.SettleMillis to "0"
                )
            ),
            AutomationEvent()
        )

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals(3, bridge.hasSelectors.size)
        assertEquals(listOf("down", "down"), bridge.scrollDirections)
        assertEquals("Advanced", bridge.hasSelectors.last().text)
    }

    @Test
    fun failedCrossAppActionIncludesScreenDiagnostics() = runTest {
        val bridge = RecordingAccessibilityBridge(tapResults = List(4) { false })
        val controller = CrossAppAutomationController(testContext(), bridge)

        val result = controller.execute(
            AutomationAction(
                type = AutomationActionTypes.TapTarget,
                metadata = mapOf(
                    AutomationActionMetadata.Text to "Continue",
                    AutomationActionMetadata.TimeoutMillis to "250",
                    AutomationActionMetadata.SettleMillis to "0"
                )
            ),
            AutomationEvent()
        )

        assertEquals(AutomationRunStatus.Failed, result.status)
        assertTrue(result.message.contains("tap failed"))
        assertTrue(result.message.contains("Screen snapshot:"))
        assertEquals(null to 12, bridge.inspectCalls.single())
    }

    @Test
    fun failedCrossAppActionCanDisableScreenDiagnostics() = runTest {
        val bridge = RecordingAccessibilityBridge(tapResults = List(4) { false })
        val controller = CrossAppAutomationController(testContext(), bridge)

        val result = controller.execute(
            AutomationAction(
                type = AutomationActionTypes.TapTarget,
                metadata = mapOf(
                    AutomationActionMetadata.Text to "Continue",
                    AutomationActionMetadata.TimeoutMillis to "250",
                    AutomationActionMetadata.SettleMillis to "0",
                    AutomationActionMetadata.IncludeDiagnostics to "false"
                )
            ),
            AutomationEvent()
        )

        assertEquals(AutomationRunStatus.Failed, result.status)
        assertTrue(!result.message.contains("Screen snapshot:"))
        assertEquals(emptyList<Pair<String?, Int>>(), bridge.inspectCalls)
    }
}

private fun testContext() = ContextWrapper(null)

private class RecordingAccessibilityBridge(
    private val enabled: Boolean = true,
    hasResults: List<Boolean> = listOf(true),
    tapResults: List<Boolean> = listOf(true),
    packageResults: List<Boolean> = listOf(true)
) : CrossAppAccessibilityBridge {
    private val hasQueue = hasResults.toMutableList()
    private val tapQueue = tapResults.toMutableList()
    private val packageQueue = packageResults.toMutableList()
    val tappedSelectors = mutableListOf<CrossAppUiSelector>()
    val typedText = mutableListOf<String>()
    val typedSelectors = mutableListOf<CrossAppUiSelector?>()
    val hasSelectors = mutableListOf<CrossAppUiSelector>()
    val inspectCalls = mutableListOf<Pair<String?, Int>>()
    val scrollDirections = mutableListOf<String>()
    val packageChecks = mutableListOf<String>()

    override fun isEnabled(): Boolean = enabled

    override fun tap(selector: CrossAppUiSelector): CrossAppUiResult {
        tappedSelectors += selector
        val success = if (tapQueue.isNotEmpty()) tapQueue.removeAt(0) else true
        return CrossAppUiResult(success, if (success) "tapped" else "tap failed")
    }

    override fun longPress(selector: CrossAppUiSelector): CrossAppUiResult =
        CrossAppUiResult(true, "long pressed")

    override fun tapBounds(left: Int, top: Int, right: Int, bottom: Int): Boolean = true

    override fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMillis: Long): Boolean = true

    override fun scroll(selector: CrossAppUiSelector?, direction: String): CrossAppUiResult {
        scrollDirections += direction
        return CrossAppUiResult(true, "scrolled")
    }

    override fun typeText(text: String, selector: CrossAppUiSelector?): CrossAppUiResult {
        typedText += text
        typedSelectors += selector
        return CrossAppUiResult(true, "typed")
    }

    override fun clearText(selector: CrossAppUiSelector): CrossAppUiResult =
        CrossAppUiResult(true, "cleared")

    override fun has(selector: CrossAppUiSelector): CrossAppUiResult {
        hasSelectors += selector
        val success = if (hasQueue.isNotEmpty()) hasQueue.removeAt(0) else true
        return CrossAppUiResult(success, if (success) "found" else "missing")
    }

    override fun hasPackage(packageName: String): CrossAppUiResult {
        packageChecks += packageName
        val success = if (packageQueue.isNotEmpty()) packageQueue.removeAt(0) else true
        return CrossAppUiResult(success, if (success) "visible" else "not visible")
    }

    override fun inspect(packageName: String?, maxNodes: Int): CrossAppUiResult {
        inspectCalls += packageName to maxNodes
        return CrossAppUiResult(true, "Visible UI nodes:\n1. text=Ready")
    }

    override fun pressBack(): CrossAppUiResult = CrossAppUiResult(true, "back")
    override fun pressHome(): CrossAppUiResult = CrossAppUiResult(true, "home")
}
