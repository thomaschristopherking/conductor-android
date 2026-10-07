package build.conductor.android.client.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Chocolate, vanilla and strawberry, in the order of the block, for the preview in Settings. */
val NeapolitanFlavours = listOf(Color(0xFF6F4A35), Color(0xFFFFE89E), Color(0xFFF48FB1))

/** Strawberry is the primary colour, chocolate the secondary and vanilla the tertiary, on a vanilla cream surface. */
val NeapolitanLightColors = lightColorScheme(
    primary = Color(0xFFA3365C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E0019),
    inversePrimary = Color(0xFFFFB1C6),
    secondary = Color(0xFF6F4A35),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5DCC8),
    onSecondaryContainer = Color(0xFF2A1609),
    tertiary = Color(0xFF735C00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE89E),
    onTertiaryContainer = Color(0xFF241A00),
    background = Color(0xFFFFF8EE),
    onBackground = Color(0xFF2B1F19),
    surface = Color(0xFFFFF8EE),
    onSurface = Color(0xFF2B1F19),
    surfaceVariant = Color(0xFFF4E1D5),
    onSurfaceVariant = Color(0xFF53433B),
    surfaceTint = Color(0xFFA3365C),
    inverseSurface = Color(0xFF41302A),
    inverseOnSurface = Color(0xFFFFEDE3),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD8C2B6),
    surfaceBright = Color(0xFFFFF8EE),
    surfaceDim = Color(0xFFE7D7CB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E6),
    surfaceContainer = Color(0xFFFAEBDF),
    surfaceContainerHigh = Color(0xFFF4E5D8),
    surfaceContainerHighest = Color(0xFFEEDFD2),
)

/** The same flavours on a dark chocolate surface. */
val NeapolitanDarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C6),
    onPrimary = Color(0xFF5F1131),
    primaryContainer = Color(0xFF822447),
    onPrimaryContainer = Color(0xFFFFD9E2),
    inversePrimary = Color(0xFFA3365C),
    secondary = Color(0xFFE4BFA6),
    onSecondary = Color(0xFF422B1C),
    secondaryContainer = Color(0xFF5B4030),
    onSecondaryContainer = Color(0xFFFFDCC6),
    tertiary = Color(0xFFEBCB6B),
    onTertiary = Color(0xFF3C2F00),
    tertiaryContainer = Color(0xFF574500),
    onTertiaryContainer = Color(0xFFFFE89E),
    background = Color(0xFF21150F),
    onBackground = Color(0xFFF1DFD5),
    surface = Color(0xFF21150F),
    onSurface = Color(0xFFF1DFD5),
    surfaceVariant = Color(0xFF53433B),
    onSurfaceVariant = Color(0xFFD8C2B6),
    surfaceTint = Color(0xFFFFB1C6),
    inverseSurface = Color(0xFFF1DFD5),
    inverseOnSurface = Color(0xFF3A2B23),
    outline = Color(0xFFA08D83),
    outlineVariant = Color(0xFF53433B),
    surfaceBright = Color(0xFF4A3A31),
    surfaceDim = Color(0xFF21150F),
    surfaceContainerLowest = Color(0xFF1B100A),
    surfaceContainerLow = Color(0xFF2A1D16),
    surfaceContainer = Color(0xFF2F211A),
    surfaceContainerHigh = Color(0xFF3A2B23),
    surfaceContainerHighest = Color(0xFF46362D),
)
