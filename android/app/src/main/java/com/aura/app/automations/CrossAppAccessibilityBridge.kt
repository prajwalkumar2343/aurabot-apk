package com.aura.app.automations

data class CrossAppUiSelector(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val partialMatch: Boolean = true,
    val clickableOnly: Boolean = false,
    val editableOnly: Boolean = false,
    val enabledOnly: Boolean = true,
    val occurrence: Int = 0
)

data class CrossAppUiResult(
    val success: Boolean,
    val message: String
)

interface CrossAppAccessibilityBridge {
    fun isEnabled(): Boolean
    fun tap(selector: CrossAppUiSelector): CrossAppUiResult
    fun longPress(selector: CrossAppUiSelector): CrossAppUiResult
    fun tapBounds(left: Int, top: Int, right: Int, bottom: Int): Boolean
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMillis: Long): Boolean
    fun scroll(selector: CrossAppUiSelector?, direction: String): CrossAppUiResult
    fun typeText(text: String, selector: CrossAppUiSelector? = null): CrossAppUiResult
    fun clearText(selector: CrossAppUiSelector): CrossAppUiResult
    fun has(selector: CrossAppUiSelector): CrossAppUiResult
    fun inspect(packageName: String? = null, maxNodes: Int = 40): CrossAppUiResult
    fun pressBack(): CrossAppUiResult
    fun pressHome(): CrossAppUiResult
}
