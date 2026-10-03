package com.necoarc.ityou.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 将 IT之家评论中的 `[表情]` 转为内联图片并渲染。
 *
 * 实现要点（性能相关）：
 * 1. 使用 [AnnotatedString] + `appendInlineContent` 让表情与文字**同段落混排**，
 *    不额外增加布局节点，也不会因为插入图片导致整段重新测量。
 * 2. 纯文本与 [InlineTextContent] 的构建结果用 [remember] 缓存，
 *    键为「文本 + 表情尺寸」，避免重组时重复解析正则与重建 map。
 * 3. 未知名称的方括号（例如 `arr[0]`）**保持原样**，不做替换，避免误伤。
 *
 * @param text 原始评论文本（可能含 `[名称]` 表情）
 * @param style 文字样式（表情尺寸默认取该样式的字号）
 * @param color 文字颜色
 */
@Composable
internal fun CommentEmojiText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip
) {
    // 表情高度对齐文字行高，宽度按正方形容器估算。
    // 防御：若样式未显式指定 fontSize（Unspecified），乘法会抛异常，
    // 此时回退到 LocalTextStyle 的字号；仍不可用则用 14sp 兜底。
    val baseFontSize: TextUnit = when {
        style.fontSize.isSpecified -> style.fontSize
        LocalTextStyle.current.fontSize.isSpecified -> LocalTextStyle.current.fontSize
        else -> DEFAULT_EMOJI_FONT_SIZE
    }
    val emojiSize: TextUnit = baseFontSize * EMOJI_SIZE_EM

    val annotated: AnnotatedString = remember(text) {
        buildEmojiAnnotatedString(text)
    }
    val inlineContent: Map<String, InlineTextContent> = remember(text, emojiSize) {
        buildEmojiInlineContent(text, emojiSize)
    }

    if (inlineContent.isEmpty()) {
        // 无表情：走最快的纯文本路径，不引入 InlineTextContent 查找开销
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            overflow = overflow
        )
    } else {
        Text(
            text = annotated,
            modifier = modifier,
            style = style,
            color = color,
            overflow = overflow,
            inlineContent = inlineContent
        )
    }
}

/** 表情相对于字号的倍率，略大于 1 让图形更醒目又不撑高行距。 */
private const val EMOJI_SIZE_EM = 1.15f

/** 字号无法推断时的兜底值。 */
private val DEFAULT_EMOJI_FONT_SIZE = 14.sp

private const val INLINE_PREFIX = "emoji_"

/**
 * 构建带内联占位符的 [AnnotatedString]。
 *
 * 用「替换为占位符 ID」的方式保留原始字符偏移语义：
 * 每个表情在文本流中占据一个占位符，图片宽度由 [InlineTextContent] 控制。
 *
 * 注意：本函数与 [buildEmojiInlineContent] 各自扫描一次文本。
 * 由于二者均被 `remember` 缓存（键为文本/尺寸），
 * 重复扫描只在文本变化时发生一次，不影响滚动重组性能。
 */
private fun buildEmojiAnnotatedString(text: String): AnnotatedString {
    val matches = CommentEmojiMap.TOKEN_REGEX.findAll(text)
        .filter { CommentEmojiMap.isKnown(it.groupValues[1]) }
        .toList()

    if (matches.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        matches.forEachIndexed { index, match ->
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            appendInlineContent(id = inlineId(index), alternateText = match.value)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

/**
 * 构建占位符 ID → 表情图片的映射。
 *
 * 每个出现位置使用独立 ID（`emoji_0`、`emoji_1`…），
 * 因此同一条评论里重复出现的同一表情也能各自正确渲染。
 */
private fun buildEmojiInlineContent(
    text: String,
    size: TextUnit
): Map<String, InlineTextContent> {
    val matches = CommentEmojiMap.TOKEN_REGEX.findAll(text)
        .filter { CommentEmojiMap.isKnown(it.groupValues[1]) }
        .toList()

    if (matches.isEmpty()) return emptyMap()

    return matches.mapIndexed { index, match ->
        val name = match.groupValues[1]
        inlineId(index) to InlineTextContent(
            placeholder = Placeholder(
                width = size,
                height = size,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            AsyncImage(
                model = CommentEmojiMap.urlFor(name),
                contentDescription = name,
                modifier = Modifier.fillMaxSize()
            )
        }
    }.toMap()
}

private fun inlineId(index: Int): String = "$INLINE_PREFIX$index"
