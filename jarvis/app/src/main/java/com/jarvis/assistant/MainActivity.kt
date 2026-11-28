package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.registerForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.services.NotificationHelper
import com.jarvis.assistant.services.ScreenCaptureService
import com.jarvis.assistant.services.StopActionReceiver
import com.jarvis.assistant.ui.AutomationsScreen
import com.jarvis.assistant.ui.ChatScreen
import com.jarvis.assistant.ui.DashboardScreen
import com.jarvis.assistant.ui.FilesScreen
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.MemoryScreen
import com.jarvis.assistant.ui.ModelCenterScreen
import com.jarvis.assistant.ui.RemindersScreen
import com.jarvis.assistant.ui.ResearchScreen
import com.jarvis.assistant.ui.ScreenAwarenessScreen
import com.jarvis.assistant.ui.SettingsScreen
import com.jarvis.assistant.ui.TasksScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: JarvisViewModel by viewModels()
    private lateinit var micLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var notifLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var projectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var documentLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private val stopReceiver = StopActionReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.setMicPermission(granted)
            if (!granted) vm.setErrorForUi("Microphone permission is needed for voice input.")
        }
        notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) vm.startScreenCapture(result.data!!)
        }
        documentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val (name, content) = vm.readDocument(uri)
                val image = contentResolver.getType(uri)?.startsWith("image/") == true
                vm.analyzeDocument(name, content, image)
            }
        }
        ContextCompat.registerReceiver(this, stopReceiver,
            IntentFilter().apply {
                addAction(NotificationHelper.ACTION_STOP_VOICE)
                addAction(NotificationHelper.ACTION_STOP_SCREEN)
                addAction(NotificationHelper.ACTION_STOP_WAKE)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)

        vm.setMicPermission(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val ui by vm.ui.collectAsState()
            JarvisTheme(dark = ui.settings.darkMode) {
                JarvisRoot(
                    ui = ui, vm = vm,
                    onMic = {
                        LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            lifecycleScope.launch { vm.startVoice() }
                        } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onProjection = {
                        val mpm = getSystemService(MediaProjectionManager::class.java)
                        projectionLauncher.launch(mpm.createScreenCaptureIntent())
                    },
                    onDocument = { documentLauncher.launch(arrayOf("*/*")) }
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisRoot(
    ui: JarvisUiState,
    vm: JarvisViewModel,
    onMic: () -> Unit,
    onProjection: () -> Unit,
    onDocument: () -> Unit
) {
    var page by remember { mutableStateOf("dashboard") }
    var drawerOpen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pages = listOf(
        "dashboard" to "Dashboard", "conversation" to "Conversations", "memory" to "Memory core",
        "screen" to "Screen awareness", "tasks" to "Task manager", "reminders" to "Reminders",
        "research" to "Research", "files" to "Files", "providers" to "Model center",
        "automations" to "Automations", "settings" to "Settings"
    )
    BackHandler {
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        else if (page != "dashboard") page = "dashboard"
    }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                pages.forEach { (id, label) ->
                    TextButton(onClick = { page = id; scope.launch { drawerState.close() } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Text(label)
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.systemBarsPadding(),
            topBar = {
                TopAppBar(
                    title = { Text(pages.firstOrNull { it.first == page }?.second ?: "J.A.R.V.I.S.") },
                    navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, contentDescription = "Menu") } },
                    actions = { Text(ui.status, modifier = Modifier.padding(end = 12.dp)) }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = page == "dashboard", onClick = { page = "dashboard" },
                        icon = {}, label = { Text("Home") })
                    NavigationBarItem(selected = page == "conversation", onClick = { page = "conversation" },
                        icon = {}, label = { Text("Chat") })
                    NavigationBarItem(selected = page == "tasks", onClick = { page = "tasks" },
                        icon = {}, label = { Text("Work") })
                    NavigationBarItem(selected = false, onClick = openDrawer,
                        icon = {}, label = { Text("More") })
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding)) {
                when (page) {
                    "dashboard" -> DashboardScreen(ui, onMic,
                        { text, agent -> vm.send(text, agent = agent) },
                        { vm.askAboutScreen("What is happening on my screen?") },
                        { page = it })
                    "conversation" -> ChatScreen(ui,
                        { text, agent -> vm.send(text, agent = agent) },
                        onMic, { vm.stopVoice() }) { vm.clearConversation() }
                    "memory" -> MemoryScreen(ui,
                        { c, t, x, tags, imp -> vm.saveMemory(c, t, x, tags, imp) },
                        { vm.deleteMemory(it) }, { vm.searchMemory(it) },
                        { vm.exportMemories() }, { vm.importMemories(it) })
                    "screen" -> ScreenAwarenessScreen(ui, onProjection, { vm.stopScreenCapture() },
                        { vm.askAboutScreen(it) }, ScreenCaptureService.latestFrame(),
                        { vm.openAccessibilitySettings(androidx.compose.ui.platform.LocalContext.current) })
                    "tasks" -> TasksScreen(ui) { goal, agent -> vm.send(goal, agent = agent) }
                    "reminders" -> RemindersScreen(ui,
                        { title, due, repeat -> vm.addReminder(title, due, repeat) },
                        { vm.toggleReminder(it) }, { vm.deleteReminder(it) })
                    "research" -> ResearchScreen(ui, { vm.send(it, agent = true) }) { vm.saveMemory("Research", "Research report", it, "research", 4) }
                    "files" -> FilesScreen(onDocument) { name, content, image -> vm.analyzeDocument(name, content, image) }
                    "providers" -> ModelCenterScreen(ui, vm)
                    "automations" -> AutomationsScreen(ui,
                        { n, t, c, a -> vm.addAutomation(n, t, c, a) },
                        { vm.toggleAutomation(it) }, { vm.deleteAutomation(it) })
                    "settings" -> SettingsScreen(ui, vm, { vm.openAccessibilitySettings(androidx.compose.ui.platform.LocalContext.current) })
                }
                if (ui.error.isNotBlank() && !ui.status.startsWith("Confirmation")) {
                    androidx.compose.material3.Snackbar { Text(ui.error) }
                    TextButton(onClick = { vm.clearError() }) { Text("Dismiss") }
                }
                if (ui.status.startsWith("Confirmation required")) {
                    AlertDialog(
                        onDismissRequest = { vm.confirmPending(false) },
                        title = { Text("Confirm action") },
                        text = { Text(ui.status.removePrefix("Confirmation required: ")) },
                        confirmButton = { TextButton(onClick = { vm.confirmPending(true) }) { Text("Allow") } },
                        dismissButton = { TextButton(onClick = { vm.confirmPending(false) }) { Text("Deny") } }
                    )
                }
            }
        }
    }
}
