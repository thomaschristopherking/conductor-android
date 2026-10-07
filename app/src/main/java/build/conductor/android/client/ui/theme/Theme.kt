package build.conductor.android.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import build.conductor.android.client.data.settings.ColorSchemeChoice

private val ConductorPurple = Color(0xFF6750A4)

private val LightColors = lightColorScheme(primary = ConductorPurple)
private val DarkColors = darkColorScheme(primary = Color(0xFFD0BCFF))

@Composable
fun ConductorTheme(choice: ColorSchemeChoice, isDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = when (choice) {
        ColorSchemeChoice.DEFAULT -> defaultColorScheme(isDark)
        ColorSchemeChoice.NEAPOLITAN -> if (isDark) NeapolitanDarkColors else NeapolitanLightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Uses the wallpaper colours on Android 12 and later, and a fixed purple scheme before that. */
@Composable
private fun defaultColorScheme(isDark: Boolean): ColorScheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(LocalContext.current)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(LocalContext.current)
    isDark -> DarkColors
    else -> LightColors
}
