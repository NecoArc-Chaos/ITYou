package com.necoarc.ityou.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = ITHomePrimaryLight,
    onPrimary = ITHomeOnPrimaryLight,
    primaryContainer = ITHomePrimaryContainerLight,
    onPrimaryContainer = ITHomeOnPrimaryContainerLight,
    secondary = ITHomeSecondaryLight,
    onSecondary = ITHomeOnSecondaryLight,
    secondaryContainer = ITHomeSecondaryContainerLight,
    onSecondaryContainer = ITHomeOnSecondaryContainerLight,
    tertiary = ITHomeTertiaryLight,
    onTertiary = ITHomeOnTertiaryLight,
    tertiaryContainer = ITHomeTertiaryContainerLight,
    onTertiaryContainer = ITHomeOnTertiaryContainerLight,
    background = ITHomeBackgroundLight,
    onBackground = ITHomeOnBackgroundLight,
    surface = ITHomeSurfaceLight,
    onSurface = ITHomeOnSurfaceLight,
    surfaceVariant = ITHomeSurfaceVariantLight,
    onSurfaceVariant = ITHomeOnSurfaceVariantLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = ITHomePrimaryDark,
    onPrimary = ITHomeOnPrimaryDark,
    primaryContainer = ITHomePrimaryContainerDark,
    onPrimaryContainer = ITHomeOnPrimaryContainerDark,
    secondary = ITHomeSecondaryDark,
    onSecondary = ITHomeOnSecondaryDark,
    secondaryContainer = ITHomeSecondaryContainerDark,
    onSecondaryContainer = ITHomeOnSecondaryContainerDark,
    tertiary = ITHomeTertiaryDark,
    onTertiary = ITHomeOnTertiaryDark,
    tertiaryContainer = ITHomeTertiaryContainerDark,
    onTertiaryContainer = ITHomeOnTertiaryContainerDark,
    background = ITHomeBackgroundDark,
    onBackground = ITHomeOnBackgroundDark,
    surface = ITHomeSurfaceDark,
    onSurface = ITHomeOnSurfaceDark,
    surfaceVariant = ITHomeSurfaceVariantDark,
    onSurfaceVariant = ITHomeOnSurfaceVariantDark,
)

@Composable
fun ITYouTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content
    )
}
