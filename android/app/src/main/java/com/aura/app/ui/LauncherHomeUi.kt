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

@Composable
fun HomeScreen(
    state: LauncherUiState,
    onAssistantInput: (String) -> Unit,
    onSend: () -> Unit,
    onTalk: () -> Unit,
    onStopVoice: () -> Unit,
    onOpenAssistant: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSelectWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onClearAttachment: () -> Unit,
    onLaunchPicker: () -> Unit,
    onLaunchCamera: () -> Unit
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
            .testTag("aura-home-screen")
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (state.status.running) {
                        onStopVoice()
                    } else {
                        onTalk()
                    }
                },
            contentAlignment = Alignment.Center
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
        }
        Spacer(Modifier.weight(1f))
        AssistantComposer(
            value = state.assistantInput,
            onValueChange = onAssistantInput,
            onSend = onSend,
            attachedImageBase64 = state.attachedImageBase64,
            onClearAttachment = onClearAttachment,
            onLaunchPicker = onLaunchPicker,
            onLaunchCamera = onLaunchCamera
        )
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
fun AppGridItem(app: AppInfo, onLaunchApp: (AppInfo) -> Unit) {
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
fun SuggestedAppCard(app: AppInfo, onLaunchApp: (AppInfo) -> Unit) {
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
fun AppListItem(app: AppInfo, onLaunchApp: (AppInfo) -> Unit) {
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
