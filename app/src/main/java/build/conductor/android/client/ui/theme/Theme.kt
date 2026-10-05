package build.conductor.android.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ConductorPurple = Color(0xFF6750A4)

private val LightColors = lightColorScheme(primary = ConductorPurple)
private val DarkColors = darkColorScheme(primary = Color(0xFFD0BCFF))

/** Uses the wallpaper colours on Android 12 and later, and a fixed purple scheme before that. */
@Composable
fun ConductorTheme(isDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(LocalContext.current)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(LocalContext.current)
        isDark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
