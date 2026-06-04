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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.app.AppContainer
import com.aura.app.apps.AppInfo
import com.aura.app.assistant.LlmProvider
import com.aura.app.assistant.MessageRole
import com.aura.app.miniapps.MiniAppBundle
import com.aura.app.miniapps.MiniAppComponent
import com.aura.app.miniapps.MiniAppComponentItem
import com.aura.app.miniapps.MiniAppField
import com.aura.app.miniapps.MiniAppInstall
import com.aura.app.miniapps.MiniAppRecord
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
    Settings("Settings"),
    Models("Models"),
    MiniApp("MiniApp")
}

private enum class AuraPresenceMode {
    Idle,
    Focused,
    Listening,
    Hearing,
    Thinking
}

@Composable
fun AuraLauncherApp(
    viewModel: LauncherViewModel,
    onRequestVoicePermissions: () -> Unit,
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
                            .padding(horizontal = 60.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .glassCard(shape = RoundedCornerShape(28.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                        .size(52.dp)
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
                                            modifier = Modifier.size(22.dp),
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
                        }
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
                        onBack = {
                            viewModel.closeMiniApp()
                            navController.popBackStack()
                        },
                        onRunAction = viewModel::runMiniAppAction,
                        onCreateRecord = viewModel::createMiniAppRecord,
                        onDeleteRecord = viewModel::deleteMiniAppRecord
                    )
                }
                composable(Route.Assistant.name) {
                    AssistantScreen(
                        state = state,
                        onAssistantInput = viewModel::setAssistantInput,
                        onSend = viewModel::sendAssistantMessage
                    )
                }
                composable(Route.Tasks.name) {
                    TasksScreen(state = state, onAddTodo = viewModel::addTodo, onBack = { navController.popBackStack() })
                }
                composable(Route.Memory.name) {
                    MemoryScreen(state = state, onAddMemory = viewModel::addMemory, onBack = { navController.popBackStack() })
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
                        onQuitApp = onQuitApp,
                        onSetAppMode = viewModel::setAppMode
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

@Composable
private fun HomeScreen(
    state: LauncherUiState,
    onAssistantInput: (String) -> Unit,
    onSend: () -> Unit,
    onTalk: () -> Unit,
    onStopVoice: () -> Unit,
    onOpenAssistant: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSelectWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit
) {
    val presenceMode = when {
        state.loading -> AuraPresenceMode.Thinking
        state.status.speechDetected || state.status.rmsLevel > 2 -> AuraPresenceMode.Hearing
        state.status.running -> AuraPresenceMode.Listening
        state.assistantInput.isNotBlank() -> AuraPresenceMode.Focused
        else -> AuraPresenceMode.Idle
    }
    var showLongPressMenu by remember { mutableStateOf(false) }
    var totalDrag = 0f
    ScreenShell(
        wallpaperUri = state.session.wallpaperUri,
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        showLongPressMenu = true
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag < -150f) {
                            onSwipeLeft()
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                )
            }
    ) {
        AuraEyes(
            mode = presenceMode,
            voiceLevel = state.status.rmsLevel,
            commandText = state.assistantInput,
            emotion = state.currentEmotion,
            isSpeaking = state.isSpeaking,
            interactionMode = state.session.interactionMode,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        AssistantComposer(state.assistantInput, onAssistantInput, onSend)
    }

    if (showLongPressMenu) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { showLongPressMenu = false },
            contentAlignment = Alignment.Center
        ) {
            var animateTrigger by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                animateTrigger = true
            }
            AnimatedVisibility(
                visible = animateTrigger,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .width(280.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DESKTOP OPTIONS",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        if (state.session.appMode == "launcher") {
                            Button(
                                onClick = {
                                    showLongPressMenu = false
                                    onSelectWallpaper()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onBackground,
                                    contentColor = MaterialTheme.colorScheme.background
                                  )
                            ) {
                                Icon(Icons.Rounded.Image, null)
                                Spacer(Modifier.width(8.dp))
                                Text("WALLPAPER", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        Button(
                            onClick = {
                                showLongPressMenu = false
                                onOpenSettings()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Rounded.Settings, null)
                            Spacer(Modifier.width(8.dp))
                            Text("SETTINGS", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeChatLayer(state: LauncherUiState, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp, max = 176.dp)
            .glassCard(shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ACTIVE COGNITIVE TRANSMISSIONS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.primary)
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                state.recentMessages.forEach { message ->
                    val isUser = message.role == MessageRole.User
                    Text(
                        text = if (isUser) "YOU: ${message.text}" else "AURA: ${message.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUser) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (state.loading) {
                    Text(
                        text = "AURA IS THINKING...",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            if (state.appBlocks.isNotEmpty()) {
                val block = state.appBlocks.first()
                Text(
                    text = "${block.label.uppercase()} BLOCKED FOR ${block.remainingMinutes()}M",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AuraEyes(
    mode: AuraPresenceMode,
    voiceLevel: Int,
    commandText: String,
    emotion: String,
    isSpeaking: Boolean,
    interactionMode: String,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val infiniteTransition = rememberInfiniteTransition(label = "aura_eyes")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(6_200, easing = LinearEasing)),
        label = "phase"
    )
    val blink by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4_200
                1f at 0
                1f at 3_000
                0.18f at 3_140
                1f at 3_320
                1f at 4_200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )
    val dotBreathe by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_breathe"
    )
    val dotAlphaTarget = if (isSpeaking || mode != AuraPresenceMode.Idle) 1f else 0f
    val dotAlpha by animateFloatAsState(dotAlphaTarget, tween(420), label = "dot_alpha")

    // Setup speech wave transition to pulse the height of the eyes when TTS is speaking
    val speechWave by if (isSpeaking) {
        val speechTransition = rememberInfiniteTransition(label = "speech_eyes")
        speechTransition.animateFloat(
            initialValue = 0.78f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(120, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "speech_wave"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // Map emotions to target scales, offsets, and slant tilts
    val baseWidthScale = when (emotion) {
        "thinking" -> 1.15f
        "excited" -> 1.1f
        else -> 1.0f
    }
    val baseHeightScale = when (emotion) {
        "happy" -> 0.52f
        "thinking" -> 0.45f
        "sad" -> 0.62f
        "angry" -> 0.72f
        "excited" -> 1.2f
        else -> 1.0f
    }
    val targetYOffset = when (emotion) {
        "happy" -> -12f
        "excited" -> -5f
        "sad" -> 12f
        else -> 0f
    }
    val targetXOffset = when (emotion) {
        "thinking" -> 8f
        else -> 0f
    }
    val targetTilt = when (emotion) {
        "angry" -> 14f
        else -> 0f
    }

    // Smoothly animate all transitions over 320ms to morph between emotions
    val widthScale by animateFloatAsState(baseWidthScale, tween(320), label = "width_scale")
    val heightScale by animateFloatAsState(baseHeightScale, tween(320), label = "height_scale")
    val yOffset by animateFloatAsState(targetYOffset, tween(320), label = "y_offset")
    val xOffset by animateFloatAsState(targetXOffset, tween(320), label = "x_offset")
    val tiltDegrees by animateFloatAsState(targetTilt, tween(320), label = "tilt_degrees")

    val eyeOpenTarget = when (mode) {
        AuraPresenceMode.Thinking -> 0.72f
        AuraPresenceMode.Hearing -> 1f
        AuraPresenceMode.Listening -> 0.92f
        AuraPresenceMode.Focused -> 0.84f
        AuraPresenceMode.Idle -> 0.9f
    }
    val focusAmountTarget = when (mode) {
        AuraPresenceMode.Thinking -> 0f
        AuraPresenceMode.Hearing -> 0.4f
        AuraPresenceMode.Listening -> 0.18f
        AuraPresenceMode.Focused -> 0.52f
        AuraPresenceMode.Idle -> 0.08f
    }

    val eyeOpen by animateFloatAsState(eyeOpenTarget * blink, tween(420), label = "eye_open")
    val focusAmount by animateFloatAsState(focusAmountTarget, tween(420), label = "focus_amount")

    val eyeColors = remember(primaryColor, secondaryColor) { listOf(primaryColor, secondaryColor) }
    val dotGlowColors = remember(primaryColor, secondaryColor, dotAlpha) {
        listOf(
            primaryColor.copy(alpha = 0.12f * dotAlpha),
            secondaryColor.copy(alpha = 0.06f * dotAlpha),
            Color.Transparent
        )
    }
    val dotColors = remember(primaryColor, secondaryColor) {
        listOf(
            primaryColor,
            secondaryColor.copy(alpha = 0.35f),
            Color.Transparent
        )
    }
    val hearingHaloColors = remember(primaryColor, secondaryColor) {
        listOf(
            primaryColor.copy(alpha = 0.22f),
            secondaryColor.copy(alpha = 0.22f * 0.3f),
            Color.Transparent
        )
    }
    val listeningHaloColors = remember(primaryColor, secondaryColor) {
        listOf(
            primaryColor.copy(alpha = 0.14f),
            secondaryColor.copy(alpha = 0.14f * 0.3f),
            Color.Transparent
        )
    }
    val focusedHaloColors = remember(primaryColor, secondaryColor) {
        listOf(
            primaryColor.copy(alpha = 0.12f),
            secondaryColor.copy(alpha = 0.12f * 0.3f),
            Color.Transparent
        )
    }
    val idleHaloColors = remember(primaryColor, secondaryColor) {
        listOf(
            primaryColor.copy(alpha = 0.06f),
            secondaryColor.copy(alpha = 0.06f * 0.3f),
            Color.Transparent
        )
    }

    val statusText = when {
        isSpeaking -> emotion.uppercase()
        mode == AuraPresenceMode.Thinking -> "THINKING"
        mode == AuraPresenceMode.Hearing -> "HEARING"
        mode == AuraPresenceMode.Listening -> "LISTENING"
        mode == AuraPresenceMode.Focused -> "READY"
        else -> "STANDBY"
    }

    Box(
        modifier = modifier
    ) {
        Column {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.75f)
            ) {

                val wave = sin(phase)
                val commandBias = (commandText.length.coerceAtMost(32) / 32f) * focusAmount
                val voiceBias = voiceLevel.coerceIn(0, 12) / 12f
                val baseEyeWidth = size.width * 0.28f
                val baseEyeHeight = size.height * 0.32f
                
                val eyeWidth = baseEyeWidth * widthScale
                val eyeHeight = baseEyeHeight * (0.22f + eyeOpen * 0.78f) * heightScale * speechWave
                
                val eyeY = (size.height - eyeHeight) / 2f + yOffset
                val leftEyeX = size.width * 0.18f + xOffset
                val rightEyeX = size.width - size.width * 0.18f - eyeWidth - xOffset

                fun drawEye(originX: Float, direction: Float) {
                    val travelX = (wave * 0.025f + commandBias * direction) * size.width
                    val travelY = ((sin(phase * 1.7f + direction) * 0.012f) + voiceBias * 0.016f) * size.height

                    val pivotX = originX + travelX + eyeWidth / 2f
                    val pivotY = eyeY + travelY + eyeHeight / 2f
                    
                    // Tilt left eye positive, right eye negative (slants inward)
                    val tilt = tiltDegrees * direction

                    val eyeBrush = Brush.linearGradient(
                        colors = eyeColors,
                        start = Offset(originX + travelX, eyeY + travelY),
                        end = Offset(originX + travelX + eyeWidth, eyeY + travelY + eyeHeight)
                    )

                    rotate(degrees = tilt, pivot = Offset(pivotX, pivotY)) {
                        drawRoundRect(
                            brush = eyeBrush,
                            topLeft = Offset(originX + travelX, eyeY + travelY),
                            size = Size(eyeWidth, eyeHeight),
                            cornerRadius = CornerRadius(eyeWidth * 0.45f, eyeHeight * 0.45f)
                        )
                    }
                }

                if (interactionMode == "dot") {
                    if (dotAlpha > 0.01f) {
                        val baseDotRadius = size.height * 0.09f
                        val dotScale = when {
                            isSpeaking -> speechWave
                            mode == AuraPresenceMode.Thinking -> dotBreathe
                            mode == AuraPresenceMode.Hearing -> 1f + (voiceLevel.coerceIn(0, 12) / 12f) * 0.45f
                            else -> 1f
                        }
                        
                        // Outer glow ring
                        val glowBrush = Brush.radialGradient(
                            colors = dotGlowColors,
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = baseDotRadius * dotScale * 3.5f
                        )
                        drawCircle(
                            brush = glowBrush,
                            radius = baseDotRadius * dotScale * 3.5f,
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                        
                        val dotBrush = Brush.radialGradient(
                            colors = dotColors,
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = baseDotRadius * dotScale * 1.3f
                        )
                        
                        drawCircle(
                            brush = dotBrush,
                            radius = baseDotRadius * dotScale * 1.3f,
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                    }
                } else {
                    // Ambient glow halos behind each eye
                    val haloColors = when (mode) {
                        AuraPresenceMode.Thinking -> listOf(
                            primaryColor.copy(alpha = 0.18f * dotBreathe),
                            secondaryColor.copy(alpha = 0.18f * dotBreathe * 0.3f),
                            Color.Transparent
                        )
                        AuraPresenceMode.Hearing -> hearingHaloColors
                        AuraPresenceMode.Listening -> listeningHaloColors
                        AuraPresenceMode.Focused -> focusedHaloColors
                        AuraPresenceMode.Idle -> idleHaloColors
                    }
                    val glowRadius = eyeWidth * 1.6f
                    
                    // Left eye halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = haloColors,
                            center = Offset(leftEyeX + eyeWidth / 2f, eyeY + eyeHeight / 2f),
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = Offset(leftEyeX + eyeWidth / 2f, eyeY + eyeHeight / 2f)
                    )
                    // Right eye halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = haloColors,
                            center = Offset(rightEyeX + eyeWidth / 2f, eyeY + eyeHeight / 2f),
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = Offset(rightEyeX + eyeWidth / 2f, eyeY + eyeHeight / 2f)
                    )
                    
                    drawEye(leftEyeX, -1f)
                    drawEye(rightEyeX, 1f)
                }
            }
        }
    }
}

@Composable
private fun AppGridItem(app: AppInfo, onLaunchApp: (AppInfo) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "app_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onLaunchApp(app) }
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .glassCard(shape = RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                AppIcon(
                    drawable = app.icon,
                    modifier = Modifier.size(52.dp)
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.label.uppercase(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun SuggestedAppCard(app: AppInfo, onLaunchApp: (AppInfo) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "suggested_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onLaunchApp(app) }
            )
            .glassCard(shape = RoundedCornerShape(20.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                AppIcon(
                    drawable = app.icon,
                    modifier = Modifier.size(38.dp)
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label.uppercase(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AppListItem(app: AppInfo, onLaunchApp: (AppInfo) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "list_item_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onLaunchApp(app) }
            )
            .glassCard(shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                AppIcon(
                    drawable = app.icon,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label.uppercase(),
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppsScreen(
    state: LauncherUiState,
    onQuery: (String) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onOpenMiniApp: (MiniAppInstall) -> Unit,
    onInstallMiniApp: (MiniAppBundle) -> Unit,
    onCreateMiniApp: (String) -> Unit,
    onRefresh: () -> Unit,
    onSwipeRight: () -> Unit
) {
    var totalDrag = 0f
    var isGridView by remember { mutableStateOf(true) }
    var createPrompt by remember { mutableStateOf("") }

    ScreenShell(
        wallpaperUri = state.session.wallpaperUri,
        modifier = Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { totalDrag = 0f },
                onDragEnd = {
                    if (totalDrag > 150f) {
                        onSwipeRight()
                    }
                },
                onHorizontalDrag = { _, dragAmount ->
                    totalDrag += dragAmount
                }
            )
        }
    ) {

        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .glassCard(shape = CircleShape)
            ) {
                OutlinedTextField(
                    value = state.appQuery,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onBackground) },
                    trailingIcon = {
                        if (state.appQuery.isNotBlank()) {
                            IconButton(onClick = { onQuery("") }) {
                                Icon(Icons.Rounded.Clear, "Clear Search", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    },
                    placeholder = { Text("SEARCH APPS...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = CircleShape
                )
            }
 
            IconButton(
                onClick = { isGridView = !isGridView },
                modifier = Modifier
                    .size(52.dp)
                    .glassCard(shape = CircleShape)
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Rounded.Layers else Icons.Rounded.Apps,
                    contentDescription = "Toggle Layout",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.appQuery.isBlank()) {
                    AuraStoreSection(
                        installed = state.miniApps,
                        builtIns = state.builtInMiniApps,
                        prompt = createPrompt,
                        onPrompt = { createPrompt = it },
                        onOpen = onOpenMiniApp,
                        onInstall = onInstallMiniApp,
                        onCreate = {
                            onCreateMiniApp(createPrompt)
                            createPrompt = ""
                        }
                    )
                    Spacer(Modifier.height(18.dp))

                    val suggestedApps = state.apps.take(4)
                    if (suggestedApps.isNotEmpty()) {
                        Text(
                            text = "SUGGESTED APPS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            suggestedApps.forEach { app ->
                                Box(modifier = Modifier.weight(1f)) {
                                    SuggestedAppCard(app, onLaunchApp)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.filteredApps, key = { it.componentName.flattenToString() }) { app ->
                            AppGridItem(app, onLaunchApp)
                        }
                    }
                } else {
                    val groupedApps = remember(state.filteredApps) {
                        state.filteredApps.groupBy { it.label.take(1).uppercase() }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        groupedApps.forEach { (letter, appsInGroup) ->
                            item(key = letter) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.onBackground),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = letter,
                                            color = MaterialTheme.colorScheme.background,
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        appsInGroup.forEach { app ->
                                            AppListItem(app, onLaunchApp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onRefresh,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .glassCard(shape = CircleShape),
                shape = CircleShape,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh Apps List",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AuraStoreSection(
    installed: List<MiniAppInstall>,
    builtIns: List<MiniAppBundle>,
    prompt: String,
    onPrompt: (String) -> Unit,
    onOpen: (MiniAppInstall) -> Unit,
    onInstall: (MiniAppBundle) -> Unit,
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                RoundedCornerShape(28.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Store, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mini Apps",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${installed.size} installed locally",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Local",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (installed.isEmpty()) {
            MiniAppEmptyStoreCard()
        } else {
            val visibleApps = installed.take(4)
            val storeRows = ((visibleApps.size + 1) / 2).coerceAtLeast(1)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height((storeRows * 146).dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                items(visibleApps, key = { it.id }) { miniApp ->
                    MiniAppIconCard(
                        miniApp = miniApp,
                        onOpen = onOpen
                    )
                }
            }
        }

        val available = builtIns.filterNot { builtIn -> installed.any { it.id == builtIn.id } }
        if (available.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Available templates",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                available.take(2).forEach { bundle ->
                    val primary = parseMiniAppColor(bundle.theme.primary, MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(primary.copy(alpha = 0.09f))
                            .clickable { onInstall(bundle) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MiniAppAvatar(bundle.icon.value, bundle.icon.background, primary, 42.dp, 12.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bundle.metadata.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(bundle.metadata.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.Add, "Install ${bundle.metadata.name}", tint = primary)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPrompt,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                placeholder = { Text("Describe an app to create") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.42f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.28f)
                )
            )
            FilledTonalButton(
                onClick = onCreate,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(Icons.Rounded.Add, "Create Mini App")
            }
        }
    }
}

@Composable
private fun MiniAppEmptyStoreCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                RoundedCornerShape(22.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Text("Create your first local mini app", fontWeight = FontWeight.Black)
            Text("Templates and generated apps live here", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniAppIconCard(
    miniApp: MiniAppInstall,
    onOpen: (MiniAppInstall) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = parseMiniAppColor(miniApp.icon.background, MaterialTheme.colorScheme.primary)
    Column(
        modifier = modifier
            .height(134.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(primary.copy(alpha = 0.24f), MaterialTheme.colorScheme.background.copy(alpha = 0.48f))
                )
            )
            .border(BorderStroke(1.dp, primary.copy(alpha = 0.18f)), RoundedCornerShape(24.dp))
            .clickable { onOpen(miniApp) }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniAppAvatar(miniApp.icon.value, miniApp.icon.background, primary, 48.dp, 14.dp)
            Icon(Icons.Rounded.ChevronRight, "Open ${miniApp.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                miniApp.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                miniApp.category,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniAppAvatar(
    value: String,
    background: String,
    fallback: Color,
    size: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp
) {
    val color = parseMiniAppColor(background, fallback)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(
                Brush.linearGradient(
                    listOf(color, color.copy(alpha = 0.72f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            value.take(2).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun MiniAppRuntimeScreen(
    bundle: MiniAppBundle?,
    records: List<MiniAppRecord>,
    onBack: () -> Unit,
    onRunAction: (String, String) -> Unit,
    onCreateRecord: (String, String, Map<String, String>) -> Unit,
    onDeleteRecord: (String, String) -> Unit
) {
    if (bundle == null) {
        ScreenShell(wallpaperUri = null) {
            MiniAppMissingState(onBack, "Mini app is not available")
        }
        return
    }
    val firstScreen = bundle.screens.firstOrNull()
    if (firstScreen == null) {
        ScreenShell(wallpaperUri = null) {
            MiniAppMissingState(onBack, "This mini app has no screen yet")
        }
        return
    }
    var selectedScreenId by remember(bundle.id) { mutableStateOf(firstScreen.id) }
    val screen = bundle.screens.firstOrNull { it.id == selectedScreenId } ?: firstScreen
    val primary = parseMiniAppColor(bundle.theme.primary, MaterialTheme.colorScheme.primary)
    val secondary = parseMiniAppColor(bundle.theme.secondary, MaterialTheme.colorScheme.tertiary)
    val today = remember(records) { records.count { isToday(it.createdAt) } }
    val streak = remember(records) { calculateStreak(records) }
    ScreenShell(wallpaperUri = null) {
        MiniAppTopBar(bundle, screen.title, primary, onBack)
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "hero") {
                MiniAppHeroCard(bundle, records.size, today, streak, primary, secondary)
            }
            if (bundle.screens.size > 1) {
                item(key = "screens") {
                    MiniAppScreenTabs(
                        screens = bundle.screens,
                        selectedScreenId = screen.id,
                        primary = primary,
                        onSelect = { selectedScreenId = it }
                    )
                }
            }
            itemsIndexed(
                screen.components,
                key = { index, component -> "$index:${component.type}:${component.title}" }
            ) { _, component ->
                MiniAppComponentView(bundle, component, records, primary, secondary, onRunAction, onCreateRecord, onDeleteRecord)
            }
        }
    }
}

@Composable
private fun MiniAppMissingState(onBack: () -> Unit, message: String) {
    IconButton(onClick = onBack, modifier = Modifier.glassCard(shape = CircleShape)) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
    }
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniAppScreenTabs(
    screens: List<com.aura.app.miniapps.MiniAppScreen>,
    selectedScreenId: String,
    primary: Color,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), RoundedCornerShape(20.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        screens.forEach { screen ->
            val selected = screen.id == selectedScreenId
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(screen.id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    screen.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                    color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniAppTopBar(
    bundle: MiniAppBundle,
    screenTitle: String,
    primary: Color,
    onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.glassCard(shape = CircleShape)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
        }
        MiniAppAvatar(bundle.icon.value, bundle.icon.background, primary, 48.dp, 14.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                bundle.metadata.name,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                screenTitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniAppHeroCard(
    bundle: MiniAppBundle,
    totalRecords: Int,
    today: Int,
    streak: Int,
    primary: Color,
    secondary: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(primary.copy(alpha = 0.32f), secondary.copy(alpha = 0.18f), MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                )
            )
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.11f)), RoundedCornerShape(30.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bundle.metadata.description.ifBlank { "A local app powered by Aura." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    bundle.metadata.category,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.38f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            MiniAppProgressOrb(today, primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MiniAppHeroStat("Today", today.toString(), Modifier.weight(1f))
            MiniAppHeroStat("Streak", streak.toString(), Modifier.weight(1f))
            MiniAppHeroStat("Total", totalRecords.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniAppProgressOrb(value: Int, primary: Color) {
    val density = LocalDensity.current
    val stroke = remember(density) {
        Stroke(
            width = with(density) { 8.dp.toPx() }
        )
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            drawArc(
                color = primary.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = primary,
                startAngle = -90f,
                sweepAngle = (value.coerceIn(0, 7) / 7f) * 360f,
                useCenter = false,
                style = stroke
            )
        }
        Text(value.toString(), color = primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun MiniAppHeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.36f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniAppComponentView(
    bundle: MiniAppBundle,
    component: MiniAppComponent,
    records: List<MiniAppRecord>,
    primary: Color,
    secondary: Color,
    onRunAction: (String, String) -> Unit,
    onCreateRecord: (String, String, Map<String, String>) -> Unit,
    onDeleteRecord: (String, String) -> Unit
) {
    when (component.type) {
        "form" -> MiniAppFormCard(bundle, component, primary, onCreateRecord)
        "quick_action_grid" -> MiniAppActionPanel(bundle, component, primary, secondary, onRunAction)
        "timeline" -> MiniAppTimelineCard(bundle, component, records, primary, onDeleteRecord)
        "chart" -> MiniAppChartCard(component, records, primary, secondary)
        "list" -> MiniAppListCard(bundle, component, records, primary, onRunAction)
        "button" -> MiniAppButtonCard(bundle, component, primary, onRunAction)
        "settings" -> MiniAppSettingsCard(bundle, component, primary)
        "slider" -> MiniAppSliderCard(component, records, primary, secondary)
        "bottom_sheet" -> MiniAppInfoPanel(component, primary)
        "streak_view", "progress_ring", "dashboard_block" -> MiniAppMetricCard(component, records, primary)
        else -> MiniAppMetricCard(component, records, primary)
    }
}

@Composable
private fun MiniAppActionPanel(
    bundle: MiniAppBundle,
    component: MiniAppComponent,
    primary: Color,
    secondary: Color,
    onRunAction: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniAppSectionTitle(component.title.ifBlank { "Actions" }, "Tap to update")
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(
                component.items,
                key = { index, item -> "$index:${item.label}:${item.actionId.orEmpty()}" }
            ) { index, item ->
                val color = if (index % 2 == 0) primary else secondary
                Column(
                    modifier = Modifier
                        .height(92.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(color.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, color.copy(alpha = 0.18f)), RoundedCornerShape(22.dp))
                        .clickable { item.actionId?.let { onRunAction(bundle.id, it) } }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(17.dp))
                    }
                    Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun MiniAppFormCard(
    bundle: MiniAppBundle,
    component: MiniAppComponent,
    primary: Color,
    onCreateRecord: (String, String, Map<String, String>) -> Unit
) {
    val schema = bundle.dataSchema
    val fields = schema.fields
    var values by remember(bundle.id, component.title) {
        mutableStateOf(fields.associate { it.name to (it.defaultValue ?: "") })
    }
    var error by remember(bundle.id, component.title) { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniAppSectionTitle(component.title.ifBlank { "New entry" }, schema.recordType)
        if (fields.isEmpty()) {
            MiniAppInfoPanel(MiniAppComponent("bottom_sheet", "No fields configured"), primary)
        } else {
            fields.forEach { field ->
                MiniAppFieldInput(
                    field = field,
                    value = values[field.name].orEmpty(),
                    primary = primary,
                    onChange = { updated -> values = values + (field.name to updated) }
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = {
                    val missing = fields.firstOrNull { it.required && values[it.name].isNullOrBlank() }
                    if (missing != null) {
                        error = "${formatMiniAppFieldLabel(missing.name)} is required"
                    } else {
                        val cleaned = fields.associate { field ->
                            field.name to values[field.name].orEmpty().ifBlank { field.defaultValue.orEmpty() }
                        }
                        onCreateRecord(bundle.id, schema.recordType, cleaned)
                        values = fields.associate { it.name to (it.defaultValue ?: "") }
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White)
            ) {
                Text(component.items.firstOrNull()?.label ?: "Save entry", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MiniAppFieldInput(
    field: MiniAppField,
    value: String,
    primary: Color,
    onChange: (String) -> Unit
) {
    val label = formatMiniAppFieldLabel(field.name) + if (field.required) " *" else ""
    if (field.type == "boolean") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.34f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold)
                Text(if (value.equals("true", ignoreCase = true)) "Enabled" else "Disabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = value.equals("true", ignoreCase = true),
                onCheckedChange = { onChange(it.toString()) }
            )
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text(field.defaultValue ?: field.type) },
            singleLine = field.type != "text",
            minLines = if (field.type == "text") 2 else 1,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.24f),
                unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.16f)
            )
        )
    }
}

private fun formatMiniAppFieldLabel(value: String): String =
    value.replace('_', ' ').split(" ").filter { it.isNotBlank() }.joinToString(" ") {
        it.replaceFirstChar { char -> char.uppercase() }
    }.ifBlank { "Field" }

@Composable
private fun MiniAppTimelineCard(
    bundle: MiniAppBundle,
    component: MiniAppComponent,
    records: List<MiniAppRecord>,
    primary: Color,
    onDeleteRecord: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniAppSectionTitle(component.title.ifBlank { "History" }, "${records.size} records")
        if (records.isEmpty()) {
            MiniAppEmptyTimeline(primary)
        } else {
            records.take(8).forEach { record ->
                MiniAppTimelineRow(bundle, record, primary, onDeleteRecord)
            }
        }
    }
}

@Composable
private fun MiniAppTimelineRow(
    bundle: MiniAppBundle,
    record: MiniAppRecord,
    primary: Color,
    onDeleteRecord: (String, String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(primary))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                record.values.values.joinToString(" / ").ifBlank { record.recordType },
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(formatMiniAppTime(record.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onDeleteRecord(bundle.id, record.id) }) {
            Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MiniAppEmptyTimeline(primary: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(primary.copy(alpha = 0.08f))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = primary)
        Text("Nothing logged yet", fontWeight = FontWeight.Black)
        Text("Use an action above to create the first record", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MiniAppChartCard(component: MiniAppComponent, records: List<MiniAppRecord>, primary: Color, secondary: Color) {
    val buckets = remember(records) { weeklyBuckets(records) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MiniAppSectionTitle(component.title.ifBlank { "Trend" }, "Last 7 days")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val peak = buckets.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            buckets.forEachIndexed { index, bucket ->
                val heightFraction = (bucket.second.toFloat() / peak.toFloat()).coerceIn(0.08f, 1f)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((76 * heightFraction).dp)
                            .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(if (index == buckets.lastIndex) primary else secondary.copy(alpha = 0.38f))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(bucket.first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MiniAppListCard(
    bundle: MiniAppBundle,
    component: MiniAppComponent,
    records: List<MiniAppRecord>,
    primary: Color,
    onRunAction: (String, String) -> Unit
) {
    val rows = component.items.ifEmpty {
        records.take(5).map { record ->
            MiniAppComponentItem(
                label = record.values.values.joinToString(" / ").ifBlank { record.recordType },
                value = formatMiniAppTime(record.createdAt)
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniAppSectionTitle(component.title.ifBlank { "Details" }, "${rows.size} items")
        if (rows.isEmpty()) {
            MiniAppInfoPanel(
                MiniAppComponent("bottom_sheet", "No details yet"),
                primary
            )
        } else {
            rows.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.34f))
                        .clickable(enabled = item.actionId != null) {
                            item.actionId?.let { onRunAction(bundle.id, it) }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.label.take(1).uppercase(), color = primary, fontWeight = FontWeight.Black)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.value?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (item.actionId != null) {
                        Icon(Icons.Rounded.ChevronRight, "Run ${item.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniAppButtonCard(
    bundle: MiniAppBundle,
    component: MiniAppComponent,
    primary: Color,
    onRunAction: (String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(primary.copy(alpha = 0.13f))
            .border(BorderStroke(1.dp, primary.copy(alpha = 0.18f)), RoundedCornerShape(24.dp))
            .clickable(enabled = component.actionId != null) {
                component.actionId?.let { onRunAction(bundle.id, it) }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Check, null, tint = primary)
        }
        Text(
            component.title.ifBlank { "Run action" },
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(Icons.Rounded.ChevronRight, "Run action", tint = primary)
    }
}

@Composable
private fun MiniAppSettingsCard(bundle: MiniAppBundle, component: MiniAppComponent, primary: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniAppSectionTitle(component.title.ifBlank { "Settings" }, "Local app")
        MiniAppSettingsRow("Storage", "Local records on this phone", primary)
        MiniAppSettingsRow("Assistant", if ("assistant_actions" in bundle.capabilities) "Voice actions enabled" else "Voice actions off", primary)
        MiniAppSettingsRow("Schema", "${bundle.dataSchema.fields.size} fields / ${bundle.actions.size} actions", primary)
    }
}

@Composable
private fun MiniAppSettingsRow(label: String, value: String, primary: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(primary.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Settings, null, tint = primary, modifier = Modifier.size(17.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Black)
            Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniAppSliderCard(component: MiniAppComponent, records: List<MiniAppRecord>, primary: Color, secondary: Color) {
    val progress = when (component.metric) {
        "today_count" -> records.count { isToday(it.createdAt) }.coerceIn(0, 5) / 5f
        "weekly_count" -> weeklyBuckets(records).sumOf { it.second }.coerceIn(0, 14) / 14f
        else -> records.size.coerceIn(0, 10) / 10f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniAppSectionTitle(component.title.ifBlank { "Progress" }, "${(progress * 100).toInt()}%")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(secondary.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(primary)
            )
        }
    }
}

@Composable
private fun MiniAppInfoPanel(component: MiniAppComponent, primary: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(primary.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, primary.copy(alpha = 0.14f)), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(component.title.ifBlank { "Note" }, fontWeight = FontWeight.Black)
        val body = component.items.firstOrNull()?.value
            ?: component.items.firstOrNull()?.label
            ?: "This mini app keeps its state local and ready for assistant actions."
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniAppSectionTitle(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniAppMetricCard(component: MiniAppComponent, records: List<MiniAppRecord>, primary: Color) {
    val today = remember(records) { records.count { isToday(it.createdAt) } }
    val streak = remember(records) { calculateStreak(records) }
    val value = when (component.metric) {
        "today_count" -> today
        "streak" -> streak
        "weekly_count" -> weeklyBuckets(records).sumOf { it.second }
        else -> records.size
    }
    val label = when (component.metric) {
        "today_count" -> "Logged today"
        "streak" -> "Consecutive days"
        "weekly_count" -> "This week"
        else -> "Total records"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(value.toString(), color = primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(component.title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun parseMiniAppColor(value: String, fallback: Color): Color =
    try {
        Color(android.graphics.Color.parseColor(value))
    } catch (_: Exception) {
        fallback
    }

private fun formatMiniAppTime(value: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(value))

private fun isToday(value: Long): Boolean {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    return fmt.format(Date(value)) == fmt.format(Date())
}

private fun weeklyBuckets(records: List<MiniAppRecord>): List<Pair<String, Int>> {
    val keyFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    val labelFormat = SimpleDateFormat("E", Locale.getDefault())
    val calendar = java.util.Calendar.getInstance()
    calendar.add(java.util.Calendar.DATE, -6)
    return (0..6).map {
        val date = calendar.time
        val key = keyFormat.format(date)
        val label = labelFormat.format(date).take(1)
        calendar.add(java.util.Calendar.DATE, 1)
        label to records.count { record -> keyFormat.format(Date(record.createdAt)) == key }
    }
}

private fun calculateStreak(records: List<MiniAppRecord>): Int {
    if (records.isEmpty()) return 0
    val days = records.map { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(it.createdAt)) }.toSet()
    var streak = 0
    val calendar = java.util.Calendar.getInstance()
    while (days.contains(SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time))) {
        streak += 1
        calendar.add(java.util.Calendar.DATE, -1)
    }
    return streak
}

@Composable
private fun AssistantScreen(
    state: LauncherUiState,
    onAssistantInput: (String) -> Unit,
    onSend: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        Header("ASSISTANT", "Your local AI conversation.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            reverseLayout = false
        ) {
            itemsIndexed(
                state.messages,
                key = { index, message -> "$index:${message.role}:${message.text.hashCode()}" }
            ) { _, message ->
                val isUser = message.role == MessageRole.User
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isUser) 0.82f else 0.88f)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomStart = if (isUser) 20.dp else 6.dp,
                                    bottomEnd = if (isUser) 6.dp else 20.dp
                                )
                            )
                            .background(
                                if (isUser) {
                                    Brush.linearGradient(
                                        colors = if (isDark) listOf(Color(0xFF7C3AED), Color(0xFF6366F1))
                                        else listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = if (isDark) listOf(Color(0xFF1E1E22), Color(0xFF18181B))
                                        else listOf(Color(0xFFFFFFFF), Color(0xFFF9F9FB))
                                    )
                                }
                            )
                            .then(
                                if (!isUser) {
                                    Modifier.border(
                                        0.6.dp,
                                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                        RoundedCornerShape(
                                            topStart = 20.dp,
                                            topEnd = 20.dp,
                                            bottomStart = 6.dp,
                                            bottomEnd = 20.dp
                                        )
                                    )
                                } else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            // Typing indicator
            if (state.loading) {
                item {
                    TypingIndicator(isDark = isDark)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AssistantComposer(state.assistantInput, onAssistantInput, onSend)
    }
}

@Composable
private fun TypingIndicator(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0; 1f at 200; 0f at 400; 0f at 1200
            }, repeatMode = RepeatMode.Restart
        ), label = "dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0; 0f at 200; 1f at 400; 0f at 600; 0f at 1200
            }, repeatMode = RepeatMode.Restart
        ), label = "dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0; 0f at 400; 1f at 600; 0f at 800; 0f at 1200
            }, repeatMode = RepeatMode.Restart
        ), label = "dot3"
    )
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .background(if (isDark) Color(0xFF1E1E22) else Color(0xFFFFFFFF))
            .border(
                0.6.dp,
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(dot1, dot2, dot3).forEach { dotAnim ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(0.6f + dotAnim * 0.4f)
                    .clip(CircleShape)
                    .background(
                        if (isDark) Color(0xFF8B5CF6).copy(alpha = 0.4f + dotAnim * 0.6f)
                        else Color(0xFF6366F1).copy(alpha = 0.3f + dotAnim * 0.7f)
                    )
            )
        }
    }
}

@Composable
private fun TasksScreen(state: LauncherUiState, onAddTodo: (String) -> Unit, onBack: () -> Unit) {
    var title by remember { mutableStateOf("") }
    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)) {
                Text("← BACK", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Header("TASKS", "${state.openTodos} open items on this device.")
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ADD A TASK...") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.onBackground
            ),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onAddTodo(title)
                title = ""
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ADD TASK", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        val isDark = isSystemInDarkTheme()
        val separatorColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
        LazyColumn {
            itemsIndexed(
                state.todos,
                key = { index, todo -> "$index:${todo.id}" }
            ) { _, todo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = separatorColor,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = todo.title.uppercase(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryScreen(state: LauncherUiState, onAddMemory: (String, String) -> Unit, onBack: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)) {
                Text("← BACK", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Header("MEMORY", "${state.memories.size} local memories stored.")
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("TITLE...") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.onBackground
            ),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("WHAT SHOULD AURA REMEMBER?") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.onBackground
            ),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onAddMemory(title, content)
                title = ""
                content = ""
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("SAVE MEMORY", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        val isDark = isSystemInDarkTheme()
        val separatorColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
        LazyColumn {
            itemsIndexed(
                state.memories,
                key = { index, memory -> "$index:${memory.id}" }
            ) { _, memory ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = separatorColor,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = memory.title.uppercase(),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = memory.content,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AuraAvatarFace(
    modifier: Modifier = Modifier,
    sizeMultiplier: Float = 1f
) {
    val isDark = isSystemInDarkTheme()
    val capsuleWidth = (36 * sizeMultiplier).dp
    val capsuleHeight = (96 * sizeMultiplier).dp
    val capsuleRadius = (18 * sizeMultiplier).dp
    val slitWidth = (8 * sizeMultiplier).dp
    val slitHeight = (48 * sizeMultiplier).dp
    val slitRadius = (4 * sizeMultiplier).dp
    val eyeGap = (40 * sizeMultiplier).dp
    val smileGap = (16 * sizeMultiplier).dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Eyes Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(eyeGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Eye
            Box(
                modifier = Modifier
                    .size(width = capsuleWidth, height = capsuleHeight)
                    .clip(RoundedCornerShape(capsuleRadius))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E))
                            } else {
                                listOf(Color(0xFFE5E5EA), Color(0xFFD1D1D6))
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(capsuleRadius)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Glowing slit
                Box(
                    modifier = Modifier
                        .size(width = slitWidth, height = slitHeight)
                        .clip(RoundedCornerShape(slitRadius))
                        .background(
                            color = if (isDark) Color.White else Color(0xFF0F0F10)
                        )
                )
            }

            // Right Eye
            Box(
                modifier = Modifier
                    .size(width = capsuleWidth, height = capsuleHeight)
                    .clip(RoundedCornerShape(capsuleRadius))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E))
                            } else {
                                listOf(Color(0xFFE5E5EA), Color(0xFFD1D1D6))
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(capsuleRadius)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Glowing slit
                Box(
                    modifier = Modifier
                        .size(width = slitWidth, height = slitHeight)
                        .clip(RoundedCornerShape(slitRadius))
                        .background(
                            color = if (isDark) Color.White else Color(0xFF0F0F10)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(smileGap))

        // Curved Smile
        val strokeColor = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f)
        Spacer(
            modifier = Modifier
                .size(width = (48 * sizeMultiplier).dp, height = (12 * sizeMultiplier).dp)
                .drawWithCache {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        quadraticTo(
                            x1 = size.width / 2f,
                            y1 = size.height,
                            x2 = size.width,
                            y2 = 0f
                        )
                    }
                    val stroke = Stroke(
                        width = (3 * sizeMultiplier).dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    onDrawBehind {
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = stroke
                        )
                    }
                }
        )
    }
}

@Composable
private fun OnboardingHeader(
    step: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        // Pill indicator label "X of 3"
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$step of 3",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Progress Capsule Bars
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..3) {
                val active = step >= i
                val barColor = if (active) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                }
                Box(
                    modifier = Modifier
                        .size(width = 24.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    state: LauncherUiState,
    onRequestPermissions: () -> Unit,
    onCreateAccount: (email: String, password: String, name: String?, onResult: (Result<com.aura.app.assistant.UserResponse>) -> Unit) -> Unit,
    onSignIn: (email: String, password: String, onResult: (Result<com.aura.app.assistant.UserResponse>) -> Unit) -> Unit,
    onFinishOnboarding: (appMode: String, provider: LlmProvider, apiKey: String, modelId: String, bgListening: Boolean) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    
    // Step 1: App Mode state (default to launcher as shown in mockup)
    var selectedAppMode by remember { mutableStateOf("launcher") }
    var accountMode by remember { mutableStateOf("local") }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var authComplete by remember { mutableStateOf(state.session.isLoggedIn) }
    
    // Step 2: AI Engine state
    var selectedProvider by remember { mutableStateOf(LlmProvider.Gemini) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    
    // Initialize default model IDs
    var modelIdInput by remember { mutableStateOf("gemini-3-flash-preview") }
    
    // Step 3: Background Listening state
    var bgListeningEnabled by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    
    // Dynamically query permission states
    val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasLoc = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    
    BackHandler(enabled = step > 1) {
        step--
    }
    
    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sleek premium Onboarding Header
            OnboardingHeader(
                step = step,
                onBack = {
                    if (step > 1) {
                        step--
                    }
                }
            )
            
            // Step content display
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (step) {
                    1 -> {
                        Spacer(Modifier.height(16.dp))
                        AuraAvatarFace(sizeMultiplier = 1.3f)

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "Set up Aura",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Choose how Aura should run, then add an optional cloud account for synced tasks and memories.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        val modeOptions = listOf(
                            Triple("launcher", "Home launcher", "Replace the default home screen with Aura."),
                            Triple("normal", "Normal app", "Use Aura as a regular app without changing Home."),
                            Triple("overlay", "Background assistant", "Keep Aura available as an always-on assistant.")
                        )
                        modeOptions.forEach { (mode, title, subtitle) ->
                            val selected = selectedAppMode == mode
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard(shape = RoundedCornerShape(20.dp), borderWidth = if (selected) 2.dp else 1.2.dp)
                                    .clickable { selectedAppMode = mode }
                                    .background(if (selected) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Spacer(Modifier.height(4.dp))
                                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (selected) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("local" to "Local", "create" to "Create", "signIn" to "Sign in").forEach { (mode, label) ->
                                val selected = accountMode == mode
                                Button(
                                    onClick = {
                                        accountMode = mode
                                        authMessage = null
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        contentColor = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                                ) {
                                    Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }

                        if (accountMode == "local") {
                            Text(
                                text = "Local mode stores settings, tasks, and memories on this device. You can add an account later.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        } else {
                            if (accountMode == "create") {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = { Icon(Icons.Rounded.RemoveRedEye, contentDescription = null) },
                                    placeholder = { Text("Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Rounded.Mail, contentDescription = null) },
                                placeholder = { Text("Email") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                placeholder = { Text("Password") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    val done: (Result<com.aura.app.assistant.UserResponse>) -> Unit = { result ->
                                        authComplete = result.isSuccess
                                        authMessage = result.fold(
                                            onSuccess = { "Signed in as ${it.email}" },
                                            onFailure = { it.message ?: "Account setup failed" }
                                        )
                                    }
                                    if (accountMode == "create") {
                                        onCreateAccount(emailInput.trim(), passwordInput, nameInput.trim(), done)
                                    } else {
                                        onSignIn(emailInput.trim(), passwordInput, done)
                                    }
                                },
                                enabled = !state.loading && emailInput.isNotBlank() && passwordInput.length >= 6,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(26.dp)
                            ) {
                                Text(if (state.loading) "Working..." else if (accountMode == "create") "Create account" else "Sign in")
                            }
                        }

                        authMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (authComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                    
                    2 -> {
                        Spacer(Modifier.height(12.dp))
                        
                        // Small Assistant Brand Icon
                        AuraAvatarFace(sizeMultiplier = 0.6f)
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text(
                            text = "Connect AI",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Add an API key now, or skip and configure it later in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // provider A: Gemini (Recommended)
                        val isGemini = selectedProvider == LlmProvider.Gemini
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(20.dp), borderWidth = if (isGemini) 2.dp else 1.2.dp)
                                .clickable {
                                    selectedProvider = LlmProvider.Gemini
                                    modelIdInput = "gemini-3-flash-preview"
                                }
                                .background(if (isGemini) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f) else Color.Transparent)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Star Icon for Gemini (using GraphicEq)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = if (isGemini) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                
                                Spacer(Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Connect Gemini",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        // Recommended Capsule Badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isDark) Color(0xFF1E3A1E) else Color(0xFFE8F5E9))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "✦ Recommended",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Fast setup. Native integration. Powered by Gemini.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Text(
                                    text = "→",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // provider B: OpenAI
                        val isOpenAi = selectedProvider == LlmProvider.OpenAI
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(20.dp), borderWidth = if (isOpenAi) 2.dp else 1.2.dp)
                                .clickable {
                                    selectedProvider = LlmProvider.OpenAI
                                    modelIdInput = "gpt-4o-mini"
                                }
                                .background(if (isOpenAi) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f) else Color.Transparent)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Spiral Icon for OpenAI (using Layers icon as representation)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Layers,
                                        contentDescription = null,
                                        tint = if (isOpenAi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                
                                Spacer(Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Connect OpenAI",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Popular setup. Reliable performance. Powered by OpenAI.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Text(
                                    text = "→",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // or separator with horizontal line dividers
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)))
                            Text(
                                text = "or",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)))
                        }
                        
                        // Credentials form
                        Text(
                            text = "${selectedProvider.label} API key (optional)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth().align(Alignment.Start)
                        )
                        
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle key visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            placeholder = { Text(if (selectedProvider == LlmProvider.Gemini) "AIzaSy..." else "sk-........................") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.onBackground
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        Spacer(Modifier.height(4.dp))
                        
                        // Stored Securely Device Footnote
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                                .border(
                                    width = 1.dp,
                                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Stored on this device.",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Leave this blank to finish setup now and add a key later.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    3 -> {
                        Spacer(Modifier.height(12.dp))
                        
                        // Small Assistant Brand Icon
                        AuraAvatarFace(sizeMultiplier = 0.6f)
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text(
                            text = "Activate capabilities",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Grant hardware permissions to enable AI voice.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Permission Card: Microphone
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(20.dp))
                                .clickable { if (!hasMic) onRequestPermissions() }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = null,
                                            tint = if (hasMic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text("Microphone Capture", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Spacer(Modifier.height(2.dp))
                                        Text("Required for speech-to-text processing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (hasMic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hasMic) {
                                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                        
                        // Permission Card: Notifications
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(20.dp))
                                .clickable { if (!hasNotif) onRequestPermissions() }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = if (hasNotif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text("System Alerts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Spacer(Modifier.height(2.dp))
                                        Text("Displays active voice states and overlay triggers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (hasNotif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hasNotif) {
                                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                        
                        // Permission Card: Location
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(20.dp))
                                .clickable { if (!hasLoc) onRequestPermissions() }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Home,
                                            contentDescription = null,
                                            tint = if (hasLoc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text("Surroundings Access", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Spacer(Modifier.height(2.dp))
                                        Text("Allows assistant context based on surroundings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (hasLoc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hasLoc) {
                                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Background Listening Toggle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Background voice capability", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.height(2.dp))
                                    Text("Opt-in background voice activation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = bgListeningEnabled,
                                    onCheckedChange = { bgListeningEnabled = it }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            val isNextEnabled = when (step) {
                1 -> accountMode == "local" || authComplete
                2 -> true
                3 -> true
                else -> true
            }

            Button(
                onClick = {
                    if (step < 3) {
                        step++
                    } else {
                        onFinishOnboarding(
                            selectedAppMode,
                            selectedProvider,
                            apiKeyInput,
                            modelIdInput,
                            bgListeningEnabled
                        )
                    }
                },
                enabled = isNextEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (step < 3) "Continue" else "Initialize system",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}


@Composable
private fun ModelsScreen(
    state: LauncherUiState,
    onProviderSelected: (LlmProvider) -> Unit,
    onGoogleApiKeyChanged: (String) -> Unit,
    onGoogleModelChanged: (String) -> Unit,
    onOpenAiApiKeyChanged: (String) -> Unit,
    onOpenAiModelChanged: (String) -> Unit,
    onOpenRouterApiKeyChanged: (String) -> Unit,
    onOpenRouterModelChanged: (String) -> Unit,
    onLoadOpenRouterModels: () -> Unit,
    onBack: () -> Unit
) {
    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)) {
                Text("← BACK", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Header("MODELS", "Configure LLM providers, API keys, and model parameters.")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("LLM PROVIDER", fontWeight = FontWeight.Bold)
                    Text(
                        "Chat is driven by the active provider below. Keys stay on this device and are sent only when you make a request.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LlmProvider.entries.forEach { provider ->
                            val selected = state.llmSettings.provider == provider
                            FilledTonalButton(
                                onClick = { onProviderSelected(provider) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface,
                                    contentColor = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(provider.label.uppercase(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("GOOGLE GEMINI", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = state.llmSettings.googleApiKey,
                        onValueChange = onGoogleApiKeyChanged,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onBackground) },
                        placeholder = { Text("GOOGLE AI STUDIO API KEY...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    OutlinedTextField(
                        value = state.llmSettings.googleModel,
                        onValueChange = onGoogleModelChanged,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onBackground) },
                        placeholder = { Text("GOOGLE GEMINI MODEL ID...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("OPENAI GPT", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = state.llmSettings.openAiApiKey,
                        onValueChange = onOpenAiApiKeyChanged,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onBackground) },
                        placeholder = { Text("OPENAI PLATFORM API KEY...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    OutlinedTextField(
                        value = state.llmSettings.openAiModel,
                        onValueChange = onOpenAiModelChanged,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onBackground) },
                        placeholder = { Text("OPENAI MODEL ID...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("OPENROUTER DIRECT", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = state.llmSettings.openRouterApiKey,
                        onValueChange = onOpenRouterApiKeyChanged,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onBackground) },
                        placeholder = { Text("OPENROUTER API KEY...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    OutlinedTextField(
                        value = state.llmSettings.openRouterModel,
                        onValueChange = onOpenRouterModelChanged,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onBackground) },
                        placeholder = { Text("OPENROUTER MODEL ID...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Button(
                        onClick = onLoadOpenRouterModels,
                        enabled = !state.loadingModels,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text(if (state.loadingModels) "LOADING" else "LOAD MODELS", fontWeight = FontWeight.Bold)
                    }
                }
                if (state.openRouterModels.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.openRouterModels.forEach { model ->
                            SettingsRow(
                                title = model.name,
                                subtitle = model.id,
                                onClick = { onOpenRouterModelChanged(model.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: LauncherUiState,
    onRequestVoicePermissions: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onBackgroundListening: (Boolean) -> Unit,
    onSelectWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onSetInteractionMode: (String) -> Unit,
    onConfigureModels: () -> Unit,
    onConfigureTasks: () -> Unit,
    onConfigureMemories: () -> Unit,
    onQuitApp: () -> Unit,
    onSetAppMode: (String) -> Unit
) {
    var showAppModeDialog by remember { mutableStateOf(false) }
    var showPermissionExplanation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        Header("SETTINGS", "System, model, and voice configuration.")
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Status card
            val isDark = isSystemInDarkTheme()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isDark) listOf(Color(0xFF1E1E22), Color(0xFF161618))
                            else listOf(Color(0xFFFFFFFF), Color(0xFFF9F9FB))
                        )
                    )
                    .border(
                        0.6.dp,
                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("LOCAL-FIRST MODE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Data stays on this device.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("AI & Data")

            SettingsRow(
                title = "Models",
                subtitle = "LLM provider, API keys, and model parameters.",
                icon = Icons.Rounded.Key,
                onClick = onConfigureModels
            )
            SettingsRow(
                title = "Tasks",
                subtitle = "Manage your todo items.",
                icon = Icons.Rounded.CheckCircle,
                onClick = onConfigureTasks
            )
            SettingsRow(
                title = "Memories",
                subtitle = "Stored assistant memory items.",
                icon = Icons.Rounded.Layers,
                onClick = onConfigureMemories
            )

            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("System")

            val currentModeLabel = when (state.session.appMode) {
                "launcher" -> "Home Launcher"
                "normal" -> "Normal App"
                "overlay" -> "Always-On Assistant"
                else -> "Home Launcher"
            }
            SettingsRow(
                title = "App Mode",
                subtitle = currentModeLabel,
                icon = Icons.Rounded.Apps,
                onClick = { showAppModeDialog = true }
            )

            if (state.session.appMode == "launcher") {
                SettingsRow(
                    title = "Default launcher",
                    subtitle = "Open Android Home settings.",
                    icon = Icons.Rounded.Home,
                    onClick = onOpenHomeSettings
                )
            }

            SettingsRow(
                title = "Permissions",
                subtitle = "Microphone, notification, and location.",
                icon = Icons.Rounded.Mic,
                onClick = onRequestVoicePermissions
            )
            SettingsRow(
                title = "Interaction mode",
                subtitle = state.session.interactionMode.replaceFirstChar { it.uppercase() },
                icon = Icons.Rounded.RemoveRedEye,
                onClick = {
                    val nextMode = if (state.session.interactionMode == "dot") "eyes" else "dot"
                    onSetInteractionMode(nextMode)
                }
            )

            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("Appearance")

            if (state.session.appMode == "launcher") {
                SettingsRow(
                    title = "Wallpaper",
                    subtitle = if (state.session.wallpaperUri != null) "Custom wallpaper active" else "None set",
                    icon = Icons.Rounded.Image,
                    onClick = onSelectWallpaper
                )
                if (state.session.wallpaperUri != null) {
                    SettingsRow(
                        title = "Clear wallpaper",
                        subtitle = "Reset to default background.",
                        icon = Icons.Rounded.Delete,
                        onClick = onClearWallpaper
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("Voice")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = if (isDark) listOf(Color(0xFF22C55E).copy(alpha = 0.15f), Color(0xFF16A34A).copy(alpha = 0.08f))
                                    else listOf(Color(0xFF22C55E).copy(alpha = 0.12f), Color(0xFF16A34A).copy(alpha = 0.05f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Background listening", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Always-on voice service", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.session.backgroundListeningEnabled,
                        onCheckedChange = onBackgroundListening
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            SettingsRow(
                title = "Quit Aura",
                subtitle = "Exit and move to background.",
                icon = Icons.Rounded.PowerSettingsNew,
                onClick = onQuitApp
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAppModeDialog) {
        AlertDialog(
            onDismissRequest = { showAppModeDialog = false },
            title = { Text("SELECT APP MODE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how Aura runs on this device:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSetAppMode("launcher")
                            showAppModeDialog = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.session.appMode == "launcher") MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Home Launcher", fontWeight = FontWeight.Bold)
                            Text("Operate as default system home screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSetAppMode("normal")
                            showAppModeDialog = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.session.appMode == "normal") MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Normal App", fontWeight = FontWeight.Bold)
                            Text("Run as a standard standalone assistant application.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val overlayGranted = android.provider.Settings.canDrawOverlays(context)
                            if (overlayGranted) {
                                onSetAppMode("overlay")
                                showAppModeDialog = false
                            } else {
                                showPermissionExplanation = true
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.session.appMode == "overlay") MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Always-On Background Assistant", fontWeight = FontWeight.Bold)
                            Text("Run always in background; auto-overlay on speech (requires display overlay permission).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppModeDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text("OVERLAY PERMISSION REQUIRED") },
            text = {
                Text("To pop up Aura instantly when speech is detected in the background, please grant the 'Display over other apps' permission in system settings.")
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionExplanation = false
                    showAppModeDialog = false
                    onSetAppMode("overlay")
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    startActivitySafely(context, intent)
                }) {
                    Text("GO TO SETTINGS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
private fun AppIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable != null) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(drawable)
            },
            modifier = modifier
        )
    }
}

@Composable
private fun rememberWallpaperPainter(uriString: String?): ImageBitmap? {
    if (uriString.isNullOrBlank()) return null
    val context = LocalContext.current
    return remember(uriString) {
        try {
            val uri = Uri.parse(uriString)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateBitmapSampleSize(options.outWidth, options.outHeight)
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)?.asImageBitmap()
            }
        } catch (_: Throwable) {
            null
        }
    }
}

private fun calculateBitmapSampleSize(width: Int, height: Int, maxDimension: Int = 2048): Int {
    if (width <= 0 || height <= 0) return 1
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth / 2 >= maxDimension || sampledHeight / 2 >= maxDimension) {
        sampleSize *= 2
        sampledWidth /= 2
        sampledHeight /= 2
    }
    return sampleSize
}

fun Modifier.glassCard(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    borderWidth: androidx.compose.ui.unit.Dp = 0.8.dp
): Modifier = this.composed {
    val isDark = isSystemInDarkTheme()
    val bgBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF1E1E22),
                Color(0xFF161618),
                Color(0xFF131315)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFFCFCFD),
                Color(0xFFF9F9FB)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }
    val borderBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = 0.08f)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.06f),
                Color.Black.copy(alpha = 0.02f),
                Color.Black.copy(alpha = 0.04f)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }
    
    this
        .clip(shape)
        .background(brush = bgBrush)
        .border(width = borderWidth, brush = borderBrush, shape = shape)
}

@Composable
private fun MeshBackground(
    isDark: Boolean,
    bgBrush: Brush,
    violetColors: List<Color>,
    cyanColors: List<Color>,
    roseColors: List<Color>,
    indigoColors: List<Color>,
    lightVioletColors: List<Color>,
    lightTealColors: List<Color>,
    lightPeachColors: List<Color>
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_mesh")
    val drift1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift1"
    )
    val drift2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift2"
    )
    val drift3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift3"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        drawRect(brush = bgBrush)
        
        if (isDark) {
            // Large violet aurora — drifts slowly
            drawCircle(
                brush = Brush.radialGradient(
                    colors = violetColors,
                    center = Offset(width * (0.65f + drift1 * 0.25f), height * (0.12f + drift2 * 0.15f)),
                    radius = width * 0.8f
                ),
                radius = width * 0.8f,
                center = Offset(width * (0.65f + drift1 * 0.25f), height * (0.12f + drift2 * 0.15f))
            )
            
            // Electric cyan aurora — counter-drifts
            drawCircle(
                brush = Brush.radialGradient(
                    colors = cyanColors,
                    center = Offset(width * (0.1f + drift2 * 0.2f), height * (0.7f + drift3 * 0.18f)),
                    radius = width * 0.75f
                ),
                radius = width * 0.75f,
                center = Offset(width * (0.1f + drift2 * 0.2f), height * (0.7f + drift3 * 0.18f))
            )
            
            // Warm rose accent — subtle center drift
            drawCircle(
                brush = Brush.radialGradient(
                    colors = roseColors,
                    center = Offset(width * (0.8f + drift3 * 0.15f), height * (0.55f + drift1 * 0.2f)),
                    radius = width * 0.5f
                ),
                radius = width * 0.5f,
                center = Offset(width * (0.8f + drift3 * 0.15f), height * (0.55f + drift1 * 0.2f))
            )
            
            // Deep indigo whisper at bottom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = indigoColors,
                    center = Offset(width * (0.4f + drift1 * 0.15f), height * 0.92f),
                    radius = width * 0.55f
                ),
                radius = width * 0.55f,
                center = Offset(width * (0.4f + drift1 * 0.15f), height * 0.92f)
            )
        } else {
            // Soft violet-indigo bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = lightVioletColors,
                    center = Offset(width * (0.7f + drift1 * 0.2f), height * (0.1f + drift2 * 0.12f)),
                    radius = width * 0.7f
                ),
                radius = width * 0.7f,
                center = Offset(width * (0.7f + drift1 * 0.2f), height * (0.1f + drift2 * 0.12f))
            )
            
            // Soft teal bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = lightTealColors,
                    center = Offset(width * (0.15f + drift2 * 0.2f), height * (0.72f + drift3 * 0.15f)),
                    radius = width * 0.6f
                ),
                radius = width * 0.6f,
                center = Offset(width * (0.15f + drift2 * 0.2f), height * (0.72f + drift3 * 0.15f))
            )
            
            // Warm peach accent
            drawCircle(
                brush = Brush.radialGradient(
                    colors = lightPeachColors,
                    center = Offset(width * (0.85f + drift3 * 0.1f), height * (0.6f + drift1 * 0.15f)),
                    radius = width * 0.45f
                ),
                radius = width * 0.45f,
                center = Offset(width * (0.85f + drift3 * 0.1f), height * (0.6f + drift1 * 0.15f))
            )
        }
    }
}

@Composable
private fun ScreenShell(
    modifier: Modifier = Modifier,
    wallpaperUri: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val wallpaperBitmap = rememberWallpaperPainter(wallpaperUri)
    val isDark = isSystemInDarkTheme()

    val bgBrush = remember(isDark) {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(Color(0xFF08080A), Color(0xFF0E0E11), Color(0xFF0A0A0C))
            } else {
                listOf(Color(0xFFF5F5F8), Color(0xFFEEEEF2), Color(0xFFE8E8ED))
            }
        )
    }

    val violetColors = remember { listOf(Color(0x558B5CF6), Color(0x2A7C3AED), Color.Transparent) }
    val cyanColors = remember { listOf(Color(0x4406B6D4), Color(0x2200D4FF), Color.Transparent) }
    val roseColors = remember { listOf(Color(0x33EC4899), Color(0x18F43F5E), Color.Transparent) }
    val indigoColors = remember { listOf(Color(0x286366F1), Color.Transparent) }
    val lightVioletColors = remember { listOf(Color(0x2A8B5CF6), Color(0x156366F1), Color.Transparent) }
    val lightTealColors = remember { listOf(Color(0x2806B6D4), Color(0x1414B8A6), Color.Transparent) }
    val lightPeachColors = remember { listOf(Color(0x1EFBBF24), Color(0x10F97316), Color.Transparent) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.65f))
            )
        } else {
            MeshBackground(
                isDark = isDark,
                bgBrush = bgBrush,
                violetColors = violetColors,
                cyanColors = cyanColors,
                roseColors = roseColors,
                indigoColors = indigoColors,
                lightVioletColors = lightVioletColors,
                lightTealColors = lightTealColors,
                lightPeachColors = lightPeachColors
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            content = content
        )
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(14.dp))
        val isDark = isSystemInDarkTheme()
        Box(
            modifier = Modifier
                .fillMaxWidth(0.12f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = if (isDark) listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4))
                        else listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                    )
                )
        )
    }
}

private fun startActivitySafely(context: Context, intent: Intent): Boolean =
    runCatching {
        context.startActivity(intent)
    }.isSuccess

@Composable
private fun AssistantComposer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val hasText = value.isNotBlank()
    val sendScale by animateFloatAsState(
        targetValue = if (hasText) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "send_scale"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(28.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "Ask Aura anything...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = if (isDark) Color(0xFF8B5CF6) else Color(0xFF6366F1)
            ),
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .scale(sendScale)
                .clip(CircleShape)
                .background(
                    if (hasText) {
                        Brush.linearGradient(
                            colors = if (isDark) listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                            else listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                            )
                        )
                    }
                )
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Send",
                tint = if (hasText) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .glassCard(shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(label.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun AppInitial(app: AppInfo, modifier: Modifier = Modifier, onLaunchApp: (AppInfo) -> Unit) {
    Column(
        modifier = modifier.clickable { onLaunchApp(app) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .glassCard(shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(app.label.take(1).uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(6.dp))
        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AppRow(app: AppInfo, onLaunchApp: (AppInfo) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(16.dp))
            .clickable { onLaunchApp(app) }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.take(1).uppercase(), color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.SemiBold)
                Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "settings_row_press"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .glassCard(shape = RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (isDark) listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                ) else listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun routeIcon(route: Route) = when (route) {
    Route.Home -> Icons.Rounded.Home
    Route.Apps -> Icons.Rounded.Apps
    Route.Assistant -> Icons.Rounded.GraphicEq
    Route.Tasks -> Icons.Rounded.CheckCircle
    Route.Memory -> Icons.Rounded.Layers
    Route.Settings -> Icons.Rounded.Settings
    Route.Models -> Icons.Rounded.Settings
    Route.MiniApp -> Icons.Rounded.Store
}
