package com.ash.partylights

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val PartyColors = darkColorScheme(
    primary = Color(0xFFFF4FD8),
    onPrimary = Color(0xFF2B0024),
    secondary = Color(0xFF00E5FF),
    tertiary = Color(0xFFB2FF59),
    background = Color(0xFF0B0614),
    surface = Color(0xFF120A1F),
    surfaceContainer = Color(0xFF1B1030),
)

@Composable
fun PartyTheme(content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        PartyColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
