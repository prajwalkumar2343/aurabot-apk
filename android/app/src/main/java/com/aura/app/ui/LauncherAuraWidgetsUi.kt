package com.aura.app.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aura.app.R
import com.aura.app.widgets.AuraWidget
import com.aura.app.widgets.AuraWidgetActionType
import com.aura.app.widgets.AuraWidgetStatus
import com.aura.app.widgets.AuraWidgetPresentation
import com.aura.app.widgets.HostedAndroidWidget
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AuraAssistantTile(
    mode: AuraPresenceMode,
    voiceLevel: Int,
    isSpeaking: Boolean,
    runState: String?,
    runPhase: String?,
    activeSubagents: Int,
    needsApproval: Boolean,
    modifier: Modifier = Modifier,
    emotion: String = "neutral",
    createdEmotion: String? = null,
    assistantText: String,
    onActivate: () -> Unit
) {
    val auraEmotion = remember(emotion, createdEmotion) {
        AuraEmotion.resolve(emotion, createdEmotion)
    }
    val emotionTransition = updateTransition(auraEmotion, label = "aura_emotion")
    val openness by emotionTransition.animateFloat(label = "emotion_openness") { it.profile.openness }
    val tiltDegrees by emotionTransition.animateFloat(label = "emotion_tilt") { it.profile.tiltDegrees }
    val gazeX by emotionTransition.animateFloat(label = "emotion_gaze_x") { it.profile.gazeX }
    val gazeY by emotionTransition.animateFloat(label = "emotion_gaze_y") { it.profile.gazeY }
    val pupilScale by emotionTransition.animateFloat(label = "emotion_pupil") { it.profile.pupilScale }
    val glow by emotionTransition.animateFloat(label = "emotion_glow") { it.profile.glow }
    val bounce by emotionTransition.animateFloat(label = "emotion_bounce") { it.profile.bounce }
    val asymmetry by emotionTransition.animateFloat(label = "emotion_asymmetry") { it.profile.asymmetry }
    val browAngle by emotionTransition.animateFloat(label = "emotion_brow") { it.profile.browAngle }
    val winkStrength by emotionTransition.animateFloat(label = "emotion_wink") { it.profile.winkStrength }
    val sparkle by emotionTransition.animateFloat(label = "emotion_sparkle") {
        it.profile.sparkleStrength
    }
    val transition = rememberInfiniteTransition(label = "aura_tile")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "voice_phase"
    )
    val blinkClock by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(5_400, easing = LinearEasing)),
        label = "blink_clock"
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
                .testTag("aura-3x3-assistant-tile")
                .semantics {
                    contentDescription = "Aura assistant, ${auraEmotion.label.lowercase()} expression"
                    role = Role.Button
                    stateDescription = when {
                        needsApproval -> "Waiting for approval"
                        activeSubagents > 0 -> "Working with $activeSubagents subagents"
                        runState == "failed" || runState == "interrupted" -> "Task failed"
                        runState == "completed" -> "Task completed"
                        else -> when (mode) {
                            AuraPresenceMode.Listening -> "Listening, ${auraEmotion.label}"
                            AuraPresenceMode.Hearing -> "Hearing you"
                            AuraPresenceMode.Thinking -> "Thinking"
                            AuraPresenceMode.Focused -> "Ready, ${auraEmotion.label}"
                            AuraPresenceMode.Idle -> auraEmotion.label
                        }
                    }
                }
                .bounceClick(onClick = onActivate)
                .background(Color.Black, RoundedCornerShape(32.dp))
                .padding(28.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val active = isSpeaking || mode in setOf(
                    AuraPresenceMode.Listening,
                    AuraPresenceMode.Hearing,
                    AuraPresenceMode.Thinking
                )
                val voiceAmount = voiceLevel.coerceIn(0, 12) / 12f
                val wave = if (active) {
                    sin(phase) * (0.08f + bounce * 0.12f) + voiceAmount * 0.05f
                } else {
                    sin(phase * 0.45f) * bounce * 0.025f
                }
                val organicGazeX = sin(phase * 0.37f + 0.4f) * 0.026f +
                    sin(phase * 0.73f + 1.1f) * 0.012f
                val organicGazeY = sin(phase * 0.29f + 1.5f) * 0.018f
                val responsiveGazeX = (gazeX + organicGazeX).coerceIn(-0.45f, 0.45f)
                val responsiveGazeY = (gazeY + organicGazeY).coerceIn(-0.45f, 0.45f)
                val organicAsymmetry = asymmetry + sin(phase * 0.43f + 0.7f) * 0.014f
                val eyeBreath = 1f + sin(phase * 0.61f + 0.2f) * 0.016f
                val leftBlink = naturalBlink(blinkClock, 0.02f)
                val rightBlink = naturalBlink(blinkClock, 0.37f)
                val winkAmount = calculateWinkPulse(blinkClock, winkStrength)
                val eyeWidth = size.width * 0.23f
                val baseEyeHeight = size.height * 0.34f
                val runtimeScale = when {
                    needsApproval -> 0.62f + 0.08f * sin(phase)
                    runState == "failed" || runState == "interrupted" -> 0.42f
                    runState == "completed" && !isSpeaking -> 0.72f
                    else -> 1f
                }
                val leftEyeHeight = baseEyeHeight * openness * (1f + organicAsymmetry) * eyeBreath * leftBlink *
                    (1f + wave) * runtimeScale
                val rightEyeHeight = baseEyeHeight * openness * (1f - organicAsymmetry) * eyeBreath *
                    rightBlink * (1f - winkAmount) *
                    (1f + wave) * runtimeScale
                val leftEyeY = (size.height - leftEyeHeight) / 2f
                val rightEyeY = (size.height - rightEyeHeight) / 2f
                val gap = size.width * 0.14f
                val delegationShift = if (activeSubagents > 0) sin(phase * 0.65f) * size.width * 0.035f else 0f
                val leftX = size.width / 2f - gap / 2f - eyeWidth + delegationShift
                val rightX = size.width / 2f + gap / 2f + delegationShift
                drawAuraEye(
                    left = leftX,
                    top = leftEyeY,
                    width = eyeWidth,
                    height = leftEyeHeight,
                    rotation = -tiltDegrees,
                    gazeX = responsiveGazeX,
                    gazeY = responsiveGazeY,
                    pupilScale = pupilScale,
                    glow = glow,
                    browAngle = browAngle,
                    browDirection = 1f,
                )
                drawAuraEye(
                    left = rightX,
                    top = rightEyeY,
                    width = eyeWidth,
                    height = rightEyeHeight,
                    rotation = tiltDegrees,
                    gazeX = responsiveGazeX,
                    gazeY = responsiveGazeY,
                    pupilScale = pupilScale,
                    glow = glow,
                    browAngle = browAngle,
                    browDirection = -1f,
                )
                if (sparkle > 0f) {
                    val sparklePulse = 0.35f + 0.65f * ((sin(blinkClock * (2f * PI).toFloat() + phase * 0.2f) + 1f) / 2f)
                    val sparkleColor = Color.White.copy(alpha = sparkle * sparklePulse * 0.9f)
                    val sparkleRadius = size.width * (0.012f + sparkle * 0.008f)
                    drawCircle(sparkleColor, sparkleRadius, Offset(size.width * 0.27f, size.height * 0.16f))
                    drawCircle(sparkleColor, sparkleRadius * 0.72f, Offset(size.width * 0.73f, size.height * 0.2f))
                }
                if (active) {
                    val centerY = size.height * 0.88f
                    repeat(7) { index ->
                        val distance = kotlin.math.abs(index - 3)
                        val barHeight = size.height * (0.025f + (3 - distance) * 0.012f) *
                            (0.82f + 0.18f * sin(phase + index))
                        val x = size.width * 0.35f + index * size.width * 0.05f
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.82f),
                            topLeft = Offset(x, centerY - barHeight / 2f),
                            size = Size(size.width * 0.018f, barHeight),
                            cornerRadius = CornerRadius(size.width * 0.009f)
                        )
                    }
                }
                if (activeSubagents > 0 || runPhase == "delegating" || runPhase == "synthesizing") {
                    repeat(activeSubagents.coerceIn(1, 3)) { index ->
                        val indicatorX = size.width / 2f + (index - (activeSubagents.coerceIn(1, 3) - 1) / 2f) * size.width * 0.1f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.55f + 0.25f * sin(phase + index)),
                            radius = size.width * 0.014f,
                            center = Offset(indicatorX, size.height * 0.1f)
                        )
                    }
                }
            }
        }
        if (assistantText.isNotBlank()) {
            Text(
                text = assistantText.replace(Regex("^\\{[a-zA-Z0-9_-]+\\}\\s*"), ""),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("aura-assistant-speech")
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .glassCard(RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun naturalBlink(progress: Float, offset: Float): Float {
    val cycle = ((progress + offset) % 1f + 1f) % 1f
    return when {
        cycle < 0.04f -> 1f - (cycle / 0.04f) * 0.84f
        cycle < 0.09f -> 0.16f + ((cycle - 0.04f) / 0.05f) * 0.84f
        else -> 1f
    }
}

private fun calculateWinkPulse(progress: Float, strength: Float): Float {
    if (strength <= 0f) return 0f
    val cycle = ((progress + 0.58f) % 1f + 1f) % 1f
    return when {
        cycle < 0.05f -> strength * (cycle / 0.05f)
        cycle < 0.14f -> strength
        cycle < 0.2f -> strength * (1f - (cycle - 0.14f) / 0.06f)
        else -> 0f
    }
}

private fun DrawScope.drawAuraEye(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    rotation: Float,
    gazeX: Float,
    gazeY: Float,
    pupilScale: Float,
    glow: Float,
    browAngle: Float,
    browDirection: Float,
) {
    val center = Offset(left + width / 2f, top + height / 2f)
    rotate(rotation, center) {
        if (glow > 0f) {
            drawCircle(
                color = Color.White.copy(alpha = glow * 0.12f),
                radius = width * (0.38f + glow * 0.18f),
                center = center,
            )
        }
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(width, height.coerceAtLeast(1f)),
            cornerRadius = CornerRadius(width / 2f, width / 2f),
        )
        val pupilWidth = width * (0.16f + pupilScale * 0.2f)
        val pupilHeight = height.coerceAtLeast(1f) * (0.24f + pupilScale * 0.34f)
        val pupilCenter = Offset(
            x = center.x + gazeX * width * 0.22f,
            y = center.y + gazeY * height.coerceAtLeast(1f) * 0.22f,
        )
        drawOval(
            color = Color(0xFF101014),
            topLeft = Offset(
                pupilCenter.x - pupilWidth / 2f,
                pupilCenter.y - pupilHeight / 2f,
            ),
            size = Size(pupilWidth, pupilHeight),
        )
        if (glow > 0.2f) {
            drawCircle(
                color = Color.White.copy(alpha = glow * 0.8f),
                radius = width * 0.035f,
                center = Offset(
                    pupilCenter.x - pupilWidth * 0.2f,
                    pupilCenter.y - pupilHeight * 0.25f,
                ),
            )
        }
        val browY = top - width * 0.22f
        val browSlope = browAngle * width * 0.18f
        drawLine(
            color = Color.White.copy(alpha = 0.24f + kotlin.math.abs(browAngle) * 0.5f),
            start = Offset(left, browY - browSlope * browDirection),
            end = Offset(left + width, browY + browSlope * browDirection),
            strokeWidth = width * 0.07f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
fun AuraDynamicWidgetSection(
    widgets: List<AuraWidget>,
    onOpenSurface: (String) -> Unit = {},
    onAction: (String, String) -> Unit,
    onConfirm: (String, String) -> Unit,
    onCancelConfirmation: (String) -> Unit,
    onDismiss: (String) -> Unit
) {
    if (widgets.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("aura-dynamic-widget-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ACTIVE WORK",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(widgets, key = { it.id }) { widget ->
                AuraDynamicWidgetCard(
                    widget = widget,
                    onOpenSurface = onOpenSurface,
                    onAction = onAction,
                    onConfirm = onConfirm,
                    onCancelConfirmation = onCancelConfirmation,
                    onDismiss = onDismiss,
                    modifier = Modifier.fillParentMaxWidth(
                        if (widget.presentation == AuraWidgetPresentation.Compact) 0.78f else 0.94f
                    )
                )
            }
        }
    }
}

@Composable
private fun AuraDynamicWidgetCard(
    widget: AuraWidget,
    onOpenSurface: (String) -> Unit,
    onAction: (String, String) -> Unit,
    onConfirm: (String, String) -> Unit,
    onCancelConfirmation: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(
                min = if (widget.presentation == AuraWidgetPresentation.Compact) 150.dp else 210.dp
            )
            .testTag("aura-widget-${widget.id}")
            .glassCard(RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${widget.kind.wireValue.replace('_', ' ').uppercase()}  ·  ${widget.status.professionalLabel().uppercase()}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (widget.status != AuraWidgetStatus.Executing) {
                IconButton(onClick = { onDismiss(widget.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss ${widget.title}",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = widget.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(widget.message, style = MaterialTheme.typography.bodyMedium)
        widget.details.forEach { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        widget.lastError?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(2.dp))
        if (widget.presentation == AuraWidgetPresentation.Fullscreen && widget.content != null) {
            Button(
                onClick = { onOpenSurface(widget.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(if (widget.kind == com.aura.app.widgets.AuraWidgetKind.Report) "Open report" else "Open surface")
            }
        }
        when (widget.status) {
            AuraWidgetStatus.AwaitingConfirmation -> {
                val action = widget.actions.firstOrNull { it.id == widget.pendingActionId }
                Text(
                    "Confirm before Aura continues",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                action?.let {
                    Text(
                        text = actionDescription(it.type, it.payload),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { action?.let { onConfirm(widget.id, it.id) } },
                        modifier = Modifier.weight(1f),
                        enabled = action != null
                    ) {
                        Text(
                            action?.let { "Confirm ${it.label}" } ?: "Confirm",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { onCancelConfirmation(widget.id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
            }
            AuraWidgetStatus.Executing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Working…", fontWeight = FontWeight.Bold)
                }
            }
            AuraWidgetStatus.Succeeded -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Completed", fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                if (widget.actions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        widget.actions.forEachIndexed { index, action ->
                            if (index == 0) {
                                Button(
                                    onClick = { onAction(widget.id, action.id) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onSurface,
                                        contentColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Text(
                                        action.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onAction(widget.id, action.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        action.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun AuraWidgetStatus.professionalLabel(): String = when (this) {
    AuraWidgetStatus.Visible -> "Ready"
    AuraWidgetStatus.AwaitingConfirmation -> "Needs approval"
    AuraWidgetStatus.Executing -> "In progress"
    AuraWidgetStatus.Succeeded -> "Completed"
    AuraWidgetStatus.Failed -> "Needs attention"
    AuraWidgetStatus.Dismissed -> "Dismissed"
    AuraWidgetStatus.Expired -> "Expired"
}

@Composable
fun HostedAndroidWidgetSection(
    widgets: List<HostedAndroidWidget>,
    onAdd: () -> Unit,
    onResize: (HostedAndroidWidget, Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    createView: (Context, Int) -> View?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hosted-android-widget-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ANDROID WIDGETS",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onAdd, modifier = Modifier.testTag("add-android-widget")) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add")
            }
        }
        if (widgets.isEmpty()) {
            Text(
                text = "Add widgets from installed apps. Aura keeps their placement and size on this home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(widgets, key = { it.appWidgetId }) { widget ->
                    var confirmRemoval by remember(widget.appWidgetId) { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .width((widget.spanX * 72).coerceIn(216, 320).dp)
                            .glassCard(RoundedCornerShape(24.dp))
                            .padding(8.dp)
                    ) {
                        AndroidView(
                            factory = { context ->
                                createView(context, widget.appWidgetId) ?: TextView(context).apply {
                                    text = context.getString(R.string.android_widget_unavailable)
                                    gravity = android.view.Gravity.CENTER
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((widget.spanY * 72).coerceIn(144, 360).dp)
                                .testTag("hosted-widget-${widget.appWidgetId}")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Width ${widget.spanX}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(
                                onClick = { onResize(widget, widget.spanX - 1, widget.spanY) },
                                enabled = widget.spanX > 1
                            ) {
                                Icon(
                                    Icons.Rounded.Remove,
                                    contentDescription = "Make widget narrower"
                                )
                            }
                            IconButton(
                                onClick = { onResize(widget, widget.spanX + 1, widget.spanY) },
                                enabled = widget.spanX < 4
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "Make widget wider"
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Height ${widget.spanY}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(
                                onClick = { onResize(widget, widget.spanX, widget.spanY - 1) },
                                enabled = widget.spanY > 1
                            ) {
                                Icon(
                                    Icons.Rounded.Remove,
                                    contentDescription = "Make widget shorter"
                                )
                            }
                            IconButton(
                                onClick = { onResize(widget, widget.spanX, widget.spanY + 1) },
                                enabled = widget.spanY < 6
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "Make widget taller"
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { confirmRemoval = true }) {
                                Text("Remove")
                            }
                        }
                    }
                    if (confirmRemoval) {
                        AlertDialog(
                            onDismissRequest = { confirmRemoval = false },
                            title = { Text("Remove Android widget?") },
                            text = {
                                Text("This removes the widget from Aura. You can add it again later.")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        confirmRemoval = false
                                        onRemove(widget.appWidgetId)
                                    }
                                ) {
                                    Text("Remove")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmRemoval = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun actionDescription(
    type: AuraWidgetActionType,
    payload: Map<String, String>
): String = when (type) {
    AuraWidgetActionType.AssistantMessage ->
        "Aura will ask: “${payload["message"].orEmpty()}”"
    AuraWidgetActionType.OpenApp ->
        "Aura will open ${payload["package_name"] ?: payload["app_query"].orEmpty()}."
    AuraWidgetActionType.Dismiss ->
        "Aura will dismiss this widget."
}
