package com.aura.app.automations

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AuraAutomationAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        activeService = this
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object : CrossAppAccessibilityBridge {
        @Volatile private var activeService: AuraAutomationAccessibilityService? = null

        override fun isEnabled(): Boolean = activeService != null

        override fun tapText(text: String, partialMatch: Boolean): Boolean {
            val service = activeService ?: return false
            val node = service.findNodeByText(text, partialMatch) ?: return false
            return service.clickNode(node)
        }

        override fun tapBounds(left: Int, top: Int, right: Int, bottom: Int): Boolean {
            val service = activeService ?: return false
            return service.dispatchTap((left + right) / 2f, (top + bottom) / 2f)
        }

        override fun typeText(text: String, targetText: String?, viewId: String?): Boolean {
            val service = activeService ?: return false
            val node = when {
                !viewId.isNullOrBlank() -> service.findNodeByViewId(viewId)
                !targetText.isNullOrBlank() -> service.findNodeByText(targetText, partialMatch = true)
                else -> service.findFocusedEditable()
            } ?: return false
            if (!node.isFocused) {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        override fun waitForText(text: String, partialMatch: Boolean): Boolean =
            hasText(text, partialMatch)

        override fun hasText(text: String, partialMatch: Boolean): Boolean {
            val service = activeService ?: return false
            return service.findNodeByText(text, partialMatch) != null
        }

        override fun pressBack(): Boolean =
            activeService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true

        override fun pressHome(): Boolean =
            activeService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
    }

    private fun findNodeByText(text: String, partialMatch: Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val directMatches = root.findAccessibilityNodeInfosByText(text).orEmpty()
        val direct = directMatches.firstOrNull { it.visibleToUser() && it.matchesText(text, partialMatch) }
        if (direct != null) return direct
        return root.depthFirst().firstOrNull { it.visibleToUser() && it.matchesText(text, partialMatch) }
    }

    private fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? =
        rootInActiveWindow
            ?.findAccessibilityNodeInfosByViewId(viewId)
            .orEmpty()
            .firstOrNull { it.visibleToUser() }

    private fun findFocusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: root.depthFirst().firstOrNull { it.visibleToUser() && it.isEditable }
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return if (!bounds.isEmpty) dispatchTap(bounds.centerX().toFloat(), bounds.centerY().toFloat()) else false
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun AccessibilityNodeInfo.depthFirst(): Sequence<AccessibilityNodeInfo> = sequence {
        yield(this@depthFirst)
        for (index in 0 until childCount) {
            getChild(index)?.let { child -> yieldAll(child.depthFirst()) }
        }
    }

    private fun AccessibilityNodeInfo.visibleToUser(): Boolean =
        isVisibleToUser

    private fun AccessibilityNodeInfo.matchesText(expected: String, partialMatch: Boolean): Boolean {
        val candidates = listOfNotNull(text?.toString(), contentDescription?.toString())
        return candidates.any { candidate ->
            if (partialMatch) {
                candidate.contains(expected, ignoreCase = true)
            } else {
                candidate.equals(expected, ignoreCase = true)
            }
        }
    }
}
