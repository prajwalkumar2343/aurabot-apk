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

        override fun tap(selector: CrossAppUiSelector): CrossAppUiResult {
            val service = activeService ?: return missingService()
            val node = service.findNode(selector) ?: return CrossAppUiResult(false, "No matching UI target")
            return if (service.clickNode(node)) {
                CrossAppUiResult(true, "Tapped ${service.nodeSummary(node)}")
            } else {
                CrossAppUiResult(false, "Matched target but could not tap it")
            }
        }

        override fun longPress(selector: CrossAppUiSelector): CrossAppUiResult {
            val service = activeService ?: return missingService()
            val node = service.findNode(selector) ?: return CrossAppUiResult(false, "No matching UI target")
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            return if (!bounds.isEmpty() && service.dispatchTap(bounds.centerX().toFloat(), bounds.centerY().toFloat(), 650L)) {
                CrossAppUiResult(true, "Long pressed ${service.nodeSummary(node)}")
            } else {
                CrossAppUiResult(false, "Matched target but could not long press it")
            }
        }

        override fun tapBounds(left: Int, top: Int, right: Int, bottom: Int): Boolean {
            val service = activeService ?: return false
            return service.dispatchTap((left + right) / 2f, (top + bottom) / 2f)
        }

        override fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMillis: Long): Boolean {
            val service = activeService ?: return false
            return service.dispatchSwipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), durationMillis)
        }

        override fun scroll(selector: CrossAppUiSelector?, direction: String): CrossAppUiResult {
            val service = activeService ?: return missingService()
            val node = selector?.let { service.findNode(it) } ?: service.scrollableNode()
            if (node == null) return CrossAppUiResult(false, "No scrollable UI target")
            val action = when (direction.lowercase()) {
                "up", "left", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            return if (node.performAction(action)) {
                CrossAppUiResult(true, "Scrolled ${service.nodeSummary(node)}")
            } else {
                CrossAppUiResult(false, "Matched target but could not scroll it")
            }
        }

        override fun typeText(text: String, selector: CrossAppUiSelector?): CrossAppUiResult {
            val service = activeService ?: return missingService()
            val node = selector?.let { service.findNode(it.copy(editableOnly = true)) }
                ?: service.findFocusedEditable()
            if (node == null) return CrossAppUiResult(false, "No editable UI target")
            if (!node.isFocused) node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                CrossAppUiResult(true, "Typed into ${service.nodeSummary(node)}")
            } else {
                CrossAppUiResult(false, "Matched editable target but could not type into it")
            }
        }

        override fun clearText(selector: CrossAppUiSelector): CrossAppUiResult =
            typeText("", selector)

        override fun has(selector: CrossAppUiSelector): CrossAppUiResult {
            val service = activeService ?: return missingService()
            val node = service.findNode(selector)
            return if (node != null) {
                CrossAppUiResult(true, "Found ${service.nodeSummary(node)}")
            } else {
                CrossAppUiResult(false, "No matching UI target")
            }
        }

        override fun hasPackage(packageName: String): CrossAppUiResult {
            val service = activeService ?: return missingService()
            val visiblePackages = service.visiblePackageNames()
            return if (packageName in visiblePackages) {
                CrossAppUiResult(true, "Package $packageName is visible")
            } else {
                val visible = visiblePackages.take(5).joinToString(", ").ifBlank { "none" }
                CrossAppUiResult(false, "Package $packageName is not visible; visible packages: $visible")
            }
        }

        override fun inspect(packageName: String?, maxNodes: Int): CrossAppUiResult {
            val service = activeService ?: return missingService()
            val snapshot = service.inspectNodes(packageName, maxNodes.coerceIn(1, 80))
            return if (snapshot.isBlank()) {
                CrossAppUiResult(false, "No visible UI nodes found")
            } else {
                CrossAppUiResult(true, snapshot)
            }
        }

        override fun pressBack(): CrossAppUiResult =
            if (activeService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true) {
                CrossAppUiResult(true, "Pressed back")
            } else {
                missingService()
            }

        override fun pressHome(): CrossAppUiResult =
            if (activeService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true) {
                CrossAppUiResult(true, "Pressed home")
            } else {
                missingService()
            }

        private fun missingService(): CrossAppUiResult =
            CrossAppUiResult(false, "Aura Accessibility Service is not enabled")
    }

    private fun findNode(selector: CrossAppUiSelector): AccessibilityNodeInfo? {
        val matches = rootNodes()
            .flatMap { it.depthFirst() }
            .filter { it.matches(selector) }
            .toList()
        return matches.getOrNull(selector.occurrence.coerceAtLeast(0))
    }

    private fun scrollableNode(): AccessibilityNodeInfo? =
        rootNodes()
            .flatMap { it.depthFirst() }
            .firstOrNull { it.visibleToUser() && it.isScrollable && it.isEnabled }

    private fun findFocusedEditable(): AccessibilityNodeInfo? =
        rootNodes().firstNotNullOfOrNull { root ->
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.visibleToUser() && it.isEditable }
        } ?: rootNodes()
            .flatMap { it.depthFirst() }
            .firstOrNull { it.visibleToUser() && it.isEditable && it.isEnabled }

    private fun inspectNodes(packageName: String?, maxNodes: Int): String {
        val nodes = rootNodes()
            .flatMap { it.depthFirst() }
            .filter { node ->
                node.visibleToUser() &&
                    (packageName.isNullOrBlank() || node.packageName?.toString() == packageName) &&
                    node.isWorthInspecting()
            }
            .take(maxNodes + 1)
            .toList()
        if (nodes.isEmpty()) return ""
        val visibleNodes = nodes.take(maxNodes)
        val lines = visibleNodes.mapIndexed { index, node -> "${index + 1}. ${node.inspectSummary()}" }
        val suffix = if (nodes.size > maxNodes) "\n... truncated after $maxNodes nodes" else ""
        return "Visible UI nodes:\n${lines.joinToString("\n")}$suffix"
    }

    private fun visiblePackageNames(): Set<String> =
        rootNodes()
            .flatMap { it.depthFirst() }
            .filter { it.visibleToUser() }
            .mapNotNull { it.packageName?.toString()?.takeIf { packageName -> packageName.isNotBlank() } }
            .toSet()

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.isEnabled && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        val bounds = node.screenBounds()
        return !bounds.isEmpty() && dispatchTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun dispatchTap(x: Float, y: Float, durationMillis: Long = 80L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMillis.coerceAtLeast(1L)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMillis: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMillis.coerceIn(50L, 2_000L)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun rootNodes(): List<AccessibilityNodeInfo> =
        (windows.orEmpty().mapNotNull { it.root } + listOfNotNull(rootInActiveWindow))
            .distinctBy { System.identityHashCode(it) }

    private fun AccessibilityNodeInfo.depthFirst(): Sequence<AccessibilityNodeInfo> = sequence {
        yield(this@depthFirst)
        for (index in 0 until childCount) {
            getChild(index)?.let { child -> yieldAll(child.depthFirst()) }
        }
    }

    private fun AccessibilityNodeInfo.matches(selector: CrossAppUiSelector): Boolean {
        if (!visibleToUser()) return false
        if (selector.enabledOnly && !isEnabled) return false
        if (selector.clickableOnly && !isClickable) return false
        if (selector.editableOnly && !isEditable) return false
        if (!selector.packageName.isNullOrBlank() && packageName?.toString() != selector.packageName) return false
        if (!selector.className.isNullOrBlank() && className?.toString()?.contains(selector.className, ignoreCase = true) != true) {
            return false
        }
        if (!selector.viewId.isNullOrBlank() && viewIdResourceName != selector.viewId) return false
        if (!selector.text.isNullOrBlank() && !matchesValue(selector.text, partialMatch = selector.partialMatch)) return false
        if (
            !selector.contentDescription.isNullOrBlank() &&
            !contentDescription.toStringOrEmpty().matchesExpected(selector.contentDescription, selector.partialMatch)
        ) {
            return false
        }
        return selector.hasAnyTarget()
    }

    private fun AccessibilityNodeInfo.matchesValue(expected: String, partialMatch: Boolean): Boolean =
        text.toStringOrEmpty().matchesExpected(expected, partialMatch) ||
            contentDescription.toStringOrEmpty().matchesExpected(expected, partialMatch)

    private fun CharSequence?.toStringOrEmpty(): String = this?.toString().orEmpty()

    private fun String.matchesExpected(expected: String, partialMatch: Boolean): Boolean =
        if (partialMatch) contains(expected, ignoreCase = true) else equals(expected, ignoreCase = true)

    private fun AccessibilityNodeInfo.visibleToUser(): Boolean = isVisibleToUser

    private fun AccessibilityNodeInfo.screenBounds(): Rect =
        Rect().also { getBoundsInScreen(it) }

    private fun nodeSummary(node: AccessibilityNodeInfo): String {
        val label = node.text?.toString()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            ?: node.viewIdResourceName
            ?: node.className?.toString()
            ?: "target"
        return label.take(80)
    }

    private fun AccessibilityNodeInfo.isWorthInspecting(): Boolean =
        !text.isNullOrBlank() ||
            !contentDescription.isNullOrBlank() ||
            !viewIdResourceName.isNullOrBlank() ||
            isClickable ||
            isEditable ||
            isScrollable

    private fun AccessibilityNodeInfo.inspectSummary(): String {
        val bounds = screenBounds()
        val flags = listOfNotNull(
            "clickable".takeIf { isClickable },
            "editable".takeIf { isEditable },
            "scrollable".takeIf { isScrollable },
            "disabled".takeIf { !isEnabled }
        )
        return listOfNotNull(
            "pkg=${packageName.toStringOrEmpty()}".takeIf { !packageName.isNullOrBlank() },
            "class=${className.toStringOrEmpty()}".takeIf { !className.isNullOrBlank() },
            "id=$viewIdResourceName".takeIf { !viewIdResourceName.isNullOrBlank() },
            "text=${text.toStringOrEmpty().inspectValue()}".takeIf { !text.isNullOrBlank() },
            "desc=${contentDescription.toStringOrEmpty().inspectValue()}".takeIf { !contentDescription.isNullOrBlank() },
            "bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}".takeIf { !bounds.isEmpty() },
            "flags=${flags.joinToString(",")}".takeIf { flags.isNotEmpty() }
        ).joinToString(" ")
    }

    private fun String.inspectValue(): String =
        replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)

    private fun CrossAppUiSelector.hasAnyTarget(): Boolean =
        !text.isNullOrBlank() ||
            !contentDescription.isNullOrBlank() ||
            !viewId.isNullOrBlank() ||
            !className.isNullOrBlank()
}
