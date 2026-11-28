package com.jarvis.assistant.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ChatMessage
import com.jarvis.assistant.JarvisUiState
import com.jarvis.assistant.MemoryEntity
import com.jarvis.assistant.ToolEvent
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true) {
    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = singleLine)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Visualizer(active: Boolean, listening: Boolean) {
    val transition = rememberInfiniteTransition(label = "jarvis")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 2600 else 7000)), label = "orbit")
    val pulse by transition.animateFloat(0.85f, 1.12f, infiniteRepeatable(tween(if (listening) 700 else 1800)), label = "pulse")
    Canvas(Modifier.size(220.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val base = MaterialTheme.colorScheme.primary
        drawCircle(color = base.copy(alpha = 0.08f), radius = 96f * pulse, center = Offset(cx, cy))
        drawCircle(color = base.copy(alpha = 0.18f), radius = 72f * pulse, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        drawCircle(color = base.copy(alpha = 0.35f), radius = 50f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        for (i in 0 until 3) {
            val a = (angle + i * 120f) * Math.PI.toFloat() / 180f
            drawArc(
                color = base.copy(alpha = 0.9f - i * 0.2f),
                startAngle = angle + i * 120f,
                sweepAngle = 58f,
                useCenter = false,
                topLeft = Offset(cx - 88f, cy - 88f),
                size = androidx.compose.ui.geometry.Size(176f, 176f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = StrokeCap.Round)
            )
            val dot = Offset(cx + cos(a) * 88f, cy + sin(a) * 88f)
            drawCircle(base, 6f, dot)
        }
        drawCircle(base.copy(alpha = 0.75f), 14f, Offset(cx, cy))
    }
}

@Composable
fun DashboardScreen(
    ui: JarvisUiState,
    onMic: () -> Unit,
    onSend: (String) -> Unit,
    onAskScreen: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth > 620.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Visualizer(ui.busy || ui.listening, ui.listening)
                }
                Column(Modifier.weight(1.2f).verticalScroll(scroll)) { DashboardBody(ui, text, { text = it }, onMic, onSend, onAskScreen, onNavigate) }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                Visualizer(ui.busy || ui.listening, ui.listening)
                DashboardBody(ui, text, { text = it }, onMic, onSend, onAskScreen, onNavigate)
            }
        }
    }
}

