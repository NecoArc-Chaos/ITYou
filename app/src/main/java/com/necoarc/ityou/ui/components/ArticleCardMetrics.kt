package com.necoarc.ityou.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.necoarc.ityou.ui.theme.Dimens

/** 骨架屏线条高度占行高的比例（剩余比例即为行间距）。 */
private const val BAR_GAP_RATIO = 0.25f

/** 元数据条高度占元数据行高的比例。 */
private const val META_BAR_RATIO = 0.75f

private val DEFAULT_TITLE_LINE_HEIGHT = 24.dp
private val DEFAULT_SUMMARY_LINE_HEIGHT = 18.dp
private val DEFAULT_META_HEIGHT = 16.dp

/**
 * 文章卡片的**唯一几何定义**。
 *
 * 这是本次修复「骨架屏与真实卡片错位」的核心：
 * 真实卡片与骨架屏共用同一个 `ArticleCardFrame`，而该 Frame 内部只依赖这份度量。
 * 于是「骨架屏行数 / 右侧图片位置 / 元数据栏高度」不可能再和真实卡片产生偏差。
 *
 * 所有行高都从 [MaterialTheme.typography] 的 `lineHeight` 换算而来（含 sp → dp 的
 * fontScale 换算），因此用户在系统里调大字号时，卡片与骨架屏会**同步**变高。
 *
 * 同时，卡片高度由「固定行数 × 行高」推导，与文本内容无关：
 * 标题恒占 2 行、摘要恒占 2 行，因此**列表内每一项高度完全一致** ——
 * 这既消灭了加载完成时的跳动，也让 LazyColumn 的测量与预取成本变得可预测。
 */
@Immutable
data class ArticleCardMetrics(
    val titleLineHeight: Dp,
    val summaryLineHeight: Dp,
    val metaHeight: Dp,
    val titleLines: Int = 2,
    val summaryLines: Int = 2,
    val contentPadding: Dp = Dimens.articleCardContentPadding,
    val textToThumbGap: Dp = Dimens.articleCardTextToThumbGap,
    val titleToSummaryGap: Dp = 6.dp,
    val summaryToMetaGap: Dp = 10.dp,
    val thumbWidth: Dp = Dimens.articleCardThumbWidth,
    val thumbHeight: Dp = Dimens.articleCardThumbHeight
) {
    // ---- 文本块（注意：Kotlin 属性按声明顺序初始化，被依赖项必须在前）----
    val titleBlockHeight: Dp = titleLineHeight * titleLines
    val summaryBlockHeight: Dp = summaryLineHeight * summaryLines

    /** 骨架屏标题线条的间距 */
    val titleBarGap: Dp = titleLineHeight * BAR_GAP_RATIO

    /** 骨架屏标题线条高度：保证 N 条线 + (N-1) 个间距 恰好铺满 [titleBlockHeight] */
    val titleBarHeight: Dp = (titleBlockHeight - titleBarGap * (titleLines - 1)) / titleLines

    /** 骨架屏摘要线条的间距 */
    val summaryBarGap: Dp = summaryLineHeight * BAR_GAP_RATIO

    /** 骨架屏摘要线条高度：同样恰好铺满 [summaryBlockHeight] */
    val summaryBarHeight: Dp = (summaryBlockHeight - summaryBarGap * (summaryLines - 1)) / summaryLines

    /** 骨架屏元数据条高度（在固定高度的元数据块内垂直居中） */
    val metaBarHeight: Dp = metaHeight * META_BAR_RATIO

    // ---- 整卡几何 ----
    val textColumnHeight: Dp =
        titleBlockHeight + titleToSummaryGap + summaryBlockHeight + summaryToMetaGap + metaHeight

    val contentHeight: Dp = maxOf(textColumnHeight, thumbHeight)
    val cardHeight: Dp = contentHeight + contentPadding * 2
}

/**
 * 纯函数：由排版行高与密度推导卡片度量（不依赖 Android 环境，可直接单元测试）。
 */
fun articleCardMetrics(
    density: Density,
    titleLineHeight: TextUnit,
    summaryLineHeight: TextUnit,
    metaLineHeight: TextUnit,
    titleLines: Int = 2,
    summaryLines: Int = 2
): ArticleCardMetrics = ArticleCardMetrics(
    titleLineHeight = textUnitToDp(density, titleLineHeight) ?: DEFAULT_TITLE_LINE_HEIGHT,
    summaryLineHeight = textUnitToDp(density, summaryLineHeight) ?: DEFAULT_SUMMARY_LINE_HEIGHT,
    metaHeight = textUnitToDp(density, metaLineHeight) ?: DEFAULT_META_HEIGHT,
    titleLines = titleLines,
    summaryLines = summaryLines
)

/**
 * sp → dp 换算。
 *
 * 这里刻意**不用** `TextUnit.toPx()` 无参重载：它依赖 `DensityAmbient` /
 * `FontScaleAmbient` 这两个 CompositionLocal，在非组合环境（例如单元测试）中会抛异常，
 * 属于隐式依赖。改为显式读取 [Density.fontScale] 与 [Density.density]：
 *
 *     px = value * fontScale * density
 *     dp = px / density
 *     => dp = value * fontScale
 *
 * 相消之后结果**只受系统字号缩放影响**，与屏幕像素密度无关。
 * 这正是「骨架屏与真实卡片在任何设备上都严格对齐」的数学基础，
 * 同时也是 `ArticleCardMetricsTest` 能够精确断言的前提。
 */
internal fun textUnitToDp(density: Density, unit: TextUnit): Dp? {
    if (!unit.isSp || unit.value <= 0f) return null
    return (unit.value * density.fontScale).dp
}

/**
 * 当前主题下的卡片度量。
 *
 * `remember(typography, density)` 保证只在「字体家族 / 字号 / 系统缩放」变化时重新计算。
 */
@Composable
fun rememberArticleCardMetrics(): ArticleCardMetrics {
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    return remember(typography, density) {
        articleCardMetrics(
            density = density,
            titleLineHeight = typography.titleMedium.lineHeight,
            summaryLineHeight = typography.bodySmall.lineHeight,
            metaLineHeight = typography.labelSmall.lineHeight
        )
    }
}

/**
 * 安全地把排版行高（sp）换算为 dp，换算规则见 [textUnitToDp]。
 */
internal fun lineHeightToDp(density: Density, style: TextStyle): Dp? =
    textUnitToDp(density, style.lineHeight)
