package com.ash.partylights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.exp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    settings: PartySettings,
    scenes: List<Scene>,
    sync: PartySync,
    torchAvailable: Boolean,
    onSettings: (PartySettings) -> Unit,
    onBeatSource: (BeatSource) -> Unit,
    onSaveScene: (String) -> Unit,
    onLoadScene: (Scene) -> Unit,
    onDeleteScene: (Scene) -> Unit,
    onStart: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmUnsafe by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Party Lights", fontWeight = FontWeight.Black) },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = pad,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PreviewCard(settings)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.size(8.dp))
                        Text("START THE PARTY", fontWeight = FontWeight.Black)
                    }
                    Text(
                        "Tip: point the screen at a wall or ceiling in a dark room. Tap the lights for controls, double-tap to pause.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                Section("Effect") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mode.entries.forEach { m ->
                            val locked = m == Mode.STROBE && settings.safeMode
                            FilterChip(
                                selected = settings.mode == m && !locked,
                                onClick = { if (locked) confirmUnsafe = true else onSettings(settings.copy(mode = m)) },
                                label = { Text(if (locked) "${m.label} 🔒" else m.label) },
                            )
                        }
                    }
                    if (settings.mode == Mode.BANNER || settings.autoDj) {
                        OutlinedTextField(
                            value = settings.text,
                            onValueChange = { onSettings(settings.copy(text = it.take(60))) },
                            label = { Text("LED text banner") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }

            item {
                Section("Colours") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Palette.entries.forEach { p ->
                            FilterChip(
                                selected = settings.palette == p,
                                onClick = { onSettings(settings.copy(palette = p)) },
                                label = { Text(p.label) },
                                leadingIcon = {
                                    val cols = if (p == Palette.CUSTOM) settings.customColors else p.colors
                                    Row { cols.take(4).forEach { Box(Modifier.size(8.dp).background(Color(it), CircleShape)) } }
                                },
                            )
                        }
                    }
                    if (settings.palette == Palette.CUSTOM) {
                        Text("Pick up to 6 colours", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customSwatches.forEach { c ->
                                val on = c in settings.customColors
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(if (on) 4.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                                        .clickable {
                                            val list = settings.customColors
                                            val next = when {
                                                on && list.size > 1 -> list - c
                                                on -> list
                                                list.size >= 6 -> list.drop(1) + c
                                                else -> list + c
                                            }
                                            onSettings(settings.copy(customColors = next))
                                        }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Section("Speed & brightness") {
                    LabeledSlider("Speed", settings.speed, 0.25f..3f, "%.1f×".format(settings.speed)) {
                        onSettings(settings.copy(speed = it))
                    }
                    LabeledSlider("Brightness", settings.brightness, 0.2f..1f, "${(settings.brightness * 100).toInt()}%") {
                        onSettings(settings.copy(brightness = it))
                    }
                }
            }

            item {
                Section("Beat") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BeatSource.entries.forEach { b ->
                            FilterChip(
                                selected = settings.beatSource == b,
                                onClick = { onBeatSource(b) },
                                label = { Text(b.label) },
                            )
                        }
                    }
                    Text(
                        when (settings.beatSource) {
                            BeatSource.AUTO -> "Lights follow a steady beat set by the speed slider."
                            BeatSource.MIC -> "Lights pulse to music the phone hears. Nothing is recorded or saved."
                            BeatSource.TAP -> "Tap the TAP button during the show along with the music."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            item {
                Section("Extras") {
                    SwitchRow("Auto DJ", "Switch effects by itself every few beats", settings.autoDj) {
                        onSettings(settings.copy(autoDj = it))
                    }
                    if (settings.autoDj) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(8, 16, 32, 64).forEach { n ->
                                FilterChip(settings.autoDjBeats == n, { onSettings(settings.copy(autoDjBeats = n)) },
                                    label = { Text("Every $n beats") })
                            }
                        }
                    }
                    if (torchAvailable) {
                        SwitchRow(
                            "Flashlight strobe",
                            if (settings.safeMode) "Turn off Safe mode to use" else "The camera LED flashes on the beat",
                            settings.torch && !settings.safeMode,
                            enabled = !settings.safeMode,
                        ) { onSettings(settings.copy(torch = it)) }
                    }
                    SwitchRow("Safe mode", "No fast flashing. Recommended around others.", settings.safeMode) {
                        if (it) onSettings(settings.copy(safeMode = true)) else confirmUnsafe = true
                    }
                    Text("Sleep timer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 15, 30, 60, 120).forEach { m ->
                            FilterChip(settings.sleepMinutes == m, { onSettings(settings.copy(sleepMinutes = m)) },
                                label = { Text(if (m == 0) "Off" else "$m min") })
                        }
                    }
                }
            }

            item {
                Section("Party sync") {
                    Text(
                        "Run the same lights on several phones. All phones must be on the same Wi-Fi or hotspot. One phone hosts, the others join.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SyncRole.entries.forEach { r ->
                            FilterChip(settings.syncRole == r, { onSettings(settings.copy(syncRole = r)) },
                                label = { Text(r.label) })
                        }
                    }
                    SyncStatus(settings.syncRole, sync)
                }
            }

            item {
                Section("My scenes") {
                    if (scenes.isEmpty()) {
                        Text("Save your favourite effect, colours and speed to use again.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    scenes.forEach { sc ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onLoadScene(sc) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(Modifier.padding(end = 12.dp)) {
                                sc.settings.colors.take(3).forEach { Box(Modifier.size(12.dp).background(Color(it), CircleShape)) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(sc.name, fontWeight = FontWeight.Bold)
                                Text("${sc.settings.mode.label} · ${sc.settings.palette.label}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onDeleteScene(sc) }) { Icon(Icons.Filled.Delete, "Delete scene") }
                        }
                    }
                    OutlinedButton(onClick = { naming = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Save current as scene")
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmUnsafe) {
        AlertDialog(
            onDismissRequest = { confirmUnsafe = false },
            title = { Text("Turn off Safe mode?") },
            text = {
                Text("This allows fast strobe flashes and the flashlight strobe. Fast flashing lights can trigger seizures in people with photosensitive epilepsy. Only continue if everyone nearby is comfortable with it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsafe = false
                    onSettings(settings.copy(safeMode = false))
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { confirmUnsafe = false }) { Text("Keep safe") } },
        )
    }

    if (naming) {
        var name by remember { mutableStateOf("Scene ${scenes.size + 1}") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save scene") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    naming = false
                    onSaveScene(name.ifBlank { "Scene" })
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SyncStatus(role: SyncRole, sync: PartySync) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(role) {
        while (true) {
            text = when (role) {
                SyncRole.OFF -> ""
                SyncRole.HOST -> "Hosting · ${sync.guestCount} phone(s) joined. Start the party on this phone."
                SyncRole.GUEST -> if (sync.hostConnected) "Host found · start the party to follow it" else "Looking for a host on this Wi-Fi…"
            }
            if (role == SyncRole.GUEST) sync.sendHello()
            kotlinx.coroutines.delay(1000)
        }
    }
    if (text.isNotEmpty()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f))
            Text(valueText, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Small live preview of the chosen effect. */
@Composable
private fun PreviewCard(settings: PartySettings) {
    val measurer = rememberTextMeasurer()
    val s by rememberUpdatedState(settings)
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) phase += (now - last) / 1e9f * s.speed
                last = now
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val beatLen = 0.5f
            val sincebeat = phase % beatLen
            val frame = Frame(
                mode = s.effectiveMode,
                phase = phase,
                pulse = exp(-sincebeat * 6f),
                beat = (phase / beatLen).toInt(),
                colors = s.colors.map { Color(it) },
                safe = true, // the preview never strobes
                text = s.text,
                brightness = s.brightness,
            )
            drawEffect(if (frame.mode == Mode.STROBE) Frame(Mode.DISCO, frame.phase, frame.pulse, frame.beat, frame.colors, true, frame.text, frame.brightness) else frame, measurer)
        }
    }
}
