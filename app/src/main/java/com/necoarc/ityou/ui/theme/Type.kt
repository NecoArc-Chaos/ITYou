package com.necoarc.ityou.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.io.File

/** 内置默认表现型字体的 Assets 路径 */
const val BUNDLED_FONT_ASSET_PATH = "fonts/MarukoGothicCJKsc-Medium.ttf"

/** 内置字体展示名。注意：Maruko Gothic 是「圓體」而非黑体。 */
const val BUNDLED_FONT_NAME = "馬路口圓體 (Maruko Gothic)"

/** 内置字体作者与来源，用于设置页致谢。 */
const val BUNDLED_FONT_CREDIT = "Max · max32002/maruko-gothic · SIL OFL 1.1"

/** 已解析字体家族缓存，避免主线程每帧或每次重组重复解析 Typeface */
private val fontFamilyCache = mutableMapOf<String, FontFamily>()

/**
 * 解析并提供应用字体。
 *
 * 优先级逻辑：
 * 1. 若 [useSystemFont] 为 true：用户显式指定使用系统字体，直接返回 [FontFamily.Default]。
 * 2. 若用户安装了本地自定义字体文件且可用，优先返回用户安装的字体。
 * 3. 默认情况下，加载随包内置的「馬路口圓體」[BUNDLED_FONT_ASSET_PATH]，
 *    使全应用默认呈现优雅圆润的 MD3 Expressive 视觉。
 * 4. 出现任何异常安全回退到系统字体。
 */
fun resolveFontFamily(
    context: Context,
    useSystemFont: Boolean,
    customFontPath: String?
): FontFamily {
    if (useSystemFont) {
        return FontFamily.Default
    }

    // 1. 若有用户外部安装字体
    if (!customFontPath.isNullOrEmpty()) {
        val file = File(customFontPath)
        if (file.exists() && file.canRead() && file.length() > 0L) {
            val cacheKey = "custom|$customFontPath|${file.length()}|${file.lastModified()}"
            synchronized(fontFamilyCache) {
                fontFamilyCache[cacheKey]?.let { return it }
            }
            try {
                val tf = Typeface.createFromFile(file)
                val family = FontFamily(tf)
                synchronized(fontFamilyCache) {
                    fontFamilyCache[cacheKey] = family
                }
                return family
            } catch (_: Exception) {}
        }
    }

    // 2. 默认加载工程内置资产字体：馬路口圓體
    val bundledCacheKey = "bundled|$BUNDLED_FONT_ASSET_PATH"
    synchronized(fontFamilyCache) {
        fontFamilyCache[bundledCacheKey]?.let { return it }
    }

    return try {
        val tf = Typeface.createFromAsset(context.assets, BUNDLED_FONT_ASSET_PATH)
        val family = FontFamily(tf)
        synchronized(fontFamilyCache) {
            fontFamilyCache[bundledCacheKey] = family
        }
        family
    } catch (_: Exception) {
        FontFamily.Default
    }
}

/**
 * 针对中文阅读排版（仿 ReadYou 与 PixelPlayer）定制的 Expressive 排版系统。
 */
fun getExpressiveTypography(fontFamily: FontFamily = FontFamily.Default): Typography {
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.4.sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}
