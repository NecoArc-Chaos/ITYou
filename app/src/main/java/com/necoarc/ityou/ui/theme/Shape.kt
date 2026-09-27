package com.necoarc.ityou.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Expressive 风格的形状系统
 * 特点：更大、更圆润、更具活力的圆角 (Card 24~28dp, Container 20~24dp)
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),      // MD3 Expressive 核心：卡片和容器使用 28dp
    extraLarge = RoundedCornerShape(36.dp)   // 大尺寸弹出层或弹窗
)
