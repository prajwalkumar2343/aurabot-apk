package com.aura.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun AuraHomeHeader(
    isWorking: Boolean,
    activeSubagents: Int,
    needsApproval: Boolean,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val locale = Locale.getDefault()
    val time = remember(now, locale) { android.text.format.DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now, locale) { SimpleDateFormat("EEEE, d MMMM", locale).format(Date(now)) }
    val status = when {
        needsApproval -> "Approval required"
        activeSubagents > 0 -> "Working with $activeSubagents ${if (activeSubagents == 1) "agent" else "agents"}"
        isWorking -> "Working"
        else -> "Ready"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("aura-home-header"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = time,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "$date  ·  $status",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(
                onClick = onOpenApps,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("open-app-drawer")
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = "Open apps")
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("open-aura-settings")
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Open Aura settings")
            }
        }
    }
}
