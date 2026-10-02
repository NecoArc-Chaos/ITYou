package com.necoarc.ityou.ui.components

/**
 * LazyColumn 的 contentType 标识。
 *
 * 显式声明 contentType 后，LazyColumn 才能在滚动回收时把同一个组合槽位
 * 复用给「结构相同」的条目（例如所有文章卡片共用一套槽位），
 * 而不是为每个条目重新分配一套组合节点，这是长列表滚动流畅度的关键之一。
 *
 * 骨架屏与真实列表必须使用同一组标识，才能保证首屏结构一致。
 */
object FeedItemType {
    const val CATEGORY_ROW = "category_row"
    const val HERO = "hero"
    const val ARTICLE = "article"
    const val LOADING_FOOTER = "loading_footer"
    const val END_FOOTER = "end_footer"
    const val EMPTY = "empty"
    const val ERROR = "error"
}
