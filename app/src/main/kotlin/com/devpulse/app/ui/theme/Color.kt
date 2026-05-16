package com.devpulse.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Indigo — brand primary
internal val Indigo50 = Color(0xFFEEF2FF)
internal val Indigo100 = Color(0xFFE0E7FF)
internal val Indigo400 = Color(0xFF818CF8)
internal val Indigo500 = Color(0xFF6366F1)
internal val Indigo600 = Color(0xFF4F46E5)
internal val Indigo700 = Color(0xFF4338CA)
internal val Indigo900 = Color(0xFF312E81)

// Zinc — neutrals
internal val Zinc50 = Color(0xFFFAFAFA)
internal val Zinc100 = Color(0xFFF4F4F5)
internal val Zinc200 = Color(0xFFE4E4E7)
internal val Zinc300 = Color(0xFFD4D4D8)
internal val Zinc400 = Color(0xFFA1A1AA)
internal val Zinc500 = Color(0xFF71717A)
internal val Zinc600 = Color(0xFF52525B)
internal val Zinc700 = Color(0xFF3F3F46)
internal val Zinc800 = Color(0xFF27272A)
internal val Zinc900 = Color(0xFF18181B)
internal val Zinc950 = Color(0xFF09090B)

// Semantic
internal val Red100 = Color(0xFFFEE2E2)
internal val Red400 = Color(0xFFF87171)
internal val Red500 = Color(0xFFEF4444)
internal val Red900 = Color(0xFF7F1D1D)

internal val Emerald100 = Color(0xFFD1FAE5)
internal val Emerald500 = Color(0xFF10B981)
internal val Emerald900 = Color(0xFF064E3B)

internal val Amber100 = Color(0xFFFEF3C7)
internal val Amber500 = Color(0xFFF59E0B)

internal val LightColorScheme =
    lightColorScheme(
        primary = Indigo600,
        onPrimary = Color.White,
        primaryContainer = Indigo50,
        onPrimaryContainer = Indigo700,
        secondary = Zinc700,
        onSecondary = Color.White,
        secondaryContainer = Zinc100,
        onSecondaryContainer = Zinc800,
        tertiary = Emerald500,
        onTertiary = Color.White,
        tertiaryContainer = Emerald100,
        onTertiaryContainer = Emerald900,
        background = Zinc50,
        onBackground = Zinc900,
        surface = Color.White,
        onSurface = Zinc900,
        surfaceVariant = Zinc100,
        onSurfaceVariant = Zinc500,
        outline = Zinc200,
        outlineVariant = Zinc100,
        error = Red500,
        onError = Color.White,
        errorContainer = Red100,
        onErrorContainer = Red900,
        inverseSurface = Zinc900,
        inverseOnSurface = Zinc50,
        inversePrimary = Indigo400,
        scrim = Zinc950,
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = Indigo400,
        onPrimary = Zinc950,
        primaryContainer = Indigo900,
        onPrimaryContainer = Indigo100,
        secondary = Zinc400,
        onSecondary = Zinc950,
        secondaryContainer = Zinc800,
        onSecondaryContainer = Zinc100,
        tertiary = Emerald500,
        onTertiary = Zinc950,
        tertiaryContainer = Emerald900,
        onTertiaryContainer = Emerald100,
        background = Zinc950,
        onBackground = Zinc100,
        surface = Zinc900,
        onSurface = Zinc100,
        surfaceVariant = Zinc800,
        onSurfaceVariant = Zinc400,
        outline = Zinc700,
        outlineVariant = Zinc800,
        error = Red400,
        onError = Red900,
        errorContainer = Color(0xFF450A0A),
        onErrorContainer = Color(0xFFFECACA),
        inverseSurface = Zinc100,
        inverseOnSurface = Zinc900,
        inversePrimary = Indigo600,
        scrim = Zinc950,
    )
