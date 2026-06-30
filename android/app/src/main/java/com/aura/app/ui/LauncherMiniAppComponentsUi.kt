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
fun MiniAppComponentView(
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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = phoneLayoutProfile(maxWidth, 720.dp).actionGridColumns
            val rows = ((component.items.size + columns - 1) / columns).coerceAtLeast(1)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.height((rows * 104).dp),
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

fun formatMiniAppFieldLabel(value: String): String =
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

fun parseMiniAppColor(value: String, fallback: Color): Color =
    try {
        Color(android.graphics.Color.parseColor(value))
    } catch (_: Exception) {
        fallback
    }

fun formatMiniAppTime(value: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(value))

fun isToday(value: Long): Boolean {
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

fun calculateStreak(records: List<MiniAppRecord>): Int {
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
