package com.necoarc.ityou.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

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
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 性能要点：MaterialTheme 的 colorScheme / typography 会被全应用所有 Text 读取。
    // 如果每次重组都新建实例，`MaterialTheme.typography` 的引用就会变化，
    // 进而让所有 Text 失去「跳过重组」能力（等于每次主题重组都全屏重排一次文本）。
    val colorScheme = remember(darkTheme, dynamicColor, supportsDynamicColor, context) {
        when {
            dynamicColor && supportsDynamicColor ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    }

    val typography = remember(fontFamily) { getExpressiveTypography(fontFamily) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = ExpressiveShapes,
        content = content
    )
}
