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
fun AppsScreen(
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
        modifier = Modifier
            .testTag("aura-apps-screen")
            .pointerInput(Unit) {
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
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val layout = phoneLayoutProfile(maxWidth, maxHeight)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(layout.appGridColumns),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(state.filteredApps, key = { it.componentName.flattenToString() }) { app ->
                                AppGridItem(app, onLaunchApp)
                            }
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
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = phoneLayoutProfile(maxWidth, 720.dp).storeGridColumns
                val storeRows = ((visibleApps.size + columns - 1) / columns).coerceAtLeast(1)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
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
                            .bounceClick { onInstall(bundle) }
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
            .bounceClick { onOpen(miniApp) }
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
fun MiniAppAvatar(
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
