package com.aura.app.apps

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val icon: Drawable?
) {
    val isAuraSettingsShortcut: Boolean
        get() = packageName == AURA_SETTINGS_SHORTCUT_PACKAGE

    companion object {
        const val AURA_SETTINGS_SHORTCUT_PACKAGE = "com.aura.app.settings"
    }
}
