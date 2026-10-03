package com.necoarc.ityou.data.parser

import androidx.compose.runtime.Immutable
import com.necoarc.ityou.data.model.ArticleComment
import org.json.JSONObject

/**
 * 评论解析结果：一页评论 + 下一页游标 + 本次请求使用的 `sn` 令牌。
 *
 * @param comments 本页评论（含内联楼中楼）
 * @param nextCursor 下一页游标 = 本页最后一条评论 id；为空字符串表示没有更多
 * @param sn 本次请求使用的 sn 令牌（供后续「加载剩余回复」复用，避免重复抓页面）
 */
@Immutable
data class CommentPageResult(
    val comments: List<ArticleComment> = emptyList(),
    val nextCursor: String = "",
    val sn: String = ""
)

/**
 * IT之家 PC 端评论接口的响应解析器。
 *
 * 接口：
 * - 列表：`/api/webcomment/getnewscomment?sn={sn}&cid={cursor}&isInit=true`
 * - 回复：`/api/webcomment/getcommentcontent?commentId={id}&sn={sn}`
 *
 * 响应结构：
 * ```json
 * { "success": true, "content": { "comments": [...], "hotComments": [...] } }
 * ```
 * 单条评论形如 `{ id, city, postTime, support, against, userInfo, replyUserInfo,
 * floorStr, elements: [{type:0, content}], children: [...], expandCount }`。
 *
 * 语义（已实测确认）：
 * - `cid = X` 返回 id 小于 X 的评论（更旧）；`cid = 0` 返回最新一页。
 * - `children` 为楼中楼回复；`expandCount > 0` 表示仍有未内联的回复。
 */
object CommentParser {

    /**
     * 解析评论列表响应（含分页游标）。
     *
     * @param jsonString API 返回的原始 JSON 字符串
     * @param isFirstPage 是否首页。首页会合并 `hotComments`（热门）与 `comments`；
     *                    后续页仅有 `comments`，不做热门合并。
     */
    fun parseComments(
        jsonString: String,
        isFirstPage: Boolean = true
    ): CommentPageResult {
        return try {
            val root = JSONObject(jsonString)
            if (!root.optBoolean("success")) return CommentPageResult()
            val content = root.optJSONObject("content") ?: return CommentPageResult()

            val comments = mutableListOf<ArticleComment>()

            // 1. 热门评论（仅首页）
            if (isFirstPage) {
                val hot = content.optJSONArray("hotComments")
                if (hot != null) {
                    for (i in 0 until hot.length()) {
                        val item = hot.optJSONObject(i) ?: continue
                        parseCommentNode(item)?.let(comments::add)
                    }
                }
            }

            // 2. 普通评论
            val list = content.optJSONArray("comments")
            val rawComments = mutableListOf<JSONObject>()
            if (list != null) {
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    rawComments.add(item)
                    parseCommentNode(item)?.let { comment ->
                        // 去重：热门已返回的评论不再重复显示
                        if (comments.none { it.id == comment.id }) {
                            comments.add(comment)
                        }
                    }
                }
            }

            // 3. 游标 = 本页最后一条（最旧）评论 id
            val nextCursor = rawComments.lastOrNull()?.optString("id").orEmpty()

            CommentPageResult(comments = comments, nextCursor = nextCursor)
        } catch (_: Exception) {
            CommentPageResult()
        }
    }

    /**
     * 解析 `getcommentcontent` 响应，得到某条评论的完整回复列表。
     *
     * @return 回复列表；解析失败返回空列表
     */
    fun parseCommentContent(jsonString: String): List<ArticleComment> {
        return try {
            val root = JSONObject(jsonString)
            if (!root.optBoolean("success")) return emptyList()
            val content = root.optJSONObject("content") ?: return emptyList()
            val comment = content.optJSONObject("comment") ?: return emptyList()
            val children = comment.optJSONArray("children") ?: return emptyList()

            val replies = mutableListOf<ArticleComment>()
            for (i in 0 until children.length()) {
                val child = children.optJSONObject(i) ?: continue
                parseCommentNode(child)?.let(replies::add)
            }
            replies
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCommentNode(node: JSONObject): ArticleComment? {
        val id = node.optString("id")
        if (id.isEmpty()) return null

        val userInfo = node.optJSONObject("userInfo")
        val replyUserInfo = node.optJSONObject("replyUserInfo")

        // 楼中楼：children 内联回复（递归解析）
        val children = node.optJSONArray("children")
        val replies = mutableListOf<ArticleComment>()
        if (children != null) {
            for (i in 0 until children.length()) {
                val child = children.optJSONObject(i) ?: continue
                parseCommentNode(child)?.let(replies::add)
            }
        }

        return ArticleComment(
            id = id,
            author = userInfo?.optString("userNick").orEmpty().ifEmpty { "匿名用户" },
            avatarUrl = userInfo?.optString("userAvatar").orEmpty().ifEmpty { null },
            location = node.optString("city").removePrefix("IT之家").trim(),
            publishTime = formatCommentTime(node.optString("postTime")),
            floor = node.optString("floorStr"),
            content = extractContent(node),
            supportCount = node.optInt("support"),
            againstCount = node.optInt("against"),
            replies = replies,
            remainingReplyCount = node.optInt("expandCount", 0).coerceAtLeast(0),
            replyToAuthor = replyUserInfo?.optString("userNick").orEmpty().ifEmpty { null }
        )
    }

    /**
     * 从 `elements` 中拼接正文（仅取 `type == 0` 的文本片段）。
     * 兼容历史字段：若无 elements，则回退到 `content` 字段。
     */
    private fun extractContent(node: JSONObject): String {
        val elements = node.optJSONArray("elements")
        if (elements != null) {
            val builder = StringBuilder()
            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                if (el.optInt("type") == 0) {
                    builder.append(el.optString("content"))
                }
            }
            return builder.toString().trim()
        }
        return node.optString("content")
            .replace("<[^>]+>".toRegex(), "")
            .trim()
    }

    /**
     * 将 PC 接口时间格式统一为 `10-03 16:44`。
     * 支持 `2026-10-03T16:44:10.530` 与 `2026-10-03 16:44:10`。
     */
    private fun formatCommentTime(raw: String): String {
        if (raw.length < 16) return raw
        return try {
            val datePart = raw.substring(5, 10)   // MM-dd
            val timePart = raw.substring(11, 16)  // HH:mm
            "$datePart $timePart"
        } catch (_: Exception) {
            raw
        }
    }
}
