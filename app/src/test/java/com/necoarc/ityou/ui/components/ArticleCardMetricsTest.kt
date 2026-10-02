package com.necoarc.ityou.ui.components

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「骨架屏 ↔ 真实卡片对齐」契约测试。
 *
 * 这组断言的意义在于：任何一次对尺寸的微调，
 * 只要破坏了「骨架屏占位块总高度 == 真实文本块高度」这个等式，测试就会立刻失败。
 * 换句话说，对齐不再是靠肉眼比对，而是被测试锁定。
 *
 * 换算约定：`sp` 行高 → dp 只受系统字号缩放影响，
 * 因此这里用 Density(density = 1f) 使期望值可以直接读出。
 */
class ArticleCardMetricsTest {

    private val unitDensity = Density(density = 1f, fontScale = 1f)

    /** 与 Typography 一一对应：titleMedium 24sp / bodySmall 18sp / labelSmall 16sp */
    private fun metrics(
        density: Density = unitDensity,
        title: TextUnit = 24.sp,
        summary: TextUnit = 18.sp,
        meta: TextUnit = 16.sp
    ) = articleCardMetrics(
        density = density,
        titleLineHeight = title,
        summaryLineHeight = summary,
        metaLineHeight = meta
    )

    @Test
    fun cardGeometry_matchesTypographyLineHeights() {
        val m = metrics()

        // 标题 2 行 × 24dp
        assertEquals(48f, m.titleBlockHeight.value, 0.01f)
        // 摘要 2 行 × 18dp
        assertEquals(36f, m.summaryBlockHeight.value, 0.01f)

        // 文本列 = 48 + 6 + 36 + 10 + 16 = 116dp
        assertEquals(116f, m.textColumnHeight.value, 0.01f)
        // 文本列高于缩略图 (72dp)，因此内容高度取文本列
        assertEquals(116f, m.contentHeight.value, 0.01f)
        // 整卡 = 内容 + 上下 16dp 内边距 = 148dp
        assertEquals(148f, m.cardHeight.value, 0.01f)
    }

    @Test
    fun skeletonBars_exactlyFillRealTextBlocks() {
        val m = metrics()

        // N 条线 + (N-1) 个间距 必须严格等于真实文本块高度，否则会出现半像素级错位
        val titleOccupied = m.titleBarHeight * m.titleLines + m.titleBarGap * (m.titleLines - 1)
        assertEquals(m.titleBlockHeight.value, titleOccupied.value, 0.001f)

        val summaryOccupied = m.summaryBarHeight * m.summaryLines + m.summaryBarGap * (m.summaryLines - 1)
        assertEquals(m.summaryBlockHeight.value, summaryOccupied.value, 0.001f)

        // 元数据条必须能放进固定高度的元数据块内
        assertTrue(m.metaBarHeight <= m.metaHeight)
    }

    @Test
    fun textColumnNeverExceedsThumbnail_soRowHeightIsDeterministic() {
        val m = metrics()
        // 缩略图不能高于文本列，否则卡片高度会由图片决定，破坏「所有卡片等高」
        assertTrue(m.thumbHeight <= m.textColumnHeight)
    }

    @Test
    fun metrics_scaleWithSystemFontScale_keepingAlignment() {
        // 用户把系统字号调大 1.5 倍
        val scaled = metrics(density = Density(density = 1f, fontScale = 1.5f))

        assertEquals(36f, scaled.titleLineHeight.value, 0.01f)
        assertEquals(27f, scaled.summaryLineHeight.value, 0.01f)
        assertEquals(24f, scaled.metaHeight.value, 0.01f)

        // 关键：卡片与骨架屏同步变高，对齐关系依旧成立
        val titleOccupied = scaled.titleBarHeight * scaled.titleLines +
            scaled.titleBarGap * (scaled.titleLines - 1)
        assertEquals(scaled.titleBlockHeight.value, titleOccupied.value, 0.001f)
    }

    @Test
    fun metrics_areIndependentOfScreenPixelDensity() {
        // 同一台设备的不同像素密度下，dp 结果必须完全一致
        val at1x = metrics(density = Density(density = 1f, fontScale = 1f))
        val at3x = metrics(density = Density(density = 3f, fontScale = 1f))

        assertEquals(at1x.titleLineHeight.value, at3x.titleLineHeight.value, 0.001f)
        assertEquals(at1x.cardHeight.value, at3x.cardHeight.value, 0.001f)
    }

    @Test
    fun unspecifiedLineHeight_fallsBackToSafeDefaults() {
        val m = metrics(
            title = TextUnit.Unspecified,
            summary = TextUnit.Unspecified,
            meta = TextUnit.Unspecified
        )

        assertEquals(24f, m.titleLineHeight.value, 0.001f)
        assertEquals(18f, m.summaryLineHeight.value, 0.001f)
        assertEquals(16f, m.metaHeight.value, 0.001f)
        // 兜底之后依旧满足对齐契约
        assertEquals(
            m.titleBlockHeight.value,
            (m.titleBarHeight * m.titleLines + m.titleBarGap * (m.titleLines - 1)).value,
            0.001f
        )
    }

    @Test
    fun skeletonBarHelpers_fillGivenBlocks() {
        val lineHeight = 28.dp
        val lines = 4
        val barHeight = skeletonBarHeight(lineHeight, lines)
        val gap = skeletonBarGap(lineHeight)
        val occupied = barHeight * lines + gap * (lines - 1)

        assertEquals((lineHeight * lines).value, occupied.value, 0.001f)
    }
}