@Composable
private fun DashboardBody(
    ui: JarvisUiState,
    text: String,
    onText: (String) -> Unit,
    onMic: () -> Unit,
    onSend: (String) -> Unit,
    onAskScreen: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Text("J.A.R.V.I.S.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(ui.status, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
    Text(ui.model, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text, onValueChange = onText,
            placeholder = { Text("Speak or type a command") },
            modifier = Modifier.weight(1f), singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        FloatingActionButton(onClick = onMic, containerColor = if (ui.listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) {
            androidx.compose.material3.Icon(Icons.Filled.Mic, contentDescription = "Microphone")
        }
        Spacer(Modifier.width(8.dp))
        FloatingActionButton(onClick = { if (text.isNotBlank()) { onSend(text); onText("") } }) {
            androidx.compose.material3.Icon(Icons.Filled.Send, contentDescription = "Send")
        }
    }
    if (ui.partial.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text("Listening: ${ui.partial}", color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(18.dp))
    Text("Quick commands", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf(
            "What is on my screen?" to onAskScreen,
            "Summarize my saved notes" to { onSend("Summarize my saved notes") },
            "Check my reminders" to { onNavigate("reminders") },
            "Research Android development" to { onSend("Research the latest Android development technologies and summarize everything") }
        ).forEach { (label, action) ->
            Button(onClick = action, modifier = Modifier.padding(end = 8.dp)) { Text(label) }
        }
    }
    Spacer(Modifier.height(18.dp))
    GlassCard {
        Text("Recent activity", style = MaterialTheme.typography.titleMedium)
        if (ui.activity.isEmpty()) Text("No activity yet. Ask JARVIS something.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else ui.activity.take(6).forEach { Text("${it.type}: ${it.detail.take(70)}", fontSize = 13.sp) }
    }
    if (ui.error.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(ui.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    }
}

@Composable
fun ChatScreen(
    ui: JarvisUiState,
    onSend: (String, Boolean) -> Unit,
    onMic: () -> Unit,
    onStopVoice: () -> Unit,
    onClear: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var agent by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(ui.messages.size, ui.streaming.length) {
        if (ui.messages.isNotEmpty()) listState.scrollToItem(ui.messages.size)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Conversations", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear") }
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(ui.messages) { msg -> MessageBubble(msg) }
            if (ui.streaming.isNotBlank()) item { MessageBubble(ChatMessage("assistant", ui.streaming + " ▌")) }
            if (ui.toolEvents.isNotEmpty()) item {
                GlassCard {
                    Text("Execution timeline", style = MaterialTheme.typography.titleMedium)
                    ui.toolEvents.forEach { e -> ToolRow(e) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message JARVIS") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = { agent = !agent }) { Text(if (agent) "Agent" else "Chat") }
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(onClick = { if (ui.listening) onStopVoice() else onMic() }) {
                androidx.compose.material3.Icon(if (ui.listening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = "Voice")
            }
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(onClick = { if (text.isNotBlank()) { onSend(text, agent); text = "" } }) {
                androidx.compose.material3.Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val mine = msg.role == "user"
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(if (mine) "You" else "J.A.R.V.I.S.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(msg.content)
            }
        }
    }
}

@Composable
private fun ToolRow(e: ToolEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(when (e.status) { "running" -> "◐"; "done" -> "✓"; else -> "✕" }, color = when (e.status) {
            "running" -> JarvisGold; "done" -> JarvisGreen; else -> JarvisRed
        })
        Spacer(Modifier.width(8.dp))
        Text("${e.step}: ${e.detail}", fontSize = 13.sp)
    }
}

@Composable
fun MemoryScreen(
    ui: JarvisUiState,
    onSave: (String, String, String, String, Int) -> Unit,
    onDelete: (MemoryEntity) -> Unit,
    onSearch: (String) -> Unit,
    onExport: () -> String,
    onImport: (String) -> Unit
) {
    var category by remember { mutableStateOf("Project") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var importance by remember { mutableStateOf(3f) }
    var query by remember { mutableStateOf("") }
    var exported by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Memory core")
        Field("Category", category, { category = it })
        Field("Title", title, { title = it })
        Field("Content", content, { content = it }, false)
        Field("Tags, comma separated", tags, { tags = it })
        Text("Importance: ${importance.toInt()}")
        Slider(importance, { importance = it }, valueRange = 1f..10f)
        Button(onClick = {
            onSave(category, title, content, tags, importance.toInt())
            title = ""; content = ""; tags = ""
        }) { Text("Save memory") }
        Spacer(Modifier.height(16.dp))
        Field("Search stored memory", query, { query = it })
        Row {
            Button(onClick = { onSearch(query) }) { Text("Search") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { exported = onExport() }) { Text("Export JSON") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onImport(exported) }) { Text("Import JSON") }
        }
        if (exported.isNotBlank()) OutlinedTextField(exported, { exported = it }, modifier = Modifier.fillMaxWidth().height(100.dp))
        Spacer(Modifier.height(10.dp))
        ui.memories.forEach { m ->
            GlassCard {
                Text(m.title, fontWeight = FontWeight.SemiBold)
                Text("${m.category} · importance ${m.importance}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(m.content.take(260), fontSize = 13.sp)
                TextButton(onClick = { onDelete(m) }) { Text("Delete") }
            }
        }
    }
}

@Composable
fun ScreenAwarenessScreen(
    ui: JarvisUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAsk: (String) -> Unit,
    frame: Bitmap?,
    onEnable: () -> Unit
) {
    var question by remember { mutableStateOf("What is happening on my screen?") }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Screen awareness")
        Text("JARVIS only captures after the Android consent dialog and shows a persistent indicator.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = onStart, enabled = !ui.screenActive) { Text("Start screen sharing") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onStop, enabled = ui.screenActive) { Text("Stop") }
        }
        Spacer(Modifier.height(12.dp))
        if (frame != null) Image(frame.asImageBitmap(), contentDescription = "Latest screen frame", modifier = Modifier.fillMaxWidth().height(220.dp))
        else Text("No frame captured yet.")
        Spacer(Modifier.height(12.dp))
        Field("Question about the screen", question, { question = it })
        Button(onClick = { onAsk(question) }, enabled = ui.screenActive) { Text("Analyse screen") }
        Spacer(Modifier.height(16.dp))
        GlassCard {
            Text("Application interaction", style = MaterialTheme.typography.titleMedium)
            Text("Status: ${if (ui.interactionEnabled) "enabled" else "not enabled"}")
            TextButton(onClick = onEnable) { Text("Enable in Android settings") }
        }
    }
}

@Composable
fun TasksScreen(ui: JarvisUiState, onRun: (String, Boolean) -> Unit) {
    var goal by remember { mutableStateOf("Research the latest Android development technologies and summarize everything") }
    var agent by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Task manager")
        Field("Objective", goal, { goal = it }, false)
        Row {
            Button(onClick = { agent = !agent }) { Text(if (agent) "Agent mode" else "Single reply") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (goal.isNotBlank()) onRun(goal, agent) }) { Text("Run") }
        }
        if (ui.toolEvents.isNotEmpty()) GlassCard {
            Text("Live progress", style = MaterialTheme.typography.titleMedium)
            ui.toolEvents.forEach { ToolRow(it) }
        }
        ui.tasks.forEach { t ->
            GlassCard {
                Text(t.title, fontWeight = FontWeight.SemiBold)
                Text("${t.status} · ${t.progress}%", fontSize = 13.sp)
                LinearProgressIndicator(t.progress / 100f, Modifier.fillMaxWidth().padding(vertical = 6.dp))
                if (t.result.isNotBlank()) Text(t.result.take(300), fontSize = 13.sp)
                if (t.error.isNotBlank()) Text(t.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun RemindersScreen(
    ui: JarvisUiState,
    onAdd: (String, Long, Long) -> Unit,
    onToggle: (com.jarvis.assistant.ReminderEntity) -> Unit,
    onDelete: (com.jarvis.assistant.ReminderEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("1") }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Reminders")
        Field("Reminder", title, { title = it })
        Field("In hours", hours, { hours = it })
        Button(onClick = {
            val h = hours.toLongOrNull() ?: 1L
            onAdd(title, System.currentTimeMillis() + h * 3600000L, 0)
            title = ""
        }) { Text("Add reminder") }
        ui.reminders.forEach { r ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(r.enabled, { onToggle(r) })
                    Text(r.title, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDelete(r) }) { Text("Delete") }
                }
                Text(android.text.format.DateFormat.format("dd MMM HH:mm", r.dueAt).toString(), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ResearchScreen(ui: JarvisUiState, onRun: (String) -> Unit, onSave: (String) -> Unit) {
    var query by remember { mutableStateOf("latest Android development technologies") }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Research")
        Field("Research question", query, { query = it }, false)
        Button(onClick = { onRun("Research $query and cite the sources you opened") }) { Text("Research") }
        ui.messages.lastOrNull { it.role == "assistant" }?.let {
            Spacer(Modifier.height(12.dp))
            Text(it.content.take(1800), fontSize = 14.sp)
            Button(onClick = { onSave(it.content) }) { Text("Save report to memory") }
        }
    }
}

@Composable
fun FilesScreen(onOpen: () -> Unit, onAnalyze: (String, String, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Files")
        Text("Select a document with Android's file picker. Nothing is scanned silently.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpen) { Text("Choose PDF, text, image, JSON or CSV") }
        Spacer(Modifier.height(12.dp))
        Text("Supported: text is summarised, images are sent to a vision-capable model when configured.", fontSize = 13.sp)
    }
}

@Composable
fun ModelCenterScreen(ui: JarvisUiState, vm: com.jarvis.assistant.JarvisViewModel) {
    var name by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("fast") }
    var roleExpanded by remember { mutableStateOf(false) }
    var vision by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Model center")
        Field("Provider name", name, { name = it })
        Field("OpenAI-compatible base URL", base, { base = it })
        Field("API key", key, { key = it })
        Field("Model ID", model, { model = it })
        Text("Routing role: $role")
        Row {
            listOf("fast", "reasoning", "coding", "vision", "speech").forEach {
                Button(onClick = { role = it }, modifier = Modifier.padding(end = 4.dp)) { Text(it) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(vision, { vision = it })
            Text("Supports vision")
        }
        Button(onClick = {
            vm.addProvider(name, base, key, model, role, 2048, 0.7, true, vision, false, "{}", 60000)
            name = ""; key = ""
        }) { Text("Add provider") }
        Spacer(Modifier.height(12.dp))
        ui.providers.forEach { p ->
            GlassCard {
                Text(p.name, fontWeight = FontWeight.SemiBold)
                Text("${p.modelId} · ${p.role}", fontSize = 13.sp)
                Text("Latency ${p.latencyMs} ms", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.lastError.isNotBlank()) Text(p.lastError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Row {
                    TextButton(onClick = { vm.testProvider(p.id) }) { Text("Test") }
                    TextButton(onClick = { vm.discoverModels(p.id) }) { Text("Discover") }
                    TextButton(onClick = { vm.deleteProvider(p) }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
fun AutomationsScreen(ui: JarvisUiState, onAdd: (String, String, String, String) -> Unit,
                       onToggle: (com.jarvis.assistant.AutomationEntity) -> Unit,
                       onDelete: (com.jarvis.assistant.AutomationEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("when I open JARVIS") }
    var condition by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("show my unfinished projects") }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Automations")
        Field("Name", name, { name = it })
        Field("Trigger", trigger, { trigger = it })
        Field("Condition, optional", condition, { condition = it })
        Field("Action", action, { action = it })
        Button(onClick = { onAdd(name, trigger, condition, action); name = "" }) { Text("Add routine") }
        ui.automations.forEach { a ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(a.enabled, { onToggle(a) })
                    Text(a.name, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDelete(a) }) { Text("Delete") }
                }
                Text("When ${a.trigger}: ${a.action}", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SettingsScreen(ui: JarvisUiState, vm: com.jarvis.assistant.JarvisViewModel, onAccessibility: () -> Unit) {
    var personality by remember { mutableStateOf(ui.settings.personality) }
    var voiceId by remember { mutableStateOf(ui.settings.voiceId) }
    var language by remember { mutableStateOf(ui.settings.language) }
    var search by remember { mutableStateOf(ui.settings.searchTemplate) }
    LaunchedEffect(Unit) { vm.refreshShizuku() }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Settings")
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark mode", modifier = Modifier.weight(1f))
                Checkbox(ui.settings.darkMode, { vm.setTheme(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Low resource mode", modifier = Modifier.weight(1f))
                Checkbox(ui.settings.lowResource, { vm.setLowResource(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-confirm tools", modifier = Modifier.weight(1f))
                Checkbox(ui.settings.autoConfirm, { vm.setAutoConfirm(it) })
            }
        }
        GlassCard {
            Field("Personality", personality, { personality = it }, false)
            Button(onClick = { vm.setPersonality(personality) }) { Text("Save personality") }
            Field("Voice ID", voiceId, { voiceId = it })
            Button(onClick = { vm.setVoiceId(voiceId) }) { Text("Use voice") }
            TextAvailableVoices(vm)
            Field("Language tag", language, { language = it })
            Button(onClick = { vm.setLanguage(language) }) { Text("Set language") }
            Text("Speech rate: ${ui.settings.speechRate}")
            Slider(ui.settings.speechRate, { vm.setSpeechRate(it) }, valueRange = 0.3f..3f)
        }
        GlassCard {
            Field("Search URL template", search, { search = it })
            Button(onClick = { vm.setSearchTemplate(search) }) { Text("Save search URL") }
            Text("Use {query} as the placeholder.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlassCard {
            Text("Permissions and capabilities", style = MaterialTheme.typography.titleMedium)
            Text("Microphone: ${if (ui.permissions) "granted" else "not granted"}")
            TextButton(onClick = onAccessibility) { Text("Configure accessibility interaction") }
            TextButton(onClick = { vm.startWake() }) { Text("Start Hey Jarvis") }
            TextButton(onClick = { vm.stopWake() }) { Text("Stop Hey Jarvis") }
            Text("Shizuku: ${ui.shizuku.label}")
            Text("Status: ${ui.status}", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TextAvailableVoices(vm: com.jarvis.assistant.JarvisViewModel) {
    val voices = remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) { voices.value = vm.voiceVoiceNames() }
    if (voices.value.isNotEmpty()) {
        Text("Available voices:", fontSize = 13.sp)
        voices.value.take(6).forEach { voice ->
            TextButton(onClick = { vm.setVoiceId(voice) }) { Text(voice) }
        }
    }
}
