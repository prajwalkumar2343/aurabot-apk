package com.aura.app.automations

interface CrossAppAccessibilityBridge {
    fun isEnabled(): Boolean
    fun tapText(text: String, partialMatch: Boolean = true): Boolean
    fun tapBounds(left: Int, top: Int, right: Int, bottom: Int): Boolean
    fun typeText(text: String, targetText: String? = null, viewId: String? = null): Boolean
    fun waitForText(text: String, partialMatch: Boolean = true): Boolean
    fun hasText(text: String, partialMatch: Boolean = true): Boolean
    fun pressBack(): Boolean
    fun pressHome(): Boolean
}
