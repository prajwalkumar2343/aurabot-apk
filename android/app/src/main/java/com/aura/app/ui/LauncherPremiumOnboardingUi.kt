package com.aura.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aura.app.BuildConfig
import com.aura.app.assistant.GoogleSignInClient
import com.aura.app.assistant.LlmProvider
import com.aura.app.assistant.validateLocalMongoSettings
import kotlinx.coroutines.launch
import kotlin.math.sin

private val OnboardingInk = Color(0xFFF5F5F7)
private val OnboardingMuted = Color(0xFFA1A1A6)
private val OnboardingSurface = Color(0xFF111111)
private val OnboardingRule = Color.White.copy(alpha = 0.12f)

private data class OnboardingCopy(
    val chapter: String,
    val title: String,
    val body: String
)

private val onboardingCopy = mapOf(
    1 to OnboardingCopy("Welcome", "Your phone, with a mind of its own.", "Aura turns your Home screen into a calm workspace for conversation, useful tools, and work that keeps moving."),
    2 to OnboardingCopy("Choose setup", "Make Aura yours.", "Choose the ready-to-use Aura cloud or keep every connection under your control."),
    3 to OnboardingCopy("Connect locally", "Your data. Your database.", "Aura verifies the connection before saving anything. Credentials remain encrypted on this device."),
    4 to OnboardingCopy("Choose intelligence", "Pick your AI provider.", "Use the provider you already trust. You can change this later in Settings."),
    5 to OnboardingCopy("Connect provider", "Add your private key.", "Your key is protected by Android Keystore and is never sent to Aura's backend."),
    6 to OnboardingCopy("Enable voice", "Talk naturally.", "Microphone access lets Aura hear a request only when you choose to speak."),
    7 to OnboardingCopy("Stay informed", "Know when work is ready.", "Notifications surface completed work, approvals, and active voice status."),
    8 to OnboardingCopy("Finish setup", "Ready when you are.", "Location is optional. It helps Aura answer with local context such as weather and nearby places.")
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun OnboardingScreen(
    state: LauncherUiState,
    onRequestPermissions: () -> Unit,
    onGoogleChallenge: (onResult: (Result<String>) -> Unit) -> Unit,
    onGoogleSignIn: (idToken: String, nonce: String, onResult: (Result<com.aura.app.assistant.UserResponse>) -> Unit) -> Unit,
    onVerifyLocalDatabase: (connectionUri: String, databaseName: String, onResult: (Result<Unit>) -> Unit) -> Unit,
    onFinishOnboarding: (OnboardingConfiguration) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var mode by remember { mutableStateOf(OnboardingMode.ManagedGoogle) }
    var provider by remember { mutableStateOf(LlmProvider.Gemini) }
    var apiKey by remember { mutableStateOf("") }
    var mongoUri by remember { mutableStateOf("") }
    var databaseName by remember { mutableStateOf("aura") }
    var databaseError by remember { mutableStateOf<String?>(null) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var isVerifyingDatabase by remember { mutableStateOf(false) }
    var showMongoUri by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val googleSignInClient = remember(context) {
        GoogleSignInClient(context, BuildConfig.STALKY_GOOGLE_WEB_CLIENT_ID)
    }

    var hasMicrophone by remember {
        mutableStateOf(context.hasPermission(android.Manifest.permission.RECORD_AUDIO))
    }
    var hasNotifications by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || context.hasPermission(android.Manifest.permission.POST_NOTIFICATIONS))
    }
    var hasLocation by remember {
        mutableStateOf(context.hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val microphoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicrophone = granted
        haptics.performHapticFeedback(if (granted) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotifications = granted
        haptics.performHapticFeedback(if (granted) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasLocation = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        haptics.performHapticFeedback(if (hasLocation) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }

    fun goTo(nextStep: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        step = nextStep.coerceIn(1, 8)
    }

    fun beginGoogleSignIn() {
        authMessage = null
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onGoogleChallenge { challengeResult ->
            val nonce = challengeResult.getOrElse { error ->
                authMessage = error.message ?: "Could not start Google sign-in."
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                return@onGoogleChallenge
            }
            scope.launch {
                try {
                    val idToken = googleSignInClient.signIn(context, nonce)
                    onGoogleSignIn(idToken, nonce) { result ->
                        authMessage = result.fold(
                            onSuccess = { "Signed in as ${it.email}. Aura is ready." },
                            onFailure = { it.message ?: "Google sign-in failed." }
                        )
                        haptics.performHapticFeedback(if (result.isSuccess) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                        if (result.isSuccess) {
                            provider = LlmProvider.Gemini
                            apiKey = ""
                            step = 6
                        }
                    }
                } catch (error: Exception) {
                    authMessage = error.message ?: "Google sign-in failed."
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }
            }
        }
    }

    fun verifyDatabase() {
        val validation = runCatching { validateLocalMongoSettings(mongoUri, databaseName) }
        databaseError = validation.exceptionOrNull()?.message
        if (validation.isFailure) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            return
        }
        isVerifyingDatabase = true
        databaseError = null
        onVerifyLocalDatabase(mongoUri, databaseName) { result ->
            isVerifyingDatabase = false
            databaseError = result.exceptionOrNull()?.message
            haptics.performHapticFeedback(if (result.isSuccess) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
            if (result.isSuccess) step = 4
        }
    }

    fun finish() {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onFinishOnboarding(
            OnboardingConfiguration(
                mode = mode,
                appMode = "launcher",
                provider = provider,
                apiKey = if (mode == OnboardingMode.Local) apiKey else "",
                modelId = defaultOnboardingModel(provider),
                mongoConnectionUri = if (mode == OnboardingMode.Local) mongoUri else "",
                mongoDatabaseName = if (mode == OnboardingMode.Local) databaseName else "",
                backgroundListening = ENABLE_BACKGROUND_LISTENING_AFTER_ONBOARDING
            )
        )
    }

    val goBack = {
        goTo(if (step == 6 && mode == OnboardingMode.ManagedGoogle) 2 else step - 1)
    }
    BackHandler(enabled = step > 1, onBack = goBack)

    ScreenShell(wallpaperUri = state.session.wallpaperUri) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("aura-onboarding-step-$step")
        ) {
            val compact = maxHeight < 700.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (maxWidth < 400.dp) 20.dp else 28.dp)
                    .widthIn(max = 620.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PremiumOnboardingHeader(step = step)
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val direction = if (targetState > initialState) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
                        (slideIntoContainer(direction, tween(420)) + fadeIn(tween(260))) togetherWith
                            (slideOutOfContainer(direction, tween(320)) + fadeOut(tween(180)))
                    },
                    label = "onboarding-step",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { visibleStep ->
                    val copy = onboardingCopy.getValue(visibleStep)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
                        OnboardingStepIntro(copy = copy, step = visibleStep, compact = compact)
                        Spacer(Modifier.height(if (compact) 18.dp else 28.dp))
                        when (visibleStep) {
                            1 -> WelcomeStep()
                            2 -> SetupModeStep(mode, authMessage) { selected ->
                                mode = selected
                                authMessage = null
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            3 -> DatabaseStep(
                                mongoUri = mongoUri,
                                onMongoUriChange = { mongoUri = it; databaseError = null },
                                databaseName = databaseName,
                                onDatabaseNameChange = { databaseName = it; databaseError = null },
                                error = databaseError,
                                revealUri = showMongoUri,
                                onToggleReveal = { showMongoUri = !showMongoUri }
                            )
                            4 -> ProviderStep(provider) { selected ->
                                provider = selected
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            5 -> ApiKeyStep(provider, apiKey, { apiKey = it }, showApiKey) { showApiKey = !showApiKey }
                            6 -> PermissionStep(Icons.Rounded.Mic, "Voice stays off until you tap the microphone.", hasMicrophone, "Allow microphone") {
                                microphoneLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                            7 -> PermissionStep(Icons.Rounded.Notifications, "Only useful updates—completed work, approvals, and live voice status.", hasNotifications, "Allow notifications") {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                else hasNotifications = true
                            }
                            8 -> FinishStep(mode, provider, hasMicrophone, hasNotifications, hasLocation) {
                                locationLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION))
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }

                PremiumOnboardingActions(
                    step = step,
                    mode = mode,
                    hasMicrophone = hasMicrophone,
                    hasNotifications = hasNotifications,
                    loading = state.loading || isVerifyingDatabase,
                    enabled = when (step) {
                        3 -> mongoUri.isNotBlank() && databaseName.isNotBlank()
                        5 -> apiKey.isNotBlank()
                        else -> true
                    },
                    onBack = goBack,
                    onNext = {
                        when (step) {
                            2 -> if (mode == OnboardingMode.ManagedGoogle) beginGoogleSignIn() else goTo(3)
                            3 -> verifyDatabase()
                            8 -> finish()
                            else -> goTo(step + 1)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PremiumOnboardingHeader(step: Int) {
    val progress by animateFloatAsState(step / 8f, tween(420), label = "onboarding-progress")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("AURA", color = OnboardingInk, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp, style = MaterialTheme.typography.labelSmall)
            Text("$step of 8", color = OnboardingMuted, style = MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(OnboardingRule)) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(OnboardingInk))
        }
    }
}

@Composable
private fun OnboardingStepIntro(copy: OnboardingCopy, step: Int, compact: Boolean) {
    AuraArtwork(
        step = step,
        modifier = Modifier.size(
            width = if (step == 1) {
                if (compact) 210.dp else 260.dp
            } else {
                if (compact) 170.dp else 200.dp
            },
            height = if (step == 1) {
                if (compact) 116.dp else 144.dp
            } else {
                if (compact) 92.dp else 110.dp
            }
        )
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(26.dp).height(1.dp).background(OnboardingInk))
        Text(copy.chapter, color = OnboardingMuted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        copy.title,
        color = OnboardingInk,
        textAlign = TextAlign.Start,
        fontWeight = FontWeight.SemiBold,
        style = if (step == 1) MaterialTheme.typography.displayLarge.copy(fontSize = if (compact) 40.sp else 48.sp, lineHeight = if (compact) 41.sp else 49.sp) else MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 34.sp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    Text(copy.body, color = OnboardingMuted, textAlign = TextAlign.Start, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp))
}

@Composable
private fun AuraArtwork(step: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "onboarding-aura-eyes")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(6_200, easing = LinearEasing)),
        label = "onboarding-aura-drift"
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4_200
                1f at 0 using LinearEasing
                1f at 3_000 using LinearEasing
                .18f at 3_140 using LinearEasing
                1f at 3_320 using LinearEasing
                1f at 4_200 using LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "onboarding-aura-blink"
    )
    val expressionHeight by animateFloatAsState(
        targetValue = when (step) {
            2 -> .58f
            3, 5 -> .76f
            6 -> 1.10f
            7 -> .88f
            8 -> .50f
            else -> 1f
        },
        animationSpec = tween(420),
        label = "onboarding-aura-expression"
    )
    val expressionOffset by animateFloatAsState(
        targetValue = when (step) {
            2 -> .045f
            8 -> -.025f
            else -> 0f
        },
        animationSpec = tween(420),
        label = "onboarding-aura-gaze"
    )
    Canvas(modifier) {
        val eyeWidth = size.width * .28f
        val eyeHeight = size.height * .32f * expressionHeight * (.22f + .78f * blink)
        val happyLift = if (step == 8) -size.height * .08f else 0f
        val eyeY = (size.height - eyeHeight) / 2f + happyLift
        val driftX = sin(phase) * size.width * .016f + expressionOffset * size.width
        val driftY = sin(phase * 1.7f) * size.height * .012f
        val leftEyeX = size.width * .18f + driftX
        val rightEyeX = size.width - size.width * .18f - eyeWidth + driftX
        val eyeBrush = Brush.linearGradient(
            colors = listOf(Color.White, Color(0xFFB3B3B3)),
            start = Offset(0f, eyeY),
            end = Offset(size.width, eyeY + eyeHeight)
        )
        val glowRadius = eyeWidth * 1.35f

        listOf(leftEyeX, rightEyeX).forEach { eyeX ->
            val center = Offset(eyeX + eyeWidth / 2f, eyeY + eyeHeight / 2f + driftY)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = .07f), Color.Transparent),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )
            drawRoundRect(
                brush = eyeBrush,
                topLeft = Offset(eyeX, eyeY + driftY),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(eyeWidth * .45f, eyeHeight * .45f)
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = OnboardingRule, shape = RoundedCornerShape(18.dp))
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "AURA WIDGET CANVAS",
            color = OnboardingMuted,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(OnboardingRule))
        Row(Modifier.fillMaxWidth()) {
            listOf("Listen", "Build", "Remember").forEachIndexed { index, label ->
                if (index > 0) Box(Modifier.width(1.dp).height(36.dp).background(OnboardingRule))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("0${index + 1}", color = OnboardingMuted, style = MaterialTheme.typography.labelSmall)
                    Text(label, color = OnboardingInk, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SetupModeStep(selected: OnboardingMode, message: String?, onSelect: (OnboardingMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OnboardingChoiceCard(OnboardingMode.ManagedGoogle == selected, Icons.Rounded.Cloud, "Aura Cloud", "Fastest setup", "Google sign-in · managed database · Gemini included", "aura-onboarding-google-option") { onSelect(OnboardingMode.ManagedGoogle) }
        OnboardingChoiceCard(OnboardingMode.Local == selected, Icons.Rounded.Storage, "Run locally", "Maximum control", "Your MongoDB · your provider · no Aura backend", "aura-onboarding-local-option") { onSelect(OnboardingMode.Local) }
        message?.let { FeedbackMessage(it, success = it.startsWith("Signed in")) }
    }
}

@Composable
private fun DatabaseStep(
    mongoUri: String,
    onMongoUriChange: (String) -> Unit,
    databaseName: String,
    onDatabaseNameChange: (String) -> Unit,
    error: String?,
    revealUri: Boolean,
    onToggleReveal: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PremiumTextField(mongoUri, onMongoUriChange, "MongoDB connection URI", "mongodb://your-host:27017/?tls=true", "aura-onboarding-mongo-uri", !revealUri, onToggleReveal)
        PremiumTextField(databaseName, onDatabaseNameChange, "Database name", "aura", "aura-onboarding-mongo-database")
        error?.let { FeedbackMessage(it, false) }
        PrivacyNote("A dedicated, least-privilege database user is recommended. Aura verifies TLS and encrypts the URI before storage.")
    }
}

@Composable
private fun ProviderStep(selected: LlmProvider, onSelect: (LlmProvider) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OnboardingChoiceCard(selected == LlmProvider.Gemini, Icons.Rounded.Key, "Google Gemini", "Recommended", "Responsive voice and everyday assistance", null) { onSelect(LlmProvider.Gemini) }
        OnboardingChoiceCard(selected == LlmProvider.OpenAI, Icons.Rounded.Key, "OpenAI", "Flexible", "Connect supported GPT models with your API key", null) { onSelect(LlmProvider.OpenAI) }
    }
}

@Composable
private fun ApiKeyStep(provider: LlmProvider, value: String, onValueChange: (String) -> Unit, reveal: Boolean, onToggleReveal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PremiumTextField(value, onValueChange, "${provider.label} API key", "Paste your key", "aura-onboarding-api-key", !reveal, onToggleReveal)
        PrivacyNote("Encrypted with Android Keystore. The key is used only for direct requests to ${provider.label}.")
    }
}

@Composable
private fun PermissionStep(icon: ImageVector, reassurance: String, granted: Boolean, actionLabel: String, onRequest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(if (granted) Color.White.copy(alpha = .10f) else OnboardingSurface).border(1.dp, if (granted) Color.White.copy(alpha = .7f) else OnboardingRule, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (granted) Icons.Rounded.Check else icon, null, tint = OnboardingInk, modifier = Modifier.size(34.dp))
        }
        Text(if (granted) "Access granted" else reassurance, color = if (granted) OnboardingInk else OnboardingMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (!granted) SecondaryActionButton(actionLabel, onRequest)
    }
}

@Composable
private fun FinishStep(mode: OnboardingMode, provider: LlmProvider, microphone: Boolean, notifications: Boolean, location: Boolean, onRequestLocation: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryRow(if (mode == OnboardingMode.ManagedGoogle) "Aura Cloud" else "Local mode", "Connected")
        SummaryRow(if (mode == OnboardingMode.ManagedGoogle) "Managed Gemini" else provider.label, "Ready")
        SummaryRow("Microphone", if (microphone) "Allowed" else "Later")
        SummaryRow("Notifications", if (notifications) "Allowed" else "Later")
        SummaryRow("Location", if (location) "Allowed" else "Optional")
        if (!location) SecondaryActionButton("Allow location", onRequestLocation)
    }
}

@Composable
private fun OnboardingChoiceCard(selected: Boolean, icon: ImageVector, title: String, badge: String, detail: String, tag: String?, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (pressed) .985f else 1f)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) OnboardingInk else OnboardingSurface)
            .border(1.dp, if (selected) Color.White.copy(alpha = .82f) else OnboardingRule, RoundedCornerShape(22.dp))
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(interactionSource = interactions, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (selected) Color.Black.copy(alpha = .08f) else Color.White.copy(alpha = .05f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (selected) Color.Black else OnboardingMuted, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = if (selected) Color.Black else OnboardingInk, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(badge, color = if (selected) Color.Black.copy(alpha = .58f) else OnboardingMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text(detail, color = if (selected) Color.Black.copy(alpha = .62f) else OnboardingMuted, style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, if (selected) Color.Black else OnboardingMuted.copy(alpha = .6f), CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(Color.Black))
        }
    }
}

