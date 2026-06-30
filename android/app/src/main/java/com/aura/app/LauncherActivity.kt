package com.aura.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.WindowManager
import com.aura.app.automations.AuraAutomationAccessibilityService
import com.aura.app.automations.AutomationPermissionSettingsIntents
import com.aura.app.automations.AutomationPermissionStatus
import com.aura.app.automations.CrossAppAutomationController
import com.aura.app.ui.AuraLauncherApp
import com.aura.app.ui.LauncherViewModel
import com.aura.app.ui.theme.AuraTheme

class LauncherActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModel.Factory(application.auraContainer)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.refreshApps(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        setContent {
            AuraTheme {
                AuraLauncherApp(
                    viewModel = viewModel,
                    onRequestVoicePermissions = ::requestVoicePermissions,
                    onRequestAutomationPermissions = ::requestAutomationPermissions,
                    onOpenHomeSettings = ::openHomeSettings,
                    onQuitApp = ::quitApp,
                    onMinimizeApp = { moveTaskToBack(true) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isDefault = isDefaultLauncher(this)
        viewModel.setIsDefaultLauncher(isDefault)
        viewModel.refreshAutomations()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    override fun onPause() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: Exception) {
        }
        super.onPause()
    }

    private fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val defaultLauncherPackage = resolveInfo?.activityInfo?.packageName
        return defaultLauncherPackage == context.packageName
    }

    private fun requestVoicePermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestAutomationPermissions(status: AutomationPermissionStatus?) {
        if (status != null) {
            startActivitySafely(AutomationPermissionSettingsIntents.intentFor(this, status))
            return
        }

        val runtimePermissions = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (runtimePermissions.isNotEmpty()) {
            permissionLauncher.launch(runtimePermissions.toTypedArray())
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            startActivitySafely(AutomationPermissionSettingsIntents.appDetailsIntent(this))
            return
        }
        if (!AuraAutomationAccessibilityService.isEnabled()) {
            startActivitySafely(CrossAppAutomationController.openAccessibilitySettingsIntent())
        }
    }

    private fun startActivitySafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(AutomationPermissionSettingsIntents.appDetailsIntent(this))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    private fun openHomeSettings() {
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun quitApp() {
        moveTaskToBack(true)
        finishAndRemoveTask()
    }
}
