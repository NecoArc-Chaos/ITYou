package com.necoarc.ityou.ui.screens.detail.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.necoarc.ityou.data.model.ArticleComment
import com.necoarc.ityou.ui.components.CommentEmojiText
import com.necoarc.ityou.ui.theme.Motion
import com.necoarc.ityou.ui.theme.ShapeCache

@Composable
fun CommentItem(
    comment: ArticleComment,
    isExpandingReplies: Boolean = false,
    onExpandReplies: () -> Unit = {}
) {
    // 楼中楼默认折叠，点击「展开 N 条回复」才渲染，避免长评论串一次性铺满
    var repliesExpanded by remember(comment.id) { mutableStateOf(false) }

    // 剩余回复加载完成后（remainingReplyCount 由 >0 变为 0）自动展开
    var previousRemaining by remember(comment.id) { mutableStateOf(comment.remainingReplyCount) }
    LaunchedEffect(comment.id, comment.remainingReplyCount) {
        val current = comment.remainingReplyCount
        if (previousRemaining > 0 && current == 0 && comment.replies.isNotEmpty()) {
            repliesExpanded = true
        }
        previousRemaining = current
    }

    Surface(
        shape = ShapeCache.smooth16,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            CommentRow(comment = comment)

            // 楼中楼：有回复时展示折叠入口
            if (comment.replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp)
                ) {
                    ReplyToggleButton(
                        expanded = repliesExpanded,
                        count = comment.replies.size,
                        onClick = { repliesExpanded = !repliesExpanded }
                    )

                    AnimatedVisibility(
                        visible = repliesExpanded,
                        enter = expandVertically(animationSpec = Motion.expandSize) +
                            fadeIn(animationSpec = Motion.fadeInSpec),
                        exit = shrinkVertically(animationSpec = Motion.expandSize) +
                            fadeOut(animationSpec = Motion.fadeInSpec)
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))
                            comment.replies.forEach { reply ->
                                Surface(
                                    shape = ShapeCache.smooth12,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Box(modifier = Modifier.padding(10.dp)) {
                                        CommentRow(comment = reply, compact = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 服务端提示仍有更多未内联回复：点击按需加载
            if (comment.remainingReplyCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.padding(start = 12.dp)) {
                    if (isExpandingReplies) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "加载回复中…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        Surface(
                            shape = ShapeCache.smoothPill,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable(
                                enabled = !isExpandingReplies,
                                onClick = onExpandReplies
                            )
                        ) {
                            Text(
                                text = "展开另外 ${comment.remainingReplyCount} 条回复",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentRow(comment: ArticleComment, compact: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        // 头像
        val avatarSize = if (compact) 26.dp else 36.dp
        if (comment.avatarUrl != null) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = comment.author,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(ShapeCache.smoothPill)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(ShapeCache.smoothPill)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.author.take(1),
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            // 昵称 + 楼层
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = comment.author,
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (comment.floor.isNotEmpty()) {
                    Text(
                        text = comment.floor,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 正文（可选中复制）
            if (comment.content.isNotEmpty() || comment.replyToAuthor != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val bodyStyle = if (compact) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                }
                SelectionContainer {
                    if (comment.replyToAuthor != null) {
                        Row {
                            Text(
                                text = "回复 @${comment.replyToAuthor}：",
                                style = bodyStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            CommentEmojiText(
                                text = comment.content,
                                style = bodyStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        CommentEmojiText(
                            text = comment.content,
                            style = bodyStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 元信息：时间 / 点赞 / 反对
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (comment.location.isNotEmpty() && !compact) {
                    Text(
                        text = comment.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (comment.publishTime.isNotEmpty()) {
                    Text(
                        text = comment.publishTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (comment.supportCount > 0) {
                    Text(
                        text = "赞 ${comment.supportCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (comment.againstCount > 0 && !compact) {
                    Text(
                        text = "踩 ${comment.againstCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun ReplyToggleButton(
    expanded: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = ShapeCache.smoothPill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(animationSpec = Motion.fadeInSpec) togetherWith
                    fadeOut(animationSpec = Motion.fadeInSpec)
            },
            label = "replyToggleLabel"
        ) { isExpanded ->
            Text(
                text = if (isExpanded) "收起回复" else "展开 $count 条回复",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun CommentLoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "评论加载中…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun CommentErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "评论加载失败",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onRetry,
            shape = ShapeCache.smoothPill
        ) {
            Text(text = "重试", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun CommentEmptyState() {
    Text(
        text = "暂无评论",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        textAlign = TextAlign.Center
    )
}
