package com.aura.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.app.AppContainer
import com.aura.app.automations.AutomationActionTypeSets
import com.aura.app.automations.AutomationActionTypes
import com.aura.app.automations.AutomationEvents
import com.aura.app.automations.AutomationPermissionStatus
import com.aura.app.automations.AutomationRunLog
import com.aura.app.automations.AutomationSpec
import com.aura.app.automations.AutomationTriggerTypes
import com.aura.app.apps.AppInfo
import com.aura.app.assistant.DEFAULT_GEMINI_MODEL
import com.aura.app.assistant.LlmProvider
import com.aura.app.assistant.MemoryAppProposal
import com.aura.app.assistant.MessageRole
import com.aura.app.miniapps.MiniAppBundle
import com.aura.app.miniapps.MiniAppComponent
import com.aura.app.miniapps.MiniAppComponentItem
import com.aura.app.miniapps.MiniAppEvolutionSuggestion
import com.aura.app.miniapps.MiniAppField
import com.aura.app.miniapps.MiniAppInstall
import com.aura.app.miniapps.MiniAppRecord
import com.aura.app.miniapps.MiniAppRevisionPreview
import com.aura.app.miniapps.MiniAppVersion
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectTapGestures

private enum class Route(val title: String) {
    Home("Aura"),
    Apps("Apps"),
    Assistant("Assistant"),
    Tasks("Tasks"),
    Memory("Memory"),
    Automations("Automations"),
    Settings("Settings"),
    Models("Models"),
    MiniApp("MiniApp")
}

enum class AuraPresenceMode {
    Idle,
    Focused,
    Listening,
    Hearing,
    Thinking
}

internal data class PhoneLayoutProfile(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val bottomBarHorizontalPadding: Dp,
    val bottomBarVerticalPadding: Dp,
    val bottomNavItemSize: Dp,
    val appGridColumns: Int,
    val storeGridColumns: Int,
    val actionGridColumns: Int,
    val dense: Boolean,
    val short: Boolean
)

internal fun phoneLayoutProfile(width: Dp, height: Dp): PhoneLayoutProfile {
    val dense = width < 360.dp
    val short = height < 680.dp
    return PhoneLayoutProfile(
        horizontalPadding = when {
            width < 340.dp -> 12.dp
            width < 390.dp -> 16.dp
            else -> 20.dp
        },
        verticalPadding = if (short) 10.dp else 16.dp,
        bottomBarHorizontalPadding = when {
            width < 340.dp -> 16.dp
            width < 390.dp -> 28.dp
            else -> 60.dp
        },
        bottomBarVerticalPadding = if (short) 10.dp else 20.dp,
        bottomNavItemSize = if (dense) 46.dp else 52.dp,
        appGridColumns = when {
            width < 340.dp -> 2
            width < 520.dp -> 3
            else -> 4
        },
        storeGridColumns = if (width < 360.dp) 1 else 2,
        actionGridColumns = if (width < 360.dp) 2 else 3,
        dense = dense,
        short = short
    )
}

