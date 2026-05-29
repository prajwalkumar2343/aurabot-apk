package com.aura.app.apps

import android.content.Intent
import android.content.pm.PackageManager

class AppsRepository(
    private val packageManager: PackageManager
) {
    fun loadLaunchableApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { result ->
                val activity = result.activityInfo ?: return@mapNotNull null
                AppInfo(
                    label = result.loadLabel(packageManager).toString(),
                    packageName = activity.packageName,
                    componentName = android.content.ComponentName(activity.packageName, activity.name),
                    icon = result.loadIcon(packageManager)
                )
            }
            .distinctBy { it.componentName.flattenToString() }
            .sortedBy { it.label.lowercase() }
    }

    fun launchIntentFor(app: AppInfo): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = app.componentName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
}
