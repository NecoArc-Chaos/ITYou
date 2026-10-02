package com.necoarc.ityou.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 已解析字体家族缓存。
 *
 * `Typeface.createFromFile` 需要读盘并解析字体表，属于重量级操作。
 * 之前 `MainActivity` 与设置页都在 composition 中直接调用它，
 * 任何一次重组（例如切换任意开关、打字预览）都会在主线程重新解析一次字体。
 * 这里用「路径 + 文件大小 + 修改时间」做键缓存，既避免重复解析，
 * 也能在用户替换字体文件后自动失效。
 */
private val fontFamilyCache = mutableMapOf<String, FontFamily>()

/**
 * 动态加载外部本地字体文件并封装为 Compose FontFamily。
 * 若路径无效或字体解析异常，安全回退到系统默认字体 [FontFamily.Default]。
 */
fun resolveFontFamily(fontFilePath: String?): FontFamily {
    if (fontFilePath.isNullOrEmpty()) return FontFamily.Default

    val file = File(fontFilePath)
    if (!file.exists() || !file.canRead() || file.length() == 0L) return FontFamily.Default

    val cacheKey = "$fontFilePath|${file.length()}|${file.lastModified()}"
    synchronized(fontFamilyCache) {
        fontFamilyCache[cacheKey]?.let { return it }
    }

    val resolved = try {
        FontFamily(Typeface.createFromFile(file))
    } catch (_: Exception) {
        FontFamily.Default
    }

    synchronized(fontFamilyCache) {
        fontFamilyCache[cacheKey] = resolved
    }
    return resolved
}

/**
 * 针对中文阅读排版（仿 ReadYou 与 PixelPlayer）定制的 Expressive 排版系统。
 *
 * 注意：本函数会构造 13 个 TextStyle，属于「贵重对象」，
 * 必须在 Compose 侧用 `remember` 缓存（见 `ITYouTheme`），不要每次重组都调用。
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
            lineHeight = 28.sp, // 扩大行高，适应中文长篇正文阅读
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