@Composable
fun AuraLauncherApp(
    viewModel: LauncherViewModel,
    onRequestVoicePermissions: () -> Unit,
    onRequestAutomationPermissions: (AutomationPermissionStatus?) -> Unit,
    onOpenHomeSettings: () -> Unit,
    onQuitApp: () -> Unit,
    onMinimizeApp: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingComplete = state.session.onboardingComplete
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showHomePrompt by remember { mutableStateOf(false) }

    val wallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            viewModel.setWallpaper(uri.toString())
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    viewModel.setAttachedImage(base64, mimeType)
                }
            } catch (e: Exception) {
                viewModel.showError("Could not load image: ${e.message}")
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: android.graphics.Bitmap? ->
        if (bitmap != null) {
            try {
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
                val bytes = outputStream.toByteArray()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                viewModel.setAttachedImage(base64, "image/jpeg")
            } catch (e: Exception) {
                viewModel.showError("Could not capture image: ${e.message}")
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            try {
                val container = (context.applicationContext as com.aura.app.AuraApplication).container
                container.voiceSpeaker.stop()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
    }

    LaunchedEffect(state.session.homeSettingsPrompted, onboardingComplete, state.session.appMode) {
        if (state.session.appMode == "launcher" && onboardingComplete && !state.session.homeSettingsPrompted) {
            showHomePrompt = true
        }
    }

    if (showHomePrompt) {
        AlertDialog(
            onDismissRequest = {
                showHomePrompt = false
                viewModel.markHomeSettingsPrompted()
            },
            title = { Text("Set Aura as Home app?") },
            text = { Text("Aura is ready to run locally. Open Android Home app settings so you can make it your default launcher?") },
            confirmButton = {
                Button(onClick = {
                    showHomePrompt = false
                    viewModel.markHomeSettingsPrompted()
                    onOpenHomeSettings()
                }) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showHomePrompt = false
                    viewModel.markHomeSettingsPrompted()
                }) {
                    Text("Not now")
                }
            }
        )
    }


    if (!state.sessionLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
    } else if (!onboardingComplete) {
        OnboardingScreen(
            state = state,
            onRequestPermissions = onRequestVoicePermissions,
            onCreateAccount = viewModel::register,
            onSignIn = viewModel::login,
            onFinishOnboarding = { appMode, provider, apiKey, modelId, bgListening ->
                viewModel.setAppMode(appMode)
                viewModel.setLlmProvider(provider)
                when (provider) {
                    LlmProvider.Gemini -> {
                        viewModel.setGoogleApiKey(apiKey)
                        if (modelId.isNotBlank()) viewModel.setGoogleModel(modelId)
                    }
                    LlmProvider.OpenAI -> {
                        viewModel.setOpenAiApiKey(apiKey)
                        if (modelId.isNotBlank()) viewModel.setOpenAiModel(modelId)
                    }
                    LlmProvider.OpenRouter -> {
                        viewModel.setOpenRouterApiKey(apiKey)
                        if (modelId.isNotBlank()) viewModel.setOpenRouterModel(modelId)
                    }
                }
                viewModel.setBackgroundListening(bgListening)
                viewModel.setOnboardingComplete(true)
            }
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = phoneLayoutProfile(maxWidth, maxHeight)
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!state.isDefaultLauncher) {
                        val current = navController.currentBackStackEntryAsState().value?.destination?.route
                        val isDark = isSystemInDarkTheme()
                        val routes = listOf(Route.Home, Route.Settings)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(
                                    horizontal = layout.bottomBarHorizontalPadding,
                                    vertical = layout.bottomBarVerticalPadding
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .glassCard(shape = RoundedCornerShape(28.dp))
                                    .padding(horizontal = if (layout.dense) 8.dp else 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (layout.dense) 4.dp else 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                routes.forEach { route ->
                                    val selected = current == route.name
                                    val iconScale by animateFloatAsState(
                                        targetValue = if (selected) 1.1f else 0.95f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "nav_icon_scale_${route.name}"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(layout.bottomNavItemSize)
                                            .scale(iconScale)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (selected) {
                                                    if (isDark) Color.White.copy(alpha = 0.12f)
                                                    else Color.Black.copy(alpha = 0.08f)
                                                } else {
                                                    Color.Transparent
                                                }
                                            )
                                            .clickable {
                                                navController.navigate(route.name) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                routeIcon(route),
                                                contentDescription = route.title,
                                                modifier = Modifier.size(if (layout.dense) 20.dp else 22.dp),
                                                tint = if (selected) {
                                                    MaterialTheme.colorScheme.onBackground
                                                } else {
                                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                                }
                                            )
                                            if (selected) {
                                                Spacer(Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isDark) Color(0xFF8B5CF6)
                                                            else Color(0xFF6366F1)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Route.Home.name,
                modifier = Modifier.padding(padding),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(400))
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(400))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(400))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(400))
                }
            ) {
                composable(Route.Home.name) {
                    val appMode = state.session.appMode
                    BackHandler(enabled = appMode != "normal") {
                        if (appMode == "overlay") {
                            onMinimizeApp()
                        }
                    }
                    HomeScreen(
                        state = state,
                        onAssistantInput = viewModel::setAssistantInput,
                        onSend = viewModel::sendAssistantMessage,
                        onTalk = {
                            onRequestVoicePermissions()
                            viewModel.startPushToTalk()
                        },
                        onStopVoice = viewModel::stopVoice,
                        onOpenAssistant = { navController.navigate(Route.Assistant.name) },
                        onSwipeLeft = { navController.navigate(Route.Apps.name) },
                        onSelectWallpaper = { wallpaperLauncher.launch(arrayOf("image/*")) },
                        onOpenSettings = { navController.navigate(Route.Settings.name) },
                        onLaunchApp = { app ->
                            if (app.packageName == "com.aura.app.settings") {
                                navController.navigate(Route.Settings.name)
                            } else {
                                viewModel.launchIntent(app)?.let { intent ->
                                    if (!startActivitySafely(context, intent)) {
                                        viewModel.showError("Could not open ${app.label}")
                                    }
                                }
                            }
                        },
                        onClearAttachment = { viewModel.setAttachedImage(null, null) },
                        onLaunchPicker = { imagePickerLauncher.launch("image/*") },
                        onLaunchCamera = { cameraLauncher.launch(null) }
                    )
                }
                composable(Route.Apps.name) {
                    AppsScreen(
                        state = state,
                        onQuery = viewModel::setAppQuery,
                        onLaunchApp = { app ->
                            viewModel.launchIntent(app)?.let { intent ->
                                if (!startActivitySafely(context, intent)) {
                                    viewModel.showError("Could not open ${app.label}")
                                }
                            }
                        },
                        onOpenMiniApp = { miniApp ->
                            viewModel.openMiniApp(miniApp.id)
                            navController.navigate(Route.MiniApp.name)
                        },
                        onInstallMiniApp = viewModel::installMiniApp,
                        onCreateMiniApp = viewModel::createMiniAppFromPrompt,
                        onRefresh = viewModel::refreshApps,
                        onSwipeRight = { navController.popBackStack() }
                    )
                }
                composable(Route.MiniApp.name) {
                    MiniAppRuntimeScreen(
                        bundle = state.activeMiniApp,
                        records = state.activeMiniAppRecords,
                        versions = state.activeMiniAppVersions,
                        evolutionSuggestion = state.activeMiniAppEvolutionSuggestion,
                        revisionPreview = state.pendingMiniAppRevision,
                        revising = state.revisingMiniApp,
                        onBack = {
                            viewModel.closeMiniApp()
                            navController.popBackStack()
                        },
                        onRunAction = viewModel::runMiniAppAction,
                        onCreateRecord = viewModel::createMiniAppRecord,
                        onDeleteRecord = viewModel::deleteMiniAppRecord,
                        onRevise = viewModel::reviseActiveMiniApp,
                        onDraftEvolution = viewModel::draftMiniAppEvolution,
                        onDismissEvolution = viewModel::dismissMiniAppEvolution,
                        onAcceptRevision = viewModel::applyPendingMiniAppRevision,
                        onDismissRevision = viewModel::dismissMiniAppRevision,
                        onRollback = viewModel::rollbackActiveMiniApp,
                        onReactListRecords = viewModel::listMiniAppRecordsForRuntime,
                        onReactCreateRecord = viewModel::createMiniAppRecordForRuntime,
                        onReactUpdateRecord = viewModel::updateMiniAppRecordForRuntime,
                        onReactDeleteRecord = viewModel::deleteMiniAppRecordForRuntime
                    )
                }
                composable(Route.Assistant.name) {
                    AssistantScreen(
                        state = state,
                        onAssistantInput = viewModel::setAssistantInput,
                        onSend = viewModel::sendAssistantMessage,
                        onClearAttachment = { viewModel.setAttachedImage(null, null) },
                        onLaunchPicker = { imagePickerLauncher.launch("image/*") },
                        onLaunchCamera = { cameraLauncher.launch(null) }
                    )
                }
                composable(Route.Tasks.name) {
                    TasksScreen(state = state, onAddTodo = viewModel::addTodo, onBack = { navController.popBackStack() })
                }
                composable(Route.Memory.name) {
                    MemoryScreen(
                        state = state,
                        onAddMemory = viewModel::addMemory,
                        onCreateMiniApp = { proposalId ->
                            viewModel.createMiniAppFromMemoryProposal(proposalId) {
                                navController.navigate(Route.MiniApp.name)
                            }
                        },
                        onDismissProposal = viewModel::dismissMemoryAppProposal,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Route.Settings.name) {
                    SettingsScreen(
                        state = state,
                        onRequestVoicePermissions = onRequestVoicePermissions,
                        onOpenHomeSettings = {
                            showHomePrompt = true
                        },
                        onBackgroundListening = viewModel::setBackgroundListening,
                        onSelectWallpaper = { wallpaperLauncher.launch(arrayOf("image/*")) },
                        onClearWallpaper = { viewModel.setWallpaper(null) },
                        onSetInteractionMode = viewModel::setInteractionMode,
                        onConfigureModels = { navController.navigate(Route.Models.name) },
                        onConfigureTasks = { navController.navigate(Route.Tasks.name) },
                        onConfigureMemories = { navController.navigate(Route.Memory.name) },
                        onConfigureAutomations = { navController.navigate(Route.Automations.name) },
                        onQuitApp = onQuitApp,
                        onSetAppMode = viewModel::setAppMode
                    )
                }
                composable(Route.Automations.name) {
                    AutomationsScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onRefresh = viewModel::refreshAutomations,
                        onSetEnabled = viewModel::setAutomationEnabled,
                        onRunNow = viewModel::runAutomationNow,
                        onDelete = viewModel::deleteAutomation,
                        onOpenPermissions = onRequestAutomationPermissions
                    )
                }
                composable(Route.Models.name) {
                    ModelsScreen(
                        state = state,
                        onProviderSelected = viewModel::setLlmProvider,
                        onGoogleApiKeyChanged = viewModel::setGoogleApiKey,
                        onGoogleModelChanged = viewModel::setGoogleModel,
                        onOpenAiApiKeyChanged = viewModel::setOpenAiApiKey,
                        onOpenAiModelChanged = viewModel::setOpenAiModel,
                        onOpenRouterApiKeyChanged = viewModel::setOpenRouterApiKey,
                        onOpenRouterModelChanged = viewModel::setOpenRouterModel,
                        onLoadOpenRouterModels = viewModel::loadOpenRouterModels,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
        }
    }
}

private fun routeIcon(route: Route) = when (route) {
    Route.Home -> Icons.Rounded.Home
    Route.Apps -> Icons.Rounded.Apps
    Route.Assistant -> Icons.Rounded.GraphicEq
    Route.Tasks -> Icons.Rounded.CheckCircle
    Route.Memory -> Icons.Rounded.Layers
    Route.Automations -> Icons.Rounded.AutoAwesome
    Route.Settings -> Icons.Rounded.Settings
    Route.Models -> Icons.Rounded.Settings
    Route.MiniApp -> Icons.Rounded.Store
}
