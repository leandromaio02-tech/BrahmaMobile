package com.brahma.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brahma.mobile.brain.Brain
import com.brahma.mobile.util.Settings
import com.brahma.mobile.voice.VoiceManager

@Composable
fun MainScreen(
    settings: Settings,
    brain: Brain,
    voice: VoiceManager,
    briefing: String,
    modelRefresh: Int,
    onMicPermission: () -> Unit,
    onOverlayPermission: () -> Unit,
    onNotificationPermission: () -> Unit,
    onAccessibility: () -> Unit,
    onShowAvatar: () -> Unit,
    onHideAvatar: () -> Unit,
    onRefreshApod: () -> Unit,
    onImportModel: () -> Unit
) {
    val status by voice.status().collectAsState(initial = "Idle")
    var last by remember { mutableStateOf("Tap the mic and say something.") }
    var typed by remember { mutableStateOf("") }
    var cloud by remember { mutableStateOf(settings.cloudEnabled) }
    var wake by remember { mutableStateOf(settings.wakeWordEnabled) }
    var spoken by remember { mutableStateOf(settings.voiceEnabled) }
    var key by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.modelFile) }

    LaunchedEffect(Unit) { voice.onTranscript = { last = it } }
    LaunchedEffect(modelRefresh) { model = settings.modelFile }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("BRAHMA MOBILE", color = androidx.compose.ui.graphics.Color(0xFFA48BFF))
        Text("Embodied 3D companion · ${brain.engineName()}")
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Status: $status")
                Text("Last: $last")
                Row {
                    Button(onClick = { voice.listenOnce() }) { Text("LISTEN") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { voice.speak(last) }) { Text("SPEAK") }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type a command") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(onClick = { if (typed.isNotBlank()) { voice.process(typed); typed = "" } }) {
                    Text("SEND")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Brain")
                Text(brain.localModelInfo())
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = cloud, onCheckedChange = {
                        cloud = it; settings.cloudEnabled = it
                    })
                    Text("Cloud fallback")
                }
                if (cloud) {
                    OutlinedTextField(
                        value = key, onValueChange = { key = it; settings.apiKey = it },
                        label = { Text("API key") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Row {
                        Button(onClick = { settings.provider = "gemini" }) { Text("Gemini") }
                        Spacer(Modifier.padding(4.dp))
                        Button(onClick = { settings.provider = "openrouter" }) { Text("OpenRouter") }
                    }
                }
                OutlinedTextField(
                    value = model, onValueChange = { model = it; settings.modelFile = it },
                    label = { Text("GGUF file in ExternalFiles/models") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Button(onClick = onImportModel) { Text("IMPORT GGUF FROM PHONE") }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Voice")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = wake, onCheckedChange = {
                        wake = it; settings.wakeWordEnabled = it
                        if (it) voice.start() else voice.stop()
                    })
                    Text("Wake word 'brahma'")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = spoken, onCheckedChange = {
                        spoken = it; settings.voiceEnabled = it
                    })
                    Text("Speak answers")
                }
                Text("Offline wake uses on-device STT when your phone provides it.")
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Avatar overlay")
                Row {
                    Button(onClick = onShowAvatar) { Text("SHOW") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = onHideAvatar) { Text("HIDE") }
                }
                Text("Drag the bubble anywhere; tap MIC to talk.")
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Permissions")
                OutlinedButton(onClick = onMicPermission, Modifier.fillMaxWidth()) { Text("Microphone") }
                OutlinedButton(onClick = onOverlayPermission, Modifier.fillMaxWidth()) { Text("Draw over other apps") }
                OutlinedButton(onClick = onNotificationPermission, Modifier.fillMaxWidth()) { Text("Notifications") }
                OutlinedButton(onClick = onAccessibility, Modifier.fillMaxWidth()) { Text("Brahma Reflex (Accessibility)") }
                Text("Also disable battery optimization for Brahma in system settings.")
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Daily space briefing")
                Text(briefing)
                Button(onClick = onRefreshApod) { Text("REFRESH APOD") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
