/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Tmusic (fork) — pure monochrome identity. No brand hue: every palette is
 * black / white / grey. Red is reserved for errors (accessibility).
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.materialkolor.score.Score

/**
 * Sentinel color for the theme-color preference. The palette is fixed to
 * monochrome, so this value never tints the UI — it only tells the theme
 * settings screen whether "dynamic" behavior is active.
 */
val DefaultThemeColor = Color(0xFFED5564)

// ---------------------------------------------------------------------------
// Monochrome palettes
// ---------------------------------------------------------------------------

/** Dark theme — true black background, grey surfaces, white accents. */
val TmusicDarkScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1E1E1E),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF9E9E9E),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color(0xFFD0D0D0),
    tertiary = Color(0xFFB4B4B4),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF262626),
    onTertiaryContainer = Color(0xFFE6E6E6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceTint = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF000000),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF2E2E2E),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    outline = Color(0xFF2C2C2C),
    outlineVariant = Color(0xFF1E1E1E),
    scrim = Color(0xFF000000),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Light theme — white background, near-white surfaces, black accents. */
val TmusicLightScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF757575),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color(0xFF333333),
    tertiary = Color(0xFF616161),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEEEEE),
    onTertiaryContainer = Color(0xFF424242),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF5F5F5),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF757575),
    surfaceTint = Color(0xFF000000),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFF0F0F0),
    inversePrimary = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE0E0E0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    outline = Color(0xFFDCDCDC),
    outlineVariant = Color(0xFFEDEDED),
    scrim = Color(0xFF000000),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/**
 * "Black" AMOLED variant — every surface is literally black. Cards are told
 * apart by a 1px outline instead of tonal elevation, so the elevation-tinted
 * surface slots collapse to black as well.
 */
val TmusicBlackScheme = TmusicDarkScheme.copy(
    background = Color(0xFF000000),
    onBackground = TmusicDarkScheme.onBackground,
    surface = Color(0xFF000000),
    onSurface = TmusicDarkScheme.onSurface,
    surfaceVariant = Color(0xFF000000),
    onSurfaceVariant = TmusicDarkScheme.onSurfaceVariant,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF000000),
    surfaceContainerHighest = Color(0xFF000000),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF000000),
    outline = Color(0xFF2C2C2C),
    outlineVariant = Color(0xFF1A1A1A),
)

// ---------------------------------------------------------------------------
// Typography & shapes — crisp grotesk sans, minimal radii
// ---------------------------------------------------------------------------

/** App-wide typography. Uses the bundled BBH Bartle grotesk as the single family. */
val TmusicTypography = Typography(defaultFontFamily = bbhBartle)

/** Crisp, structured corner radii (8–12dp) instead of bubbly heavy rounding. */
val TmusicShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    // The theme is deliberately monochrome: `themeColor` is accepted for
    // settings compatibility but never tints the palette.
    val colorScheme =
        remember(darkTheme, pureBlack) {
            when {
                darkTheme && pureBlack -> TmusicBlackScheme
                darkTheme -> TmusicDarkScheme
                else -> TmusicLightScheme
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TmusicTypography,
        shapes = TmusicShapes,
        content = content,
    )
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
