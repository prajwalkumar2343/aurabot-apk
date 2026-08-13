package com.aura.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
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
import androidx.lifecycle.lifecycleScope
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.WindowManager
import com.aura.app.automations.AutomationPermissionSettingsIntents
import com.aura.app.automations.AutomationPermissionStatus
import com.aura.app.ui.AuraLauncherApp
import com.aura.app.ui.LauncherViewModel
import com.aura.app.ui.theme.AuraTheme
import com.aura.app.widgets.AndroidWidgetHostController
import com.aura.app.widgets.HostedAndroidWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LauncherActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModel.Factory(application.auraContainer)
    }

    private var startVoiceAfterPermission = false
    private var pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pinWidgetDialog: AlertDialog? = null
    private val requestedSurface = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val widgetHostController by lazy {
        AndroidWidgetHostController(
            context = this,
            repository = application.auraContainer.auraWidgetRepository,
            scope = lifecycleScope
        )
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (startVoiceAfterPermission) {
                startVoiceAfterPermission = false
                if (viewModel.startPushToTalk().not()) {
                    viewModel.showError("Microphone permission is required")
                }
            }
        }

    private val widgetConfigureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val appWidgetId = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                pendingAppWidgetId
            ) ?: pendingAppWidgetId
            pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            if (result.resultCode == Activity.RESULT_OK) {
                persistBoundWidget(appWidgetId)
            } else {
                widgetHostController.abandonAppWidgetId(appWidgetId)
            }
        }

    private val widgetPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val fallbackId = pendingAppWidgetId
            if (result.resultCode != Activity.RESULT_OK) {
                pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                widgetHostController.abandonAppWidgetId(fallbackId)
                return@registerForActivityResult
            }
            val appWidgetId = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                fallbackId
            ) ?: fallbackId
            pendingAppWidgetId = appWidgetId
            configureOrPersistWidget(appWidgetId)
        }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.refreshApps(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAppWidgetId = savedInstanceState?.getInt(
            PENDING_APP_WIDGET_ID_KEY,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        requestedSurface.value = intent.getStringExtra(EXTRA_REQUESTED_SURFACE)
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
                    onStartVoice = ::requestAndStartVoice,
                    onRequestAutomationPermissions = ::requestAutomationPermissions,
                    onAddAndroidWidget = ::launchAndroidWidgetPicker,
                    onResizeAndroidWidget = ::resizeAndroidWidget,
                    onRemoveAndroidWidget = ::removeAndroidWidget,
                    createHostedWidgetView = widgetHostController::createView,
                    onOpenHomeSettings = ::openHomeSettings,
                    onQuitApp = ::quitApp,
                    onMinimizeApp = { moveTaskToBack(true) },
                    requestedSurface = requestedSurface.value,
                    onRequestedSurfaceHandled = { requestedSurface.value = null }
                )
            }
        }
        handlePinWidgetRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_REQUESTED_SURFACE)?.let { requestedSurface.value = it }
        handlePinWidgetRequest(intent)
    }

    override fun onStart() {
        super.onStart()
        try {
            widgetHostController.startListening()
        } catch (error: Exception) {
            viewModel.showError(error.message ?: "Could not start Android widgets")
        }
        lifecycleScope.launch {
            try {
                val ignoredIds = pendingAppWidgetId
                    .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
                    ?.let(::setOf)
                    .orEmpty()
                widgetHostController.reconcile(ignoredIds)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                viewModel.showError(error.message ?: "Could not restore Android widgets")
            }
        }
    }

    override fun onStop() {
        runCatching { widgetHostController.stopListening() }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(PENDING_APP_WIDGET_ID_KEY, pendingAppWidgetId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        pinWidgetDialog?.dismiss()
        pinWidgetDialog = null
        super.onDestroy()
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

    private fun requestAndStartVoice() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            if (!viewModel.startPushToTalk()) {
                viewModel.showError("Could not start microphone listening")
            }
            return
        }
        startVoiceAfterPermission = true
        requestVoicePermissions()
    }

    private fun requestAutomationPermissions(status: AutomationPermissionStatus?) {
        if (status != null) {
            startActivitySafely(AutomationPermissionSettingsIntents.intentFor(this, status))
            return
        }

        val runtimePermissions = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (BuildConfig.DIRECT_SMS_AVAILABLE) {
                add(Manifest.permission.SEND_SMS)
            }
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

    private fun launchAndroidWidgetPicker() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)) {
            viewModel.showError("This device does not support Android widgets")
            return
        }
        val appWidgetId = widgetHostController.allocateAppWidgetId()
        pendingAppWidgetId = appWidgetId
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        try {
            widgetPickerLauncher.launch(intent)
        } catch (error: Exception) {
            pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            widgetHostController.abandonAppWidgetId(appWidgetId)
            viewModel.showError(
                if (error is ActivityNotFoundException) {
                    "No Android widget picker is available"
                } else {
                    error.message ?: "Could not open the Android widget picker"
                }
            )
        }
    }

    private fun configureOrPersistWidget(appWidgetId: Int) {
        val provider = widgetHostController.appWidgetInfo(appWidgetId)
        val configure = provider?.configure
        if (configure == null) {
            pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            persistBoundWidget(appWidgetId)
            return
        }
        val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        try {
            widgetConfigureLauncher.launch(configureIntent)
        } catch (error: Exception) {
            pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            widgetHostController.abandonAppWidgetId(appWidgetId)
            viewModel.showError(
                if (error is ActivityNotFoundException) {
                    "This widget's setup screen is unavailable"
                } else {
                    error.message ?: "Could not configure the Android widget"
                }
            )
        }
    }

    private fun persistBoundWidget(appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        lifecycleScope.launch {
            var failureMessage = "The selected Android widget is unavailable"
            val persisted = try {
                widgetHostController.persistBoundWidget(appWidgetId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failureMessage = error.message ?: "Could not save the Android widget"
                null
            }
            if (persisted == null) {
                widgetHostController.abandonAppWidgetId(appWidgetId)
                viewModel.showError(failureMessage)
            }
        }
    }

    private fun resizeAndroidWidget(widget: HostedAndroidWidget, spanX: Int, spanY: Int) {
        lifecycleScope.launch {
            try {
                val resized = widgetHostController.resize(widget.appWidgetId, spanX, spanY)
                if (resized == null) {
                    viewModel.showError("This Android widget is no longer available")
                } else if (
                    resized.spanX == widget.spanX &&
                    resized.spanY == widget.spanY &&
                    (spanX != widget.spanX || spanY != widget.spanY)
                ) {
                    viewModel.showError("This widget does not support that size")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                viewModel.showError(error.message ?: "Could not resize the Android widget")
            }
        }
    }

    private fun removeAndroidWidget(appWidgetId: Int) {
        lifecycleScope.launch {
            try {
                widgetHostController.remove(appWidgetId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                viewModel.showError(error.message ?: "Could not remove the Android widget")
            }
        }
    }

    private fun handlePinWidgetRequest(sourceIntent: Intent?) {
        if (sourceIntent?.action != LauncherApps.ACTION_CONFIRM_PIN_APPWIDGET) return
        setIntent(Intent(sourceIntent).setAction(null))
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = launcherApps.getPinItemRequest(sourceIntent) ?: return
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET) return
        if (!request.isValid) {
            viewModel.showError("This widget request has expired")
            return
        }
        pinWidgetDialog?.dismiss()
        val appWidgetId = widgetHostController.allocateAppWidgetId()
        val provider = request.getAppWidgetProviderInfo(this)
        val label = provider?.loadLabel(packageManager)?.toString() ?: "this widget"
        val packageName = provider?.provider?.packageName
        var settled = false
        pinWidgetDialog = AlertDialog.Builder(this)
            .setTitle("Add Android widget?")
            .setMessage(
                buildString {
                    append("Allow ")
                    append(label)
                    append(" on your Aura home screen?")
                    if (!packageName.isNullOrBlank()) {
                        append("\n\nProvider: ")
                        append(packageName)
                    }
                }
            )
            .setNegativeButton("Cancel") { _, _ ->
                settled = true
                widgetHostController.abandonAppWidgetId(appWidgetId)
            }
            .setPositiveButton("Add") { _, _ ->
                val accepted = runCatching {
                    request.accept(
                        Bundle().apply {
                            putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                    )
                }.getOrDefault(false)
                settled = true
                if (accepted) {
                    persistBoundWidget(appWidgetId)
                } else {
                    widgetHostController.abandonAppWidgetId(appWidgetId)
                    viewModel.showError("The widget request was not accepted")
                }
            }
            .setOnCancelListener {
                settled = true
                widgetHostController.abandonAppWidgetId(appWidgetId)
            }
            .create()
        pinWidgetDialog?.setOnDismissListener {
            if (!settled) {
                widgetHostController.abandonAppWidgetId(appWidgetId)
            }
            pinWidgetDialog = null
        }
        pinWidgetDialog?.show()
    }

    private fun quitApp() {
        moveTaskToBack(true)
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_REQUESTED_SURFACE = "com.aura.app.extra.REQUESTED_SURFACE"
        const val SURFACE_SETTINGS = "settings"
        private const val PENDING_APP_WIDGET_ID_KEY = "pending_app_widget_id"
    }
}
