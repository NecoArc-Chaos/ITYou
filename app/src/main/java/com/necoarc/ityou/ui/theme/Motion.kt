package com.necoarc.ityou.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntSize

/**
 * MD3E 物理弹簧动效令牌。
 *
 * 与 `MainActivity` 的导航转场保持同一组参数
 * （`DampingRatioLowBouncy` + `StiffnessMediumLow`），
 * 保证全应用动效语言一致；此处集中定义，避免各处散落魔法数字。
 */
object Motion {

    /**
     * 通用尺寸变化（高度/宽度展开收起）。
     *
     * 使用较高的阻尼比而非 LowBouncy：展开类动画涉及布局尺寸变化，
     * 过度回弹会让相邻内容跟着抖动，视觉上显得廉价。
     * 这里取 [Spring.DampingRatioNoBouncy] 保证稳定收敛，仅保留弹簧的
     * 加速手感（相比 tween 更"跟手"）。
     */
    val expandSize: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 通用淡入淡出（配合尺寸动画一起使用）。 */
    val fadeInSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 轻微缩放（展开内容时从 0.96 归位，制造"生长"感）。 */
    val scaleInSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
