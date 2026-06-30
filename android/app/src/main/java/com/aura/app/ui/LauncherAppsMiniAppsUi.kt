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
fun MiniAppRuntimeScreen(
    bundle: MiniAppBundle?,
    records: List<MiniAppRecord>,
    versions: List<MiniAppVersion>,
    evolutionSuggestion: MiniAppEvolutionSuggestion?,
    revisionPreview: MiniAppRevisionPreview?,
    revising: Boolean,
    onBack: () -> Unit,
    onRunAction: (String, String) -> Unit,
    onCreateRecord: (String, String, Map<String, String>) -> Unit,
    onDeleteRecord: (String, String) -> Unit,
    onRevise: (String) -> Unit,
    onDraftEvolution: () -> Unit,
    onDismissEvolution: () -> Unit,
    onAcceptRevision: () -> Unit,
    onDismissRevision: () -> Unit,
    onRollback: (Int) -> Unit,
    onReactListRecords: suspend (String, String?) -> List<MiniAppRecord>,
    onReactCreateRecord: suspend (String, String, Map<String, String>) -> MiniAppRecord,
    onReactUpdateRecord: suspend (String, String, Map<String, String>) -> MiniAppRecord?,
    onReactDeleteRecord: suspend (String, String) -> Boolean
) {
    if (bundle == null) {
        ScreenShell(wallpaperUri = null) {
            MiniAppMissingState(onBack, "Mini app is not available")
        }
        return
    }
    if (bundle.runtime == "react") {
        MiniAppReactRuntimeScreen(
            bundle = bundle,
            versions = versions,
            evolutionSuggestion = evolutionSuggestion,
            revisionPreview = revisionPreview,
            revising = revising,
            onBack = onBack,
            onRevise = onRevise,
            onDraftEvolution = onDraftEvolution,
            onDismissEvolution = onDismissEvolution,
            onAcceptRevision = onAcceptRevision,
            onDismissRevision = onDismissRevision,
            onRollback = onRollback,
            onListRecords = onReactListRecords,
            onCreateRecord = onReactCreateRecord,
            onUpdateRecord = onReactUpdateRecord,
            onDeleteRecord = onReactDeleteRecord
        )
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
            item(key = "forge") {
                MiniAppForgePanel(
                    bundle = bundle,
                    versions = versions,
                    evolutionSuggestion = evolutionSuggestion,
                    revisionPreview = revisionPreview,
                    revising = revising,
                    primary = primary,
                    onRevise = onRevise,
                    onDraftEvolution = onDraftEvolution,
                    onDismissEvolution = onDismissEvolution,
                    onAcceptRevision = onAcceptRevision,
                    onDismissRevision = onDismissRevision,
                    onRollback = onRollback
                )
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
fun MiniAppForgePanel(
    bundle: MiniAppBundle,
    versions: List<MiniAppVersion>,
    evolutionSuggestion: MiniAppEvolutionSuggestion?,
    revisionPreview: MiniAppRevisionPreview?,
    revising: Boolean,
    primary: Color,
    onRevise: (String) -> Unit,
    onDraftEvolution: () -> Unit,
    onDismissEvolution: () -> Unit,
    onAcceptRevision: () -> Unit,
    onDismissRevision: () -> Unit,
    onRollback: (Int) -> Unit
) {
    var instruction by remember(bundle.id, revisionPreview?.bundle?.version) { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(BorderStroke(1.dp, primary.copy(alpha = 0.16f)), RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = primary)
            Column(modifier = Modifier.weight(1f)) {
                Text("Forge", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "v${bundle.version} / ${bundle.dataSchema.fields.size} fields / ${bundle.actions.size} actions / ${bundle.assistantIntents.size} intents",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (revising) {
                Text(
                    "Drafting",
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = primary
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = instruction,
                onValueChange = { instruction = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add soreness, charts, actions...") },
                singleLine = true,
                enabled = !revising,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )
            FilledTonalButton(
                onClick = {
                    onRevise(instruction)
                    instruction = ""
                },
                enabled = instruction.isNotBlank() && !revising,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(54.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, "Forge revision")
            }
        }

        AnimatedVisibility(visible = evolutionSuggestion != null && revisionPreview == null && !revising) {
            evolutionSuggestion?.let { suggestion ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(primary.copy(alpha = 0.1f))
                        .border(BorderStroke(1.dp, primary.copy(alpha = 0.18f)), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = primary, modifier = Modifier.size(18.dp))
                        Text(
                            "Living upgrade",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                            color = primary
                        )
                    }
                    Text(
                        suggestion.title,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        suggestion.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (suggestion.proposedFields.isNotEmpty()) {
                        Text(
                            suggestion.proposedFields.joinToString(" + ") { formatMiniAppFieldLabel(it.name) },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onDraftEvolution,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primary)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Draft")
                        }
                        OutlinedButton(onClick = onDismissEvolution, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Clear, null)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = revisionPreview != null) {
            revisionPreview?.let { preview ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(primary.copy(alpha = 0.08f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Preview v${preview.bundle.version}",
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        preview.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    preview.migrationPlan.take(3).forEach { step ->
                        Text(
                            "- $step",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${preview.bundle.dataSchema.fields.size} fields / ${preview.bundle.actions.size} actions / ${preview.bundle.assistantIntents.size} intents",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onAcceptRevision,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primary)
                        ) {
                            Icon(Icons.Rounded.Check, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Apply")
                        }
                        OutlinedButton(onClick = onDismissRevision, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Clear, null)
                        }
                    }
                }
            }
        }

        val rollbackTargets = versions.filterNot { it.active }.take(3)
        if (rollbackTargets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Rollback",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rollbackTargets.forEach { version ->
                    TextButton(onClick = { onRollback(version.version) }) {
                        Text("v${version.version}")
                    }
                }
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
fun MiniAppTopBar(
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
