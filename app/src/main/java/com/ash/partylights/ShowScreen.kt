package com.ash.partylights

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

private class ShowClock {
    var phase = 0.0
    var lastNanos = 0L
    var lastBeatSeen = -1
    var lastRemoteBeats = -1
    var lastBroadcast = 0L
    var lastHello = 0L
    var beatsSinceSwitch = 0
    var lastStrobeStep = -1L
}

@Composable
fun ShowScreen(
    settings: PartySettings,
    engine: BeatEngine,
    torch: Torch,
    sync: PartySync,
    onSettings: (PartySettings) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val current by rememberUpdatedState(settings)
    val clock = remember { ShowClock() }
    var frameTick by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsKey by remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val startNanos = remember { System.nanoTime() }

    // Full brightness, screen always on, hide system bars.
    DisposableEffect(Unit) {
        val window = activity.window
        val oldBrightness = window.attributes.screenBrightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
            insets.show(WindowInsetsCompat.Type.systemBars())
            engine.stopMic()
            torch.off()
        }
    }

    // Microphone only while needed.
    LaunchedEffect(settings.beatSource, settings.syncRole) {
        if (settings.beatSource == BeatSource.MIC && settings.syncRole != SyncRole.GUEST) {
            engine.startMic()
        } else {
            engine.stopMic()
        }
    }

    LaunchedEffect(controlsKey) {
        if (controlsVisible) {
            delay(3500)
            controlsVisible = false
        }
    }

    BackHandler(onBack = onExit)

    // Frame loop.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val now = System.nanoTime()
            val s = current
            val dt = if (clock.lastNanos == 0L) 0.0 else (now - clock.lastNanos) / 1e9
            clock.lastNanos = now

            if (s.sleepMinutes > 0 && now - startNanos > s.sleepMinutes * 60_000_000_000L) {
                onExit(); return@LaunchedEffect
            }

            engine.minGapNanos = if (s.safeMode) 400_000_000L else 280_000_000L
            val guestRemote = if (s.syncRole == SyncRole.GUEST && sync.hostConnected) sync.remote else null

            if (!paused) {
                if (guestRemote != null) {
                    val rs = guestRemote.settings
                    clock.phase = guestRemote.phase + (now - guestRemote.receivedAt) / 1e9 * rs.speed
                    if (clock.lastRemoteBeats != -1 && guestRemote.beats != clock.lastRemoteBeats) engine.beat(now)
                    clock.lastRemoteBeats = guestRemote.beats
                } else {
                    clock.phase += dt * s.speed
                    val bpm = (120f * s.speed).coerceIn(40f, if (s.safeMode) 150f else 300f)
                    engine.tick(now, s.beatSource, bpm)
                }
            }

            val beats = engine.beats
            val newBeat = beats != clock.lastBeatSeen
            clock.lastBeatSeen = beats
            val shown = guestRemote?.settings?.copy(safeMode = s.safeMode, brightness = s.brightness, torch = s.torch) ?: s

            if (!paused && newBeat) {
                // Auto DJ (the host decides for guests).
                if (s.autoDj && guestRemote == null) {
                    clock.beatsSinceSwitch++
                    if (clock.beatsSinceSwitch >= s.autoDjBeats) {
                        clock.beatsSinceSwitch = 0
                        val choices = Mode.entries.filter {
                            it != s.mode && !(s.safeMode && it == Mode.STROBE) && !(it == Mode.BANNER && s.text.isBlank())
                        }
                        onSettings(s.copy(mode = choices.random()))
                    }
                }
                if (shown.torch && !shown.safeMode && shown.effectiveMode != Mode.STROBE) torch.flash()
            }

            // In strobe mode the LED follows the screen flashes.
            if (!paused && shown.torch && !shown.safeMode && shown.effectiveMode == Mode.STROBE) {
                val step = kotlin.math.floor(clock.phase * 4).toLong()
                if (step != clock.lastStrobeStep) {
                    clock.lastStrobeStep = step
                    torch.flash(40)
                }
            }

            if (s.syncRole == SyncRole.HOST && (newBeat || now - clock.lastBroadcast > 200_000_000L)) {
                clock.lastBroadcast = now
                sync.broadcastState(s, clock.phase, beats)
            }
            if (s.syncRole == SyncRole.GUEST && now - clock.lastHello > 2_000_000_000L) {
                clock.lastHello = now
                sync.sendHello()
            }
            frameTick = now
        }
    }

    // Slow ticker so status text (guests joined, host found) stays fresh.
    var secondTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); secondTick++ } }

    fun shownSettings(): PartySettings {
        val remote = if (current.syncRole == SyncRole.GUEST && sync.hostConnected) sync.remote else null
        return remote?.settings?.copy(safeMode = current.safeMode, brightness = current.brightness) ?: current
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible; controlsKey++ },
                    onDoubleTap = { paused = !paused },
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val now = frameTick
            val shown = shownSettings()
            val guestRemote = if (current.syncRole == SyncRole.GUEST && sync.hostConnected) sync.remote else null
            val frame = Frame(
                mode = shown.effectiveMode,
                phase = clock.phase.toFloat(),
                pulse = if (paused) 0f else engine.pulse(if (now == 0L) System.nanoTime() else now),
                beat = guestRemote?.beats ?: engine.beats,
                colors = shown.colors.map { Color(it) },
                safe = shown.safeMode,
                text = shown.text,
                brightness = if (paused) 0.35f * shown.brightness else shown.brightness,
            )
            drawEffect(frame, measurer)
        }

        if (paused) {
            Text("PAUSED · double-tap to resume", color = Color.White, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ShowControls(
                tick = secondTick,
                settings = settings,
                shown = shownSettings(),
                engine = engine,
                sync = sync,
                paused = paused,
                onPause = { paused = !paused; controlsKey++ },
                onMode = { delta ->
                    val list = Mode.entries.filter { !(settings.safeMode && it == Mode.STROBE) }
                    val i = list.indexOf(settings.effectiveMode).coerceAtLeast(0)
                    onSettings(settings.copy(mode = list[(i + delta + list.size) % list.size]))
                    controlsKey++
                },
                onTap = { engine.tap(System.nanoTime()); controlsKey++ },
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun ShowControls(
    @Suppress("UNUSED_PARAMETER") tick: Int,
    settings: PartySettings,
    shown: PartySettings,
    engine: BeatEngine,
    sync: PartySync,
    paused: Boolean,
    onPause: () -> Unit,
    onMode: (Int) -> Unit,
    onTap: () -> Unit,
    onExit: () -> Unit,
) {
    val guest = settings.syncRole == SyncRole.GUEST
    Column(
        Modifier
            .safeDrawingPadding()
            .padding(16.dp)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val status = when {
            guest && sync.hostConnected -> "Following host · ${shown.effectiveMode.label}"
            guest -> "Looking for host on this Wi-Fi…"
            settings.syncRole == SyncRole.HOST -> "Hosting · ${sync.guestCount} joined · ${shown.effectiveMode.label}"
            else -> shown.effectiveMode.label
        }
        Text(status, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (settings.beatSource == BeatSource.TAP && !guest) {
            Text("${engine.tapBpm} BPM", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onExit) { Icon(Icons.Filled.Close, "Exit") }
            if (!guest) {
                FilledTonalIconButton(onClick = { onMode(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous effect") }
            }
            FilledTonalIconButton(onClick = onPause) {
                Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (paused) "Resume" else "Pause")
            }
            if (!guest) {
                FilledTonalIconButton(onClick = { onMode(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next effect") }
            }
            if (settings.beatSource == BeatSource.TAP && !guest) {
                FilledTonalIconButton(onClick = onTap, modifier = Modifier.size(56.dp)) {
                    Text("TAP", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
