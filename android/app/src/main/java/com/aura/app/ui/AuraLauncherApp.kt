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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    Models("Models")
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
    onQuitApp: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingComplete = state.session.onboardingComplete
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showHomePrompt by remember { mutableStateOf(false) }

    val wallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.setWallpaper(uri.toString())
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
    }

    LaunchedEffect(state.session.homeSettingsPrompted, onboardingComplete) {
        if (onboardingComplete && !state.session.homeSettingsPrompted) {
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
            onGoogleLogin = { key ->
                viewModel.setGoogleApiKey(key)
                viewModel.setLlmProvider(LlmProvider.Gemini)
                viewModel.setOnboardingComplete(true)
            },
            onOpenAiLogin = { key ->
                viewModel.setOpenAiApiKey(key)
                viewModel.setLlmProvider(LlmProvider.OpenAI)
                viewModel.setOnboardingComplete(true)
            },
            onLocalSetup = { provider, key ->
                viewModel.setLlmProvider(provider)
                if (provider == LlmProvider.Gemini) {
                    viewModel.setGoogleApiKey(key)
                } else if (provider == LlmProvider.OpenRouter) {
                    viewModel.setOpenRouterApiKey(key)
                }
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
                    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                        modifier = Modifier.drawBehind {
                            drawLine(
                                color = borderColor,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    ) {
                        val routes = listOf(Route.Home, Route.Settings)
                        routes.forEach { route ->
                            NavigationBarItem(
                                selected = current == route.name,
                                onClick = {
                                    navController.navigate(route.name) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(routeIcon(route), contentDescription = route.title) },
                                label = { Text(route.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    indicatorColor = Color.Transparent
                                )
                            )
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
                    BackHandler(enabled = state.isDefaultLauncher) {
                        // Do absolutely nothing. Prevents finishing/exiting the launcher activity and avoids screen jitter.
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
                        onSelectWallpaper = { wallpaperLauncher.launch("image/*") },
                        onOpenSettings = { navController.navigate(Route.Settings.name) },
                        onLaunchApp = { app ->
                            if (app.packageName == "com.aura.app.settings") {
                                navController.navigate(Route.Settings.name)
                            } else {
                                viewModel.launchIntent(app)?.let { context.startActivity(it) }
                            }
                        }
                    )
                }
                composable(Route.Apps.name) {
                    AppsScreen(
                        state = state,
                        onQuery = viewModel::setAppQuery,
                        onLaunchApp = { app ->
                            viewModel.launchIntent(app)?.let { context.startActivity(it) }
                        },
                        onRefresh = viewModel::refreshApps,
                        onSwipeRight = { navController.popBackStack() }
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
                        onSelectWallpaper = { wallpaperLauncher.launch("image/*") },
                        onClearWallpaper = { viewModel.setWallpaper(null) },
                        onSetInteractionMode = viewModel::setInteractionMode,
                        onConfigureModels = { navController.navigate(Route.Models.name) },
                        onConfigureTasks = { navController.navigate(Route.Tasks.name) },
                        onConfigureMemories = { navController.navigate(Route.Memory.name) },
                        onQuitApp = onQuitApp
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
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = if (state.status.running) onStopVoice else onTalk,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (state.status.running) Icons.Rounded.Stop else Icons.Rounded.Mic, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.status.running) "STOP" else "TALK")
            }
        }

        Spacer(Modifier.height(16.dp))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp, max = 176.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("CHAT CONVERSATION", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            state.recentMessages.forEach { message ->
                val isUser = message.role == MessageRole.User
                Text(
                    text = if (isUser) "YOU: ${message.text}" else "AURA: ${message.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUser) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.loading) {
                Text(
                    text = "AURA IS THINKING...",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (state.appBlocks.isNotEmpty()) {
                val block = state.appBlocks.first()
                Text(
                    text = "${block.label.uppercase()} BLOCKED FOR ${block.remainingMinutes()}M",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
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
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp)
    ) {
        Column {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.75f)
            ) {
                // Solid flat pitch-black visor display
                drawRoundRect(
                    color = Color.Black,
                    cornerRadius = CornerRadius(12f, 12f)
                )

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

                    rotate(degrees = tilt, pivot = Offset(pivotX, pivotY)) {
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(originX + travelX, eyeY + travelY),
                            size = Size(eyeWidth, eyeHeight)
                        )
                    }
                }

                if (interactionMode == "dot") {
                    if (dotAlpha > 0.01f) {
                        val baseDotRadius = size.height * 0.08f
                        val dotScale = when {
                            isSpeaking -> speechWave
                            mode == AuraPresenceMode.Thinking -> dotBreathe
                            mode == AuraPresenceMode.Hearing -> 1f + (voiceLevel.coerceIn(0, 12) / 12f) * 0.45f
                            else -> 1f
                        }
                        drawCircle(
                            color = Color.White.copy(alpha = dotAlpha),
                            radius = baseDotRadius * dotScale,
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
    val isDark = isSystemInDarkTheme()
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
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                AppIcon(
                    drawable = app.icon,
                    modifier = Modifier.size(56.dp)
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
    val isDark = isSystemInDarkTheme()
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
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                RoundedCornerShape(16.dp)
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                AppIcon(
                    drawable = app.icon,
                    modifier = Modifier.size(42.dp)
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
    val isDark = isSystemInDarkTheme()
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                AppIcon(
                    drawable = app.icon,
                    modifier = Modifier.size(34.dp)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
    onRefresh: () -> Unit,
    onSwipeRight: () -> Unit
) {
    var totalDrag = 0f
    var isGridView by remember { mutableStateOf(true) }

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
        Header("APPS", "Search and open installed applications.")
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.appQuery,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
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
                    focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    cursorColor = MaterialTheme.colorScheme.onBackground
                ),
                shape = CircleShape
            )

            IconButton(
                onClick = { isGridView = !isGridView },
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                        CircleShape
                    )
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
                    .padding(16.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
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
private fun OnboardingScreen(
    state: LauncherUiState,
    onGoogleLogin: (String) -> Unit,
    onOpenAiLogin: (String) -> Unit,
    onLocalSetup: (LlmProvider, String) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var showGoogleDialog by remember { mutableStateOf(false) }
    var showOpenAiDialog by remember { mutableStateOf(false) }
    
    var googleKeyInput by remember { mutableStateOf("") }
    var openAiKeyInput by remember { mutableStateOf("") }
    
    var localProvider by remember { mutableStateOf(LlmProvider.Gemini) }
    var localKeyInput by remember { mutableStateOf("") }

    BackHandler(enabled = true) {
        if (step == 2) {
            step = 1
        }
    }

    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AURA",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "SWISS COGNITIVE LAUNCHER",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(48.dp))

            if (step == 1) {
                Text(
                    text = "WELCOME TO AURA",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Select an onboarding option below to configure your digital intelligence environment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                FilledTonalButton(
                    onClick = { showGoogleDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("LOGIN VIA GOOGLE", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { showOpenAiDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("LOGIN VIA OPENAI", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { step = 2 },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("CONTINUE AS LOCAL-FIRST", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "CONFIGURE LOCAL-FIRST ENGINE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Data remains 100% on-device. Please enter a valid API key to drive local-first assistant conversations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(LlmProvider.Gemini, LlmProvider.OpenRouter).forEach { provider ->
                        val selected = localProvider == provider
                        FilledTonalButton(
                            onClick = { localProvider = provider },
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
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = localKeyInput,
                    onValueChange = { localKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (localProvider == LlmProvider.Gemini) "GOOGLE API KEY..." else "OPENROUTER API KEY...") },
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
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { step = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                    ) {
                        Text("BACK", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onLocalSetup(localProvider, localKeyInput) },
                        enabled = localKeyInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text("FINISH SETUP", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showGoogleDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleDialog = false },
            title = { Text("Google Secure Auth Login", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Provide your Google Gemini API Key to log in securely.")
                    OutlinedTextField(
                        value = googleKeyInput,
                        onValueChange = { googleKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("GEMINI API KEY...") },
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGoogleDialog = false
                        onGoogleLogin(googleKeyInput)
                    },
                    enabled = googleKeyInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sign In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOpenAiDialog) {
        AlertDialog(
            onDismissRequest = { showOpenAiDialog = false },
            title = { Text("OpenAI Secure Auth Login", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Provide your OpenAI API Key or Secure Access Token to log in.")
                    OutlinedTextField(
                        value = openAiKeyInput,
                        onValueChange = { openAiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("OPENAI API KEY...") },
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOpenAiDialog = false
                        onOpenAiLogin(openAiKeyInput)
                    },
                    enabled = openAiKeyInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sign In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenAiDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
    onQuitApp: () -> Unit
) {
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
            SettingsRow(
                title = "Default launcher",
                subtitle = "Open Android Home app settings.",
                icon = Icons.Rounded.Home,
                onClick = onOpenHomeSettings
            )
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

@Composable
private fun ScreenShell(
    wallpaperUri: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val wallpaperBitmap = rememberWallpaperPainter(wallpaperUri)
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
            val isDark = isSystemInDarkTheme()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.65f))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Text(subtitle.uppercase(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun AssistantComposer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("ASK AURA...") },
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
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onSend,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Rounded.Search, contentDescription = "Send")
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(label.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground
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
}
