package com.aura.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.aura.app.assistant.MessageRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Route(val title: String) {
    Home("Aura"),
    Apps("Apps"),
    Assistant("Assistant"),
    Tasks("Tasks"),
    Memory("Memory"),
    Settings("Settings")
}

@Composable
fun AuraLauncherApp(
    container: AppContainer,
    onRequestVoicePermissions: () -> Unit,
    onOpenHomeSettings: () -> Unit
) {
    val viewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showHomePrompt by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
    }

    LaunchedEffect(state.session.homeSettingsPrompted) {
        if (!state.session.homeSettingsPrompted) {
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val current = navController.currentBackStackEntryAsState().value?.destination?.route
            NavigationBar {
                listOf(Route.Home, Route.Apps, Route.Assistant, Route.Tasks, Route.Memory, Route.Settings)
                    .forEach { route ->
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
                            label = { Text(route.title) }
                        )
                    }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home.name,
            modifier = Modifier.padding(padding)
        ) {
            composable(Route.Home.name) {
                HomeScreen(
                    state = state,
                    onAssistantInput = viewModel::setAssistantInput,
                    onSend = viewModel::sendAssistantMessage,
                    onTalk = {
                        onRequestVoicePermissions()
                        viewModel.startPushToTalk()
                    },
                    onStopVoice = viewModel::stopVoice,
                    onOpenApps = { navController.navigate(Route.Apps.name) },
                    onOpenAssistant = { navController.navigate(Route.Assistant.name) },
                    onLaunchApp = { app ->
                        context.startActivity(viewModel.launchIntent(app))
                    }
                )
            }
            composable(Route.Apps.name) {
                AppsScreen(
                    state = state,
                    onQuery = viewModel::setAppQuery,
                    onLaunchApp = { app ->
                        context.startActivity(viewModel.launchIntent(app))
                    },
                    onRefresh = viewModel::refreshApps
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
                TasksScreen(state = state, onAddTodo = viewModel::addTodo)
            }
            composable(Route.Memory.name) {
                MemoryScreen(state = state, onAddMemory = viewModel::addMemory)
            }
            composable(Route.Settings.name) {
                SettingsScreen(
                    state = state,
                    onRequestVoicePermissions = onRequestVoicePermissions,
                    onOpenHomeSettings = {
                        showHomePrompt = true
                    },
                    onBackgroundListening = viewModel::setBackgroundListening
                )
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
    onOpenApps: () -> Unit,
    onOpenAssistant: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit
) {
    ScreenShell {
        Text(
            text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Aura is local-first and ready.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        AssistantComposer(state.assistantInput, onAssistantInput, onSend)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = if (state.status.running) onStopVoice else onTalk) {
                Icon(if (state.status.running) Icons.Outlined.Stop else Icons.Outlined.Mic, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.status.running) "Stop" else "Talk")
            }
            FilledTonalButton(onClick = onOpenAssistant) {
                Icon(Icons.Outlined.GraphicEq, null)
                Spacer(Modifier.width(8.dp))
                Text("Chat")
            }
            FilledTonalButton(onClick = onOpenApps) {
                Icon(Icons.Outlined.Apps, null)
                Spacer(Modifier.width(8.dp))
                Text("Apps")
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Open tasks", state.openTodos.toString(), Modifier.weight(1f))
            StatTile("Memories", state.memories.size.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Text("Pinned apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            state.pinnedApps.forEach { app ->
                AppInitial(app, Modifier.weight(1f), onLaunchApp)
            }
        }
    }
}

@Composable
private fun AppsScreen(
    state: LauncherUiState,
    onQuery: (String) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onRefresh: () -> Unit
) {
    ScreenShell {
        Header("Apps", "Search and open anything installed.")
        OutlinedTextField(
            value = state.appQuery,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Search apps") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRefresh) { Text("Refresh app list") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.filteredApps, key = { it.componentName.flattenToString() }) { app ->
                AppRow(app, onLaunchApp)
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
    ScreenShell {
        Header("Assistant", "Local assistant responses are active.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(state.messages) { message ->
                val isUser = message.role == MessageRole.User
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 1f)
                ) {
                    Text(message.text, Modifier.padding(14.dp))
                }
            }
        }
        AssistantComposer(state.assistantInput, onAssistantInput, onSend)
    }
}

@Composable
private fun TasksScreen(state: LauncherUiState, onAddTodo: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    ScreenShell {
        Header("Tasks", "${state.openTodos} still open on this device.")
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Add a task") },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            onAddTodo(title)
            title = ""
        }) { Text("Add task") }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.todos, key = { it.id }) { todo ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null)
                        Spacer(Modifier.width(10.dp))
                        Text(todo.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryScreen(state: LauncherUiState, onAddMemory: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    ScreenShell {
        Header("Memory", "${state.memories.size} notes saved locally.")
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Title") },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What should Aura remember?") }
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            onAddMemory(title, content)
            title = ""
            content = ""
        }) { Text("Save memory") }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.memories, key = { it.id }) { memory ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(memory.title, fontWeight = FontWeight.SemiBold)
                        Text(memory.content, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onBackgroundListening: (Boolean) -> Unit
) {
    ScreenShell {
        Header("Settings", "Launcher and voice controls.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Local-first mode", fontWeight = FontWeight.SemiBold)
                Text(
                    "Tasks, memories, and assistant context stay on this device right now.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsRow("Default launcher", "Open Android Home app settings.", onOpenHomeSettings)
        SettingsRow("Voice permissions", "Microphone and notification access.", onRequestVoicePermissions)
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Background listening", fontWeight = FontWeight.SemiBold)
                    Text("Opt-in foreground service.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.session.backgroundListeningEnabled,
                    onCheckedChange = onBackgroundListening
                )
            }
        }
    }
}

@Composable
private fun ScreenShell(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
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
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun AssistantComposer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask Aura") },
            singleLine = true
        )
        Spacer(Modifier.width(10.dp))
        Button(onClick = onSend, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(56.dp)) {
            Icon(Icons.Outlined.Search, contentDescription = "Send")
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(app.label.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AppRow(app: AppInfo, onLaunchApp: (AppInfo) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLaunchApp(app) }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(app.label, fontWeight = FontWeight.SemiBold)
                Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun routeIcon(route: Route) = when (route) {
    Route.Home -> Icons.Outlined.Home
    Route.Apps -> Icons.Outlined.Apps
    Route.Assistant -> Icons.Outlined.GraphicEq
    Route.Tasks -> Icons.Outlined.CheckCircle
    Route.Memory -> Icons.Outlined.Layers
    Route.Settings -> Icons.Outlined.Settings
}
