package com.necoarc.ityou.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 借鉴 PixelPlayer 与 ReadYou 的 Material Design 3 Expressive 形状体系
 * 统一采用大曲率圆角与连续平滑曲线比例
 */
object ShapeCache {
    /** 8dp - 紧凑标签、小型徽章 */
    val smooth8 = RoundedCornerShape(8.dp)

    /** 12dp - 小卡片、二级元数据徽标 */
    val smooth12 = RoundedCornerShape(12.dp)

    /** 16dp - 列表缩略图、浮动操作按钮内部 */
    val smooth16 = RoundedCornerShape(16.dp)

    /** 20dp - 筛选胶囊 (FilterChip) */
    val smooth20 = RoundedCornerShape(20.dp)

    /** 24dp - 核心文章卡片 (ArticleCard)、引用框 */
    val smooth24 = RoundedCornerShape(24.dp)

    /** 28dp - Expressive 强调头条大卡 (HeroCard)、底部弹出栏 */
    val smooth28 = RoundedCornerShape(28.dp)

    /** 32dp - 悬浮控制胶囊栏 (FloatingToolbar) */
    val smooth32 = RoundedCornerShape(32.dp)

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
