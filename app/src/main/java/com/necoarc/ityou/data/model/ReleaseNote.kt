package com.necoarc.ityou.data.model

import androidx.compose.runtime.Immutable

/**
 * 变更类型枚举，映射 MD3E 色彩层级
 */
enum class ChangeType(val label: String) {
    FEATURE("新特性"),
    IMPROVEMENT("优化"),
    FIX("修复"),
    DESIGN("视觉")
}

data class ChangeItem(
    val type: ChangeType,
    val description: String
)

/**
 * 版本发布记录模型
 */
@Immutable
data class ReleaseNote(
    val version: String,
    val releaseDate: String,
    val summary: String,
    val changes: List<ChangeItem>,
    val isLatest: Boolean = false
)

/**
 * 内置可视化版本演进历史列表 (从最新到早期版本)
 */
val AppReleaseHistory = listOf(
    ReleaseNote(
        version = "v1.0.0",
        releaseDate = "2026-10-01",
        summary = "Material 3 Expressive 正式架构重构，带来纯净原生阅读与视觉跃迁。",
        isLatest = true,
        changes = listOf(
            ChangeItem(ChangeType.FEATURE, "支持 Android 14+ 预见式预测返回手势 (Predictive Back Gesture)"),
            ChangeItem(ChangeType.FEATURE, "新增自定义字体家族选择（衬线/无衬线/等宽代码/系统默认）"),
            ChangeItem(ChangeType.DESIGN, "1:1 精确对齐的流光微光骨架屏，解决布局回流跳动问题"),
            ChangeItem(ChangeType.DESIGN, "收敛顶栏标题空间，消除孤立占位行，阅读沉浸感提升"),
            ChangeItem(ChangeType.IMPROVEMENT, "重构 RSS 分类推断引擎与确定性关键词算法")
        )
    ),
    ReleaseNote(
        version = "v0.9.5",
        releaseDate = "2026-09-28",
        summary = "细节打磨与动效增强。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.DESIGN, "引入 PixelPlayer 风格的 24dp/28dp 平滑曲率形状系统 (ShapeCache)"),
            ChangeItem(ChangeType.IMPROVEMENT, "卡片点击引入物理微缩放反馈动效 (graphicsLayer scale)"),
            ChangeItem(ChangeType.FIX, "修复文章详情页面硬编码回退导致的文章内容错配问题")
        )
    ),
    ReleaseNote(
        version = "v0.9.0",
        releaseDate = "2026-09-27",
        summary = "ITYou 初代架构落成。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.FEATURE, "基于 Jsoup 的全原生富文本段落与图片网格 DOM 解析"),
            ChangeItem(ChangeType.FEATURE, "支持 Material You 动态壁纸 Monet 取色与本地设置持久化"),
            ChangeItem(ChangeType.IMPROVEMENT, "Clean Architecture 架构建立与 CI 自动化流水线打通")
        )
    )
)
