package com.aura.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.app.miniapps.MiniAppWidgetSnapshot

@Composable
fun MiniAppHomeWidgetSection(
    widgets: List<MiniAppWidgetSnapshot>,
    unavailableCount: Int,
    onOpenMiniApp: (String) -> Unit,
    onRunAction: (String, String) -> Unit
) {
    if (widgets.isEmpty() && unavailableCount == 0) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mini-app-widget-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "YOUR WIDGETS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (unavailableCount == 0) "${widgets.size} READY" else "$unavailableCount UNAVAILABLE",
                style = MaterialTheme.typography.labelSmall,
                color = if (unavailableCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = if (unavailableCount == 0) Modifier else Modifier.testTag("mini-app-widget-load-warning")
            )
        }
        if (widgets.isEmpty()) {
            Text(
                text = "Aura could not safely load the installed mini-app widgets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 20.dp)
            ) {
                items(widgets, key = { it.bundle.id }) { state ->
                    MiniAppHomeWidgetCard(state, onOpenMiniApp, onRunAction)
                }
            }
        }
    }
}

@Composable
private fun MiniAppHomeWidgetCard(
    state: MiniAppWidgetSnapshot,
    onOpenMiniApp: (String) -> Unit,
    onRunAction: (String, String) -> Unit
) {
    val bundle = state.bundle
    val widget = bundle.widget ?: return
    val primary = parseMiniAppColor(bundle.theme.primary, MaterialTheme.colorScheme.primary)
    val metric = widgetMetric(widget.metric, state)
    val actions = widget.actionIds.mapNotNull { actionId ->
        bundle.actions.firstOrNull { it.id == actionId }?.let { action -> action.id to actionLabel(action.id) }
    }
    Column(
        modifier = Modifier
            .width(280.dp)
            .height(150.dp)
            .testTag("mini-app-widget-${bundle.id}")
            .glassCard(shape = RoundedCornerShape(24.dp))
            .bounceClick { onOpenMiniApp(bundle.id) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(bundle.icon.value.take(1).uppercase(), color = primary, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(widget.title, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    widget.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Open ${bundle.metadata.name}", tint = primary)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(metric.first.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = primary)
            Spacer(Modifier.width(8.dp))
            Text(metric.second, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (widget.type == "progress" && widget.goal != null) {
            LinearProgressIndicator(
                progress = { (metric.first.toFloat() / widget.goal.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mini-app-widget-progress-${bundle.id}"),
                color = primary,
                trackColor = primary.copy(alpha = 0.12f)
            )
        } else if (widget.type == "quick_actions" && actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (id, label) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mini-app-widget-action-${bundle.id}-${id}")
                            .bounceClick { onRunAction(bundle.id, id) }
                            .clip(RoundedCornerShape(50))
                            .background(primary.copy(alpha = 0.1f))
                            .border(1.dp, primary.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun widgetMetric(metric: String, snapshot: MiniAppWidgetSnapshot): Pair<Long, String> = when (metric) {
    "today_count" -> snapshot.todayCount to "today"
    "weekly_count" -> snapshot.weeklyCount to "this week"
    "streak" -> snapshot.streak.toLong() to "day streak"
    else -> snapshot.totalCount to "total"
}

private fun actionLabel(id: String): String =
    id.replace('_', ' ').split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
