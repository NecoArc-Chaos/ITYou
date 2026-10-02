package com.necoarc.ityou.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 借鉴 PixelPlayer 与 ReadYou 的 Material Design 3 Expressive 形状体系。
 *
 * 注意：圆角半径统一以 `radiusXxx` 常量对外暴露。
 * 骨架屏（Shimmer 占位）需要用「半径数值」而不是 Shape 对象来绘制圆角矩形，
 * 如果两处各写一份数字，就会随着迭代悄悄错位 ——
 * 所以 Shape 对象与骨架屏必须共用同一份常量。
 */
object ShapeCache {
    val radiusTiny = 8.dp
    val radiusSmall = 12.dp
    val radiusMedium = 16.dp
    val radiusLarge = 20.dp
    val radiusXLarge = 24.dp
    val radiusHero = 28.dp
    val radiusSheet = 32.dp

    /** 8dp - 紧凑标签、小型徽章 */
    val smooth8 = RoundedCornerShape(radiusTiny)

    /** 12dp - 小卡片、二级元数据徽标 */
    val smooth12 = RoundedCornerShape(radiusSmall)

    /** 16dp - 列表缩略图、引用框 */
    val smooth16 = RoundedCornerShape(radiusMedium)

    /** 20dp - 筛选胶囊 (FilterChip) */
    val smooth20 = RoundedCornerShape(radiusLarge)

    /** 24dp - 核心文章卡片 (ArticleCard) */
    val smooth24 = RoundedCornerShape(radiusXLarge)

    /** 28dp - Expressive 强调头条大卡 (HeroCard)、底部弹出栏 */
    val smooth28 = RoundedCornerShape(radiusHero)

    /** 32dp - 悬浮控制胶囊栏、底部抽屉 */
    val smooth32 = RoundedCornerShape(radiusSheet)

    /** 全胶囊形状 - 按钮、搜索条 */
    val smoothPill = RoundedCornerShape(percent = 50)
}

val ExpressiveShapes = Shapes(
    extraSmall = ShapeCache.smooth8,
    small = ShapeCache.smooth12,
    medium = ShapeCache.smooth20,
    large = ShapeCache.smooth28,
    extraLarge = ShapeCache.smooth32
)