@Composable
private fun PremiumTextField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, tag: String, password: Boolean = false, onToggleReveal: (() -> Unit)? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = OnboardingMuted.copy(alpha = .55f)) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = onToggleReveal?.let {
            {
                IconButton(onClick = it) {
                    Icon(
                        if (password) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        if (password) "Show" else "Hide"
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OnboardingInk,
            unfocusedTextColor = OnboardingInk,
            focusedLabelColor = OnboardingInk,
            unfocusedLabelColor = OnboardingMuted,
            focusedBorderColor = OnboardingInk,
            unfocusedBorderColor = OnboardingRule,
            cursorColor = OnboardingInk,
            focusedContainerColor = OnboardingSurface,
            unfocusedContainerColor = OnboardingSurface
        )
    )
}

@Composable
private fun PrivacyNote(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .035f)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Lock, null, tint = OnboardingInk, modifier = Modifier.size(18.dp))
        Text(text, color = OnboardingMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FeedbackMessage(text: String, success: Boolean) {
    val borderAlpha = if (success) .18f else .38f
    Text(
        text,
        color = OnboardingInk,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = .05f))
            .border(1.dp, Color.White.copy(alpha = borderAlpha), RoundedCornerShape(14.dp))
            .padding(12.dp)
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OnboardingInk, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = if (value == "Ready" || value == "Connected" || value == "Allowed") OnboardingInk else OnboardingMuted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SecondaryActionButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .08f), contentColor = OnboardingInk)) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.PremiumOnboardingActions(step: Int, mode: OnboardingMode, hasMicrophone: Boolean, hasNotifications: Boolean, loading: Boolean, enabled: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    val label = when (step) {
        2 -> if (mode == OnboardingMode.ManagedGoogle) "CONTINUE WITH GOOGLE" else "CONTINUE LOCALLY"
        3 -> "VERIFY DATABASE SETTINGS"
        5 -> "SAVE & CONTINUE"
        6 -> if (hasMicrophone) "CONTINUE" else "SKIP STEP"
        7 -> if (hasNotifications) "CONTINUE" else "SKIP STEP"
        8 -> "INITIALIZE SYSTEM"
        else -> "CONTINUE"
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (step > 1) {
            Button(onClick = onBack, enabled = !loading, modifier = Modifier.size(54.dp), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .07f), contentColor = OnboardingInk, disabledContainerColor = Color.White.copy(alpha = .03f))) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
        }
        val interactions = remember { MutableInteractionSource() }
        val pressed by interactions.collectIsPressedAsState()
        Button(
            onClick = onNext,
            enabled = enabled && !loading,
            interactionSource = interactions,
            modifier = Modifier.weight(1f).height(54.dp).scale(if (pressed) .985f else 1f).testTag("aura-onboarding-next"),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OnboardingInk, contentColor = Color.Black, disabledContainerColor = Color.White.copy(alpha = .12f), disabledContentColor = Color.White.copy(alpha = .38f))
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                Spacer(Modifier.width(9.dp))
            }
            Text(if (loading) "PLEASE WAIT" else label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            if (!loading) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
