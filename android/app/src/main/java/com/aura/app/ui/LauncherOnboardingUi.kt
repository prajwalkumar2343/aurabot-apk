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
fun OnboardingScreen(
    state: LauncherUiState,
    onRequestPermissions: () -> Unit,
    onCreateAccount: (email: String, password: String, name: String?, onResult: (Result<com.aura.app.assistant.UserResponse>) -> Unit) -> Unit,
    onSignIn: (email: String, password: String, onResult: (Result<com.aura.app.assistant.UserResponse>) -> Unit) -> Unit,
    onFinishOnboarding: (appMode: String, provider: LlmProvider, apiKey: String, modelId: String, bgListening: Boolean) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    
    // State
    var selectedAppMode by remember { mutableStateOf("launcher") }
    var selectedStorageMode by remember { mutableStateOf("local") }
    var accountMode by remember { mutableStateOf("signIn") }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    
    var selectedProvider by remember { mutableStateOf(LlmProvider.Gemini) }
    var apiKeyInput by remember { mutableStateOf("") }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var authComplete by remember { mutableStateOf(state.session.isLoggedIn) }

    val context = LocalContext.current
    
    // Permission states
    var hasMicState by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var hasNotifState by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var hasLocState by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicState = isGranted
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotifState = isGranted
    }

    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        hasLocState = permissionsMap[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissionsMap[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    val stepTitle = when (step) {
        1 -> "01 // SYSTEM SHELL"
        2 -> "02 // DATA RESIDENCY"
        3 -> "03 // CLOUD ACCOUNT"
        4 -> "04 // LLM ENGINE"
        5 -> "05 // CREDENTIALS"
        6 -> "06 // HARDWARE CAPABILITY"
        7 -> "07 // HARDWARE CAPABILITY"
        8 -> "08 // HARDWARE CAPABILITY"
        else -> ""
    }

    val stepHeadline = when (step) {
        1 -> "SELECT APP MODE"
        2 -> "CHOOSE DATA STORAGE"
        3 -> "SIGN IN / REGISTER"
        4 -> "SELECT PROVIDER"
        5 -> "ENTER API KEY"
        6 -> "WE NEED ACCESS."
        7 -> "WE NEED ACCESS."
        8 -> "WE NEED ACCESS."
        else -> ""
    }

    val onContinue = {
        if (step == 2 && selectedStorageMode == "local") {
            step = 4
        } else if (step == 3 && !authComplete) {
            selectedStorageMode = "local"
            step = 4
        } else if (step < 8) {
            step++
        } else {
            onFinishOnboarding(
                selectedAppMode,
                selectedProvider,
                apiKeyInput,
                if (selectedProvider == LlmProvider.Gemini) DEFAULT_GEMINI_MODEL else "gpt-4o-mini",
                hasMicState
            )
        }
    }

    BackHandler(enabled = step > 1) {
        if (step == 4 && selectedStorageMode == "local") {
            step = 2
        } else {
            step--
        }
    }

    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val layout = phoneLayoutProfile(maxWidth, maxHeight)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (layout.dense) 8.dp else 16.dp,
                        vertical = if (layout.short) 6.dp else 12.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header (always visible)
                OnboardingHeader(
                    step = step,
                    title = stepTitle
                )

                Spacer(Modifier.height(if (layout.short) 8.dp else 16.dp))

                // Step content Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = if (step >= 6) Arrangement.Center else Arrangement.spacedBy(if (layout.short) 10.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (step < 6) {
                        Text(
                            text = stepHeadline,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Start
                            ),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    when (step) {
                        1 -> {
                            val modes = listOf(
                                "launcher" to Pair("01 / HOME LAUNCHER", "Replace your default Android home screen with the Aura system environment."),
                                "normal" to Pair("02 / SANDBOX APP", "Run Aura as a standard independent application without home integration."),
                                "overlay" to Pair("03 / OVERLAY ASSISTANT", "Display the assistant floating overlay over existing system tasks.")
                            )
                            modes.forEach { (mode, pair) ->
                                val isSelected = selectedAppMode == mode
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .bounceClick { selectedAppMode = mode }
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = pair.first,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = pair.second,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        2 -> {
                            val storages = listOf(
                                "local" to Pair("LOCAL STORAGE", "Save settings, tasks, and memory entries securely on this hardware only."),
                                "cloud" to Pair("CLOUD SYNCHRONIZATION", "Synchronize tasks and database memory logs in real-time across devices.")
                            )
                            storages.forEach { (mode, pair) ->
                                val isSelected = selectedStorageMode == mode
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .bounceClick { selectedStorageMode = mode }
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = pair.first,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = pair.second,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        3 -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                                    .padding(4.dp)
                            ) {
                                val authModes = listOf("signIn" to "Sign In", "create" to "Register")
                                authModes.forEach { (mode, label) ->
                                    val isSelected = accountMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(if (isSelected) Color.White else Color.Transparent)
                                            .bounceClick { 
                                                accountMode = mode 
                                                authMessage = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label.uppercase(),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            if (accountMode == "create") {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    label = { Text("NAME_ENTRY", color = Color.White.copy(alpha = 0.4f)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                )
                            }

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                label = { Text("EMAIL_ADDRESS", color = Color.White.copy(alpha = 0.4f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                label = { Text("PASSWORD_HASH", color = Color.White.copy(alpha = 0.4f)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )

                            Button(
                                onClick = {
                                    val done: (Result<com.aura.app.assistant.UserResponse>) -> Unit = { result ->
                                        authComplete = result.isSuccess
                                        authMessage = result.fold(
                                            onSuccess = { "Authenticated successfully as ${it.email}." },
                                            onFailure = { it.message ?: "Authentication failed." }
                                        )
                                    }
                                    if (accountMode == "create") {
                                        onCreateAccount(emailInput.trim(), passwordInput, nameInput.trim(), done)
                                    } else {
                                        onSignIn(emailInput.trim(), passwordInput, done)
                                    }
                                },
                                enabled = !state.loading && emailInput.isNotBlank() && passwordInput.length >= 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                    disabledContainerColor = Color.White.copy(alpha = 0.2f),
                                    disabledContentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = if (state.loading) "AUTHENTICATING..." else if (accountMode == "create") "CREATE ACCOUNT" else "AUTHENTICATE",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            authMessage?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (authComplete) Color.White else Color.Red,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                        
                        4 -> {
                            val providers = listOf(
                                LlmProvider.Gemini to Pair("GEMINI CORE", "Native system integration. Recommended for speed and high-efficiency voice processing."),
                                LlmProvider.OpenAI to Pair("OPENAI API", "Connect external GPT models to execute actions.")
                            )
                            providers.forEach { (provider, pair) ->
                                val isSelected = selectedProvider == provider
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .bounceClick { selectedProvider = provider }
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isSelected) "[X] ${pair.first}" else "[ ] ${pair.first}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                            color = Color.White
                                        )
                                        Text(
                                            text = pair.second,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        5 -> {
                            val providerLabel = if (selectedProvider == LlmProvider.Gemini) "Gemini" else "OpenAI"
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                label = { Text("$providerLabel API Key", color = Color.White.copy(alpha = 0.4f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Aura core requires an API key to execute cognitive actions. Keys are securely stored in the on-device keystore. You can skip this and configure it later in settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        6 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "WE NEED ACCESS.",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                    color = Color.White
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "MICROPHONE // VOICE CAPTURE",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = "Allows Aura to listen and transcribe conversations in real-time.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(32.dp))
                                Button(
                                    onClick = {
                                        micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    },
                                    enabled = !hasMicState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black,
                                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                                        disabledContentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Text(text = if (hasMicState) "ACCESS GRANTED" else "GRANT MICROPHONE ACCESS", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "STATUS: ${if (hasMicState) "GRANTED" else "PENDING"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                        
                        7 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "WE NEED ACCESS.",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                    color = Color.White
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "NOTIFICATIONS // OVERLAYS",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = "Allows Aura to display active voice states and launcher alerts.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(32.dp))
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            hasNotifState = true
                                        }
                                    },
                                    enabled = !hasNotifState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black,
                                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                                        disabledContentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Text(text = if (hasNotifState) "ACCESS GRANTED" else "ENABLE NOTIFICATIONS", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "STATUS: ${if (hasNotifState) "GRANTED" else "PENDING"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                        
                        8 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "WE NEED ACCESS.",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                    color = Color.White
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "LOCATION // LOCAL CONTEXT",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = "Provides local environmental coordinates for ambient assistant context.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(32.dp))
                                Button(
                                    onClick = {
                                        locLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                                android.Manifest.permission.ACCESS_FINE_LOCATION
                                            )
                                        )
                                    },
                                    enabled = !hasLocState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black,
                                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                                        disabledContentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Text(text = if (hasLocState) "ACCESS GRANTED" else "ALLOW LOCATION ACCESS", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "STATUS: ${if (hasLocState) "GRANTED" else "PENDING"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(if (layout.short) 8.dp else 16.dp))

                // Bottom Navigation Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (layout.short) 4.dp else 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (layout.dense) 8.dp else 12.dp)
                ) {
                    if (step > 1) {
                        Button(
                            onClick = {
                                if (step == 4 && selectedStorageMode == "local") {
                                    step = 2
                                } else {
                                    step--
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("BACK", fontWeight = FontWeight.Bold)
                        }
                    }

                    val isNextEnabled = when (step) {
                        3 -> authComplete
                        else -> true
                    }

                    val nextText = when (step) {
                        3 -> if (authComplete) "CONTINUE" else "SKIP SYNC"
                        5 -> "SAVE & CONTINUE"
                        8 -> "INITIALIZE SYSTEM"
                        6, 7 -> if ((step == 6 && hasMicState) || (step == 7 && hasNotifState)) "CONTINUE" else "SKIP STEP"
                        else -> "CONTINUE"
                    }

                    Button(
                        onClick = {
                            if (step == 3 && !authComplete) {
                                selectedStorageMode = "local"
                                step = 4
                            } else {
                                onContinue()
                            }
                        },
                        enabled = isNextEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.2f),
                            disabledContentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(nextText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
