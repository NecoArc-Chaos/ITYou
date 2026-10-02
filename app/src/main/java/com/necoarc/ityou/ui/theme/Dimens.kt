package com.necoarc.ityou.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 跨组件共享的尺寸令牌。
 *
 * 骨架屏与真实内容必须引用同一份尺寸常量，否则一旦某处单独调整，
 * 加载完成时就会出现肉眼可见的跳变或结构错位。
 */
object Dimens {
    // ---- 列表 ----
    val listHorizontalPadding = 16.dp
    val listVerticalPadding = 8.dp
    val listItemSpacing = 14.dp

    // ---- 分类胶囊 ----
    /** M3 FilterChip 的标准高度 */
    val filterChipHeight = 32.dp

    // ---- 头条大卡 ----
    val heroCardHeight = 220.dp

    // ---- 文章卡片（ArticleCard）----
    val articleCardContentPadding = 16.dp
    val articleCardTextToThumbGap = 12.dp
    val articleCardThumbWidth = 96.dp
    val articleCardThumbHeight = 72.dp

    // ---- 详情页 ----
    val detailHorizontalPadding = 22.dp
    val detailVerticalPadding = 12.dp
    val detailBlockSpacing = 18.dp
    /** 正文图片在未知宽高比时的最大高度上限，避免按原图尺寸解码超大图 */
    val detailImageMaxHeight = 640.dp
    val detailImageMinHeight = 120.dp
}
