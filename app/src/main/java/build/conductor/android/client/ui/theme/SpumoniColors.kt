package build.conductor.android.client.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Pistachio, fior di latte and cherry, in the order of the block, for the preview in Settings. */
val SpumoniFlavours = listOf(Color(0xFF93C572), Color(0xFFFFFFFF), Color(0xFFD2042D))

/** Cherry is the primary colour and pistachio the secondary and tertiary, so "Ready" badges stay green, on a fior di latte surface. */
val SpumoniLightColors = lightColorScheme(
    primary = Color(0xFFA4262C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF410006),
    inversePrimary = Color(0xFFFFB3AE),
    secondary = Color(0xFF4C662B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDEDA6),
    onSecondaryContainer = Color(0xFF112000),
    tertiary = Color(0xFF2F6B3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB9F0BB),
    onTertiaryContainer = Color(0xFF002109),
    background = Color(0xFFFFFCF7),
    onBackground = Color(0xFF1D1B19),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF1D1B19),
    surfaceVariant = Color(0xFFE3E8D9),
    onSurfaceVariant = Color(0xFF44483D),
    surfaceTint = Color(0xFFA4262C),
    inverseSurface = Color(0xFF32302C),
    inverseOnSurface = Color(0xFFF6F0E9),
    outline = Color(0xFF75796C),
    outlineVariant = Color(0xFFC5C8BA),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceDim = Color(0xFFDED9D2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF6EF),
    surfaceContainer = Color(0xFFF4F1EA),
    surfaceContainerHigh = Color(0xFFEFEBE4),
    surfaceContainerHighest = Color(0xFFE9E6DF),
)

/** The same flavours on a dark pistachio surface. */
val SpumoniDarkColors = darkColorScheme(
    primary = Color(0xFFFFB3AE),
    onPrimary = Color(0xFF680010),
    primaryContainer = Color(0xFF8C1820),
    onPrimaryContainer = Color(0xFFFFDAD8),
    inversePrimary = Color(0xFFA4262C),
    secondary = Color(0xFFB2D28C),
    onSecondary = Color(0xFF213600),
    secondaryContainer = Color(0xFF354F1A),
    onSecondaryContainer = Color(0xFFCDEDA6),
    tertiary = Color(0xFF9ED3A0),
    onTertiary = Color(0xFF023915),
    tertiaryContainer = Color(0xFF1E5129),
    onTertiaryContainer = Color(0xFFB9F0BB),
    background = Color(0xFF191C16),
    onBackground = Color(0xFFE3E3DA),
    surface = Color(0xFF191C16),
    onSurface = Color(0xFFE3E3DA),
    surfaceVariant = Color(0xFF44483D),
    onSurfaceVariant = Color(0xFFC5C8BA),
    surfaceTint = Color(0xFFFFB3AE),
    inverseSurface = Color(0xFFE3E3DA),
    inverseOnSurface = Color(0xFF2E312A),
    outline = Color(0xFF8F9285),
    outlineVariant = Color(0xFF44483D),
    surfaceBright = Color(0xFF3F423A),
    surfaceDim = Color(0xFF191C16),
    surfaceContainerLowest = Color(0xFF131611),
    surfaceContainerLow = Color(0xFF1F221C),
    surfaceContainer = Color(0xFF232620),
    surfaceContainerHigh = Color(0xFF2E312A),
    surfaceContainerHighest = Color(0xFF393C34),
)
