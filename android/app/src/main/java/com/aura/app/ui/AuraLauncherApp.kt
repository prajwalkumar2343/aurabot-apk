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
                                shape = RoundedCornerShape(8.dp),
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
                            shape = RoundedCornerShape(8.dp),
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
                        colors = listOf(primaryColor, secondaryColor),
                        start = Offset(originX + travelX, eyeY + travelY),
                        end = Offset(originX + travelX + eyeWidth, eyeY + travelY + eyeHeight)
                    )

                    rotate(degrees = tilt, pivot = Offset(pivotX, pivotY)) {
                        drawRoundRect(
                            brush = eyeBrush,
                            topLeft = Offset(originX + travelX, eyeY + travelY),
                            size = Size(eyeWidth, eyeHeight),
                            cornerRadius = CornerRadius(16f, 16f)
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
                        
                        val dotBrush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor,
                                secondaryColor.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
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
                .clip(RoundedCornerShape(8.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Store, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "AURA STORE",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(118.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(installed, key = { it.id }) { miniApp ->
                MiniAppIconCard(miniApp, onOpen)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            builtIns.filterNot { builtIn -> installed.any { it.id == builtIn.id } }.take(2).forEach { bundle ->
                FilledTonalButton(
                    onClick = { onInstall(bundle) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(bundle.metadata.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPrompt,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                placeholder = { Text("CREATE WITH AURA...") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            IconButton(
                onClick = onCreate,
                modifier = Modifier
                    .size(52.dp)
                    .glassCard(shape = CircleShape)
            ) {
                Icon(Icons.Rounded.Add, "Create Mini App")
            }
        }
    }
}

@Composable
private fun MiniAppIconCard(miniApp: MiniAppInstall, onOpen: (MiniAppInstall) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(miniApp) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(parseMiniAppColor(miniApp.icon.background, MaterialTheme.colorScheme.primary)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                miniApp.icon.value.take(2).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            miniApp.name.uppercase(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MiniAppRuntimeScreen(
    bundle: MiniAppBundle?,
    records: List<MiniAppRecord>,
    onBack: () -> Unit,
    onRunAction: (String, String) -> Unit,
    onDeleteRecord: (String, String) -> Unit
) {
    if (bundle == null) {
        ScreenShell(wallpaperUri = null) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
        }
        return
    }
    val screen = bundle.screens.first()
    val primary = parseMiniAppColor(bundle.theme.primary, MaterialTheme.colorScheme.primary)
    ScreenShell(wallpaperUri = null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.glassCard(shape = CircleShape)) {
                Icon(Icons.Rounded.ArrowBack, "Back")
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(parseMiniAppColor(bundle.icon.background, primary)),
                contentAlignment = Alignment.Center
            ) {
                Text(bundle.icon.value.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(bundle.metadata.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                Text(screen.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            }
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(screen.components, key = { it.type + it.title }) { component ->
                MiniAppComponentView(bundle, component, records, primary, onRunAction, onDeleteRecord)
            }
        }
    }
}

@Composable
private fun MiniAppComponentView(
    bundle: MiniAppBundle,
    component: MiniAppComponent,
    records: List<MiniAppRecord>,
    primary: Color,
    onRunAction: (String, String) -> Unit,
    onDeleteRecord: (String, String) -> Unit
) {
    when (component.type) {
        "quick_action_grid" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(component.title.uppercase(), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(92.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), userScrollEnabled = false) {
                items(component.items, key = { it.label }) { item ->
                    FilledTonalButton(
                        onClick = { item.actionId?.let { onRunAction(bundle.id, it) } },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(72.dp)
                    ) {
                        Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        "timeline" -> Column(
            modifier = Modifier.glassCard(shape = RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(component.title.uppercase(), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
            records.take(8).ifEmpty { listOf(null) }.forEach { record ->
                if (record == null) {
                    Text("No local records yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(primary))
                        Column(Modifier.weight(1f)) {
                            Text(record.values.values.joinToString(" · ").ifBlank { record.recordType }, fontWeight = FontWeight.Bold)
                            Text(formatMiniAppTime(record.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteRecord(bundle.id, record.id) }) {
                            Icon(Icons.Rounded.Delete, "Delete", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        "streak_view", "progress_ring", "dashboard_block", "chart" -> MiniAppMetricCard(component, records, primary)
        else -> MiniAppMetricCard(component, records, primary)
    }
}

@Composable
private fun MiniAppMetricCard(component: MiniAppComponent, records: List<MiniAppRecord>, primary: Color) {
    val today = remember(records) { records.count { isToday(it.createdAt) } }
    val streak = remember(records) { calculateStreak(records) }
    val value = when (component.metric) {
        "today_count" -> "$today"
        "streak" -> "$streak"
        "weekly_count" -> records.take(7).size.toString()
        else -> records.size.toString()
    }
    Row(
        modifier = Modifier.glassCard(shape = RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(value, color = primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        }
        Column {
            Text(component.title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text("Stored locally on this phone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        Header("ASSISTANT", "Local assistant interface active.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(state.messages) { message ->
                val isUser = message.role == MessageRole.User
                Card(
                    shape = RoundedCornerShape(8.dp),
                    border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface,
                        contentColor = if (isUser) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 1f)
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal)
                    )
                }
            }
        }
        AssistantComposer(state.assistantInput, onAssistantInput, onSend)
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
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onAddTodo(title)
                title = ""
            },
            shape = RoundedCornerShape(8.dp),
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
            items(state.todos, key = { it.id }) { todo ->
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
            shape = RoundedCornerShape(8.dp)
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
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onAddMemory(title, content)
                title = ""
                content = ""
            },
            shape = RoundedCornerShape(8.dp),
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
            items(state.memories, key = { it.id }) { memory ->
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
        Canvas(
            modifier = Modifier.size(width = (48 * sizeMultiplier).dp, height = (12 * sizeMultiplier).dp)
        ) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                quadraticTo(
                    x1 = size.width / 2f,
                    y1 = size.height,
                    x2 = size.width,
                    y2 = 0f
                )
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (3 * sizeMultiplier).dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
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
                imageVector = Icons.Rounded.ArrowBack,
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
                                                .clip(RoundedCornerShape(8.dp))
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
                shape = RoundedCornerShape(8.dp),
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
                                shape = RoundedCornerShape(8.dp),
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
                shape = RoundedCornerShape(8.dp),
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
                        shape = RoundedCornerShape(8.dp)
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
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
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
                        shape = RoundedCornerShape(8.dp)
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
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
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
                        shape = RoundedCornerShape(8.dp)
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
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = onLoadOpenRouterModels,
                        enabled = !state.loadingModels,
                        shape = RoundedCornerShape(8.dp),
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("LOCAL-FIRST MODE", fontWeight = FontWeight.Bold)
                    Text(
                        "Tasks, memories, and assistant context stay on this device right now.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            SettingsRow(
                title = "Models",
                subtitle = "Configure active LLM provider, API keys, and model parameters.",
                icon = Icons.Rounded.Key,
                onClick = onConfigureModels
            )
            SettingsRow(
                title = "Tasks",
                subtitle = "Manage list of todo items.",
                icon = Icons.Rounded.CheckCircle,
                onClick = onConfigureTasks
            )
            SettingsRow(
                title = "Memories",
                subtitle = "Manage stored assistant memory items.",
                icon = Icons.Rounded.Layers,
                onClick = onConfigureMemories
            )

            val currentModeLabel = when (state.session.appMode) {
                "launcher" -> "Home Launcher"
                "normal" -> "Normal App"
                "overlay" -> "Always-On Background Assistant"
                else -> "Home Launcher"
            }
            SettingsRow(
                title = "App Mode",
                subtitle = "Active: $currentModeLabel. Tap to change.",
                icon = Icons.Rounded.Apps,
                onClick = { showAppModeDialog = true }
            )

            if (state.session.appMode == "launcher") {
                SettingsRow(
                    title = "Default launcher",
                    subtitle = "Open Android Home app settings.",
                    icon = Icons.Rounded.Home,
                    onClick = onOpenHomeSettings
                )
            }

            SettingsRow(
                title = "App permissions",
                subtitle = "Microphone, notification, and location access.",
                icon = Icons.Rounded.Mic,
                onClick = onRequestVoicePermissions
            )
            SettingsRow(
                title = "Interaction visualizer",
                subtitle = "Active: ${state.session.interactionMode.uppercase()}",
                icon = Icons.Rounded.RemoveRedEye,
                onClick = {
                    val nextMode = if (state.session.interactionMode == "dot") "eyes" else "dot"
                    onSetInteractionMode(nextMode)
                }
            )

            if (state.session.appMode == "launcher") {
                SettingsRow(
                    title = "Set custom wallpaper",
                    subtitle = if (state.session.wallpaperUri != null) "Custom wallpaper active." else "None set.",
                    icon = Icons.Rounded.Image,
                    onClick = onSelectWallpaper
                )
                if (state.session.wallpaperUri != null) {
                    SettingsRow(
                        title = "Clear custom wallpaper",
                        subtitle = "Reset to solid black/white background.",
                        icon = Icons.Rounded.Delete,
                        onClick = onClearWallpaper
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Background listening", fontWeight = FontWeight.Bold)
                        Text("Opt-in foreground service.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.session.backgroundListeningEnabled,
                        onCheckedChange = onBackgroundListening
                    )
                }
            }
            SettingsRow(
                title = "Quit system",
                subtitle = "Exit the launcher activity and move system task to back.",
                icon = Icons.Rounded.PowerSettingsNew,
                onClick = onQuitApp
            )
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
                        shape = RoundedCornerShape(8.dp),
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
                        shape = RoundedCornerShape(8.dp),
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
                        shape = RoundedCornerShape(8.dp),
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
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

fun Modifier.glassCard(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    borderWidth: androidx.compose.ui.unit.Dp = 1.2.dp
): Modifier = this.composed {
    val isDark = isSystemInDarkTheme()
    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1C1C1E), // Frosted Carbon Grey
                Color(0xFF141416)  // Pitch Carbon Grey
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFFFF), // Crisp Clean White
                Color(0xFFFAFAFC)
            )
        )
    }
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.02f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.08f),
                Color.Black.copy(alpha = 0.02f)
            )
        )
    }
    
    this
        .clip(shape)
        .background(brush = bgBrush)
        .border(width = borderWidth, brush = borderBrush, shape = shape)
}

@Composable
private fun ScreenShell(
    modifier: Modifier = Modifier,
    wallpaperUri: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val wallpaperBitmap = rememberWallpaperPainter(wallpaperUri)
    val isDark = isSystemInDarkTheme()
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                val bgBrush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color(0xFF08080A), Color(0xFF0E0E11), Color(0xFF0A0A0C))
                    } else {
                        listOf(Color(0xFFF5F5F8), Color(0xFFEEEEF2), Color(0xFFE8E8ED))
                    }
                )
                drawRect(brush = bgBrush)
                
                if (isDark) {
                    // Large violet aurora — drifts slowly
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x558B5CF6), Color(0x2A7C3AED), Color.Transparent),
                            center = Offset(width * (0.65f + drift1 * 0.25f), height * (0.12f + drift2 * 0.15f)),
                            radius = width * 0.8f
                        ),
                        radius = width * 0.8f,
                        center = Offset(width * (0.65f + drift1 * 0.25f), height * (0.12f + drift2 * 0.15f))
                    )
                    
                    // Electric cyan aurora — counter-drifts
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x4406B6D4), Color(0x2200D4FF), Color.Transparent),
                            center = Offset(width * (0.1f + drift2 * 0.2f), height * (0.7f + drift3 * 0.18f)),
                            radius = width * 0.75f
                        ),
                        radius = width * 0.75f,
                        center = Offset(width * (0.1f + drift2 * 0.2f), height * (0.7f + drift3 * 0.18f))
                    )
                    
                    // Warm rose accent — subtle center drift
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x33EC4899), Color(0x18F43F5E), Color.Transparent),
                            center = Offset(width * (0.8f + drift3 * 0.15f), height * (0.55f + drift1 * 0.2f)),
                            radius = width * 0.5f
                        ),
                        radius = width * 0.5f,
                        center = Offset(width * (0.8f + drift3 * 0.15f), height * (0.55f + drift1 * 0.2f))
                    )
                    
                    // Deep indigo whisper at bottom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x286366F1), Color.Transparent),
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
                            colors = listOf(Color(0x2A8B5CF6), Color(0x156366F1), Color.Transparent),
                            center = Offset(width * (0.7f + drift1 * 0.2f), height * (0.1f + drift2 * 0.12f)),
                            radius = width * 0.7f
                        ),
                        radius = width * 0.7f,
                        center = Offset(width * (0.7f + drift1 * 0.2f), height * (0.1f + drift2 * 0.12f))
                    )
                    
                    // Soft teal bloom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x2806B6D4), Color(0x1414B8A6), Color.Transparent),
                            center = Offset(width * (0.15f + drift2 * 0.2f), height * (0.72f + drift3 * 0.15f)),
                            radius = width * 0.6f
                        ),
                        radius = width * 0.6f,
                        center = Offset(width * (0.15f + drift2 * 0.2f), height * (0.72f + drift3 * 0.15f))
                    )
                    
                    // Warm peach accent
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x1EFBBF24), Color(0x10F97316), Color.Transparent),
                            center = Offset(width * (0.85f + drift3 * 0.1f), height * (0.6f + drift1 * 0.15f)),
                            radius = width * 0.45f
                        ),
                        radius = width * 0.45f,
                        center = Offset(width * (0.85f + drift3 * 0.1f), height * (0.6f + drift1 * 0.15f))
                    )
                }
            }
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
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(app.label.take(1).uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(6.dp))
        Text(app.label.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AppRow(app: AppInfo, onLaunchApp: (AppInfo) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLaunchApp(app) },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.take(1).uppercase(), color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(app.label, fontWeight = FontWeight.Bold)
                Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .glassCard(shape = RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
    Route.Settings -> Icons.Rounded.Settings
    Route.Models -> Icons.Rounded.Settings
    Route.MiniApp -> Icons.Rounded.Store
}
