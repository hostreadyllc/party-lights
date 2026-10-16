package com.ash.partylights

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var engine: BeatEngine
    private lateinit var torch: Torch
    private lateinit var sync: PartySync
    private val startRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = Store(this)
        engine = BeatEngine(applicationContext)
        torch = Torch(applicationContext)
        sync = PartySync(applicationContext)
        handleIntent(intent)
        setContent {
            PartyTheme {
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_START) startRequested.value = true
    }

    override fun onDestroy() {
        sync.stop()
        engine.stopMic()
        torch.release()
        super.onDestroy()
    }

    @Composable
    private fun App() {
        var settings by remember { mutableStateOf(store.load()) }
        var scenes by remember { mutableStateOf(store.loadScenes()) }
        var accepted by remember { mutableStateOf(store.warningAccepted) }
        var showing by rememberSaveable { mutableStateOf(false) }

        fun update(s: PartySettings) {
            settings = s
            store.save(s)
        }

        LaunchedEffect(settings.syncRole) { sync.start(settings.syncRole) }

        val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            update(settings.copy(beatSource = if (granted) BeatSource.MIC else BeatSource.AUTO))
        }

        val wantsStart by startRequested
        LaunchedEffect(wantsStart, accepted) {
            if (wantsStart && accepted) {
                showing = true
                startRequested.value = false
            }
        }

        if (!accepted) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("⚠️ Photosensitivity warning") },
                text = {
                    Text(
                        "This app shows flashing and fast-changing coloured lights. " +
                            "A small number of people may have seizures when exposed to flashing lights, " +
                            "even with no history of epilepsy.\n\n" +
                            "Stop using the app right away if you or anyone nearby feels dizzy, has blurred vision, " +
                            "eye or face twitching, or any discomfort.\n\n" +
                            "Safe mode (no fast flashing) is on by default."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        store.warningAccepted = true
                        accepted = true
                    }) { Text("I understand") }
                },
                dismissButton = { TextButton(onClick = { finish() }) { Text("Exit") } },
            )
            return
        }

        if (showing) {
            ShowScreen(
                settings = settings,
                engine = engine,
                torch = torch,
                sync = sync,
                onSettings = ::update,
                onExit = { showing = false },
            )
        } else {
            HomeScreen(
                settings = settings,
                scenes = scenes,
                sync = sync,
                torchAvailable = torch.available,
                onSettings = ::update,
                onBeatSource = { b ->
                    if (b == BeatSource.MIC && !engine.hasMicPermission()) {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        update(settings.copy(beatSource = b))
                    }
                },
                onSaveScene = { name ->
                    scenes = scenes + Scene(name, settings)
                    store.saveScenes(scenes)
                },
                onLoadScene = { sc ->
                    // Keep this phone's safety and sync choices.
                    update(sc.settings.copy(safeMode = settings.safeMode, syncRole = settings.syncRole,
                        torch = settings.torch, sleepMinutes = settings.sleepMinutes))
                },
                onDeleteScene = { sc ->
                    scenes = scenes - sc
                    store.saveScenes(scenes)
                },
                onStart = { showing = true },
            )
        }
    }

    companion object {
        const val ACTION_START = "com.ash.partylights.START"
    }
}
