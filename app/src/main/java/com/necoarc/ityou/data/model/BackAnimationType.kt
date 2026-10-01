package com.necoarc.ityou.data.model

import androidx.compose.runtime.Immutable

/**
 * 预见式返回与页面转场动效风格枚举
 */
@Immutable
enum class BackAnimationType(
    val title: String,
    val summary: String,
    val badge: String
) {
    SPRING_SLIDE(
        title = "Expressive 物理弹簧滑移",
        summary = "基于 MD3E 阻尼张力的水平跟随位移，动感富有回弹活力",
        badge = "默认推荐"
    ),
    CONTAINER_SCALE(
        title = "容器共享缩放 (Scale)",
        summary = "页面由中心向外弹性扩张与收缩，类似 PixelPlayer 沉浸视效",
        badge = "PixelPlayer"
    ),
    SUBTLE_FADE(
        title = "柔和平滑渐变 (Crossfade)",
        summary = "极简透明度渐变过渡，无任何位移与视差干扰",
        badge = "极简"
    ),
    DRAWER_LIFT(
        title = "垂直抽屉升降 (Sheet Lift)",
        summary = "页面自屏幕底部向上浮现，返回时弹性回落",
        badge = "沉浸"
    )
}
