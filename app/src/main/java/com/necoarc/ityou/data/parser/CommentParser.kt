package com.necoarc.ityou.data.parser

import androidx.compose.runtime.Immutable
import com.necoarc.ityou.data.model.ArticleComment
import com.necoarc.ityou.data.model.CommentImage
import com.necoarc.ityou.data.model.CommentSubmitResult
import com.necoarc.ityou.data.model.InlineLink
import com.necoarc.ityou.data.model.UploadedCommentImage
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 含可点击超链接的纯文本通知（用于把服务端 HTML 提示转为干净原生可点击富文本）。
 */
@Immutable
data class RichNotice(
    val text: String,
    val links: List<InlineLink> = emptyList()
)

/**
 * 评论解析结果：全部评论 + 本次请求使用的 `sn` 令牌。
 */
@Immutable
data class CommentPageResult(
    val comments: List<ArticleComment> = emptyList(),
    val sn: String = "",
    val canComment: Boolean = true,
    val cannotCommentTip: RichNotice? = null
)

/**
 * IT之家 PC 端评论接口的响应解析器。
 *
 * 接口：
 * - 列表：`/api/webcomment/getnewscomment?sn={sn}&cid={cursor}&isInit=true`
 * - 回复：`/api/webcomment/getcommentcontent?commentId={id}&sn={sn}`
 * - 发评：`/apiv2/comment/submit?appver=900`
 * - 传图：`/api/image/uploadimage`
 * - 投票：`/api/comment/vote` & `/api/comment/cancelvote`
 */
object CommentParser {

    private const val ELEMENT_TYPE_TEXT = 0
    private const val ELEMENT_TYPE_IMAGE = 1
    private const val ELEMENT_TYPE_LINK = 2
    private const val ELEMENT_TYPE_TOPIC = 3
    private const val ELEMENT_TYPE_AT = 4

    /**
     * 解析评论列表响应。
     */
    fun parseComments(jsonString: String): CommentPageResult {
        return try {
            val root = JSONObject(jsonString)
            if (!root.optBoolean("success")) return CommentPageResult()
            val content = root.optJSONObject("content") ?: return CommentPageResult()

            val comments = mutableListOf<ArticleComment>()

            // 1. 热门评论
            val hot = content.optJSONArray("hotComments")
            if (hot != null) {
                for (i in 0 until hot.length()) {
                    val item = hot.optJSONObject(i) ?: continue
                    parseCommentNode(item)?.let(comments::add)
                }
            }

            // 2. 普通评论（与热门去重）
            val list = content.optJSONArray("comments")
            if (list != null) {
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    parseCommentNode(item)?.let { comment ->
                        if (comments.none { it.id == comment.id }) {
                            comments.add(comment)
                        }
                    }
                }
            }

            val canComment = content.optBoolean("canComment", true)
            val rawTip = content.optString("cannotCommentTip")
                .trim()
                .takeIf { it.isNotEmpty() && it != "null" }
            val cannotCommentTip = rawTip?.let { parseRichNoticeHtml(it) }
                ?.takeIf { it.text.isNotBlank() }

            CommentPageResult(
                comments = comments,
                canComment = canComment,
                cannotCommentTip = cannotCommentTip
            )
        } catch (_: Exception) {
            CommentPageResult()
        }
    }

    /**
     * 解析 `getcommentcontent` 响应，得到某条评论的完整回复列表。
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

    /**
     * 解析服务端返回的 HTML 提示文本（如 `cannotCommentTip`），
     * 剥离原始 `<a ...>` 标签并将双层 App 唤醒链接解包为直接可点击的 HTTPS 地址。
     */
    fun parseRichNoticeHtml(rawHtml: String): RichNotice {
        val trimmed = rawHtml.trim()
        if (trimmed.isEmpty()) return RichNotice("")
        if (!trimmed.contains('<')) return RichNotice(trimmed)

        return runCatching {
            val doc = Jsoup.parseBodyFragment(trimmed)
            val links = doc.select("a[href]").mapNotNull { a ->
                val label = a.text().trim()
                val href = unwrapAppLink(a.attr("href").trim())
                if (label.isNotEmpty() && href.startsWith("http")) {
                    InlineLink(text = label, url = href)
                } else {
                    null
                }
            }
            val cleanText = doc.body().text().replace("\\s+".toRegex(), " ").trim()
            RichNotice(text = cleanText, links = links)
        }.getOrElse {
            RichNotice(trimmed.replace("<[^>]+>".toRegex(), "").trim())
        }
    }

    /**
     * 解包 `open.html?url=ithome://openurl?url=...` 等 App 跳转包裹链接，提取真实 HTTPS 地址。
     */
    internal fun unwrapAppLink(rawUrl: String): String {
        var current = rawUrl.trim()
        for (depth in 0 until 3) {
            val queryPart = current.substringAfter("url=", missingDelimiterValue = "")
            if (queryPart.isEmpty()) break
            val decoded = runCatching {
                URLDecoder.decode(queryPart.substringBefore('&'), StandardCharsets.UTF_8.toString())
            }.getOrNull() ?: break
            if (decoded == current) break
            current = decoded
        }
        return current
    }

    /**
     * 解析发评响应（`/apiv2/comment/submit`）。
     */
    fun parseSubmitResponse(jsonString: String): Result<CommentSubmitResult> {
        return try {
            val root = JSONObject(jsonString)
            if (!root.optBoolean("success")) {
                val rawMsg = root.optString("message").ifBlank { "发布失败，请稍后重试" }
                return Result.failure(IllegalStateException(rawMsg))
            }
            val contentNode = root.optJSONObject("content")
            val comment = contentNode?.let { parseCommentNode(it) }
            val parentId = contentNode?.optString("parentCommentId")
                ?.takeIf { it.isNotBlank() && it != "0" }
                .orEmpty()
            val message = root.optString("message").ifBlank { "发布成功" }
            Result.success(
                CommentSubmitResult(
                    comment = comment,
                    message = message,
                    parentCommentId = parentId
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 解析图片上传响应（`/api/image/uploadimage`）。
     *
     * 兼容 `comment.js` 中的双层 JSON 字符串编码场景（最多解包 2 次）。
     */
    fun parseImageUploadResponse(rawResponse: String): Result<UploadedCommentImage> {
        return try {
            var currentText = rawResponse.trim()
            var root: JSONObject? = null
            for (attempt in 0..2) {
                if (currentText.startsWith("{")) {
                    root = JSONObject(currentText)
                    break
                }
                if (currentText.startsWith("\"")) {
                    // JSONObject 不直接支持顶层字符串，包一层再取出内层字符串
                    currentText = JSONObject("{\"v\":$currentText}").optString("v").trim()
                } else {
                    break
                }
            }

            if (root == null) {
                return Result.failure(IllegalStateException("图片上传响应格式异常"))
            }
            if (!root.optBoolean("success")) {
                val errMsg = root.optString("errorMessage")
                    .ifBlank { root.optString("message") }
                    .ifBlank { "图片上传失败" }
                return Result.failure(IllegalStateException(errMsg))
            }

            val imageObj = root.optJSONObject("content")
                ?: root.optJSONObject("data")
                ?: root.optJSONObject("result")

            val rawUrl = when {
                imageObj != null -> imageObj.optString("url").ifBlank { imageObj.optString("src") }
                else -> root.optString("content").ifBlank { root.optString("data") }
            }

            val normalized = normalizeCommentImageUrl(rawUrl)
            if (normalized.isBlank()) {
                return Result.failure(IllegalStateException("图片地址为空"))
            }

            Result.success(
                UploadedCommentImage(
                    url = normalized,
                    width = imageObj?.optInt("width", 0) ?: 0,
                    height = imageObj?.optInt("height", 0) ?: 0,
                    flag = imageObj?.optInt("flag", 0) ?: 0
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 解析赞 / 踩 / 取消投票响应（`/api/comment/vote` & `/api/comment/cancelvote`）。
     */
    fun parseVoteResponse(jsonString: String): Result<Int?> {
        return try {
            val root = JSONObject(jsonString)
            if (!root.optBoolean("success")) {
                val msg = root.optString("message").ifBlank { "操作失败" }
                return Result.failure(IllegalStateException(msg))
            }
            val updatedCount = root.optString("message").trim().toIntOrNull()
            Result.success(updatedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 规范化评论图片地址（对齐 `comment.js#normalizeCommentImageUrl`）。
     */
    fun normalizeCommentImageUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        val url = rawUrl.trim()
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> "https://qimg.ithome.com/${url.trimStart('/')}"
        }
    }

    /**
     * 构造 `/apiv2/comment/submit` 的 `elements` JSON 数组。
     */
    fun buildSubmitElementsJson(
        text: String,
        images: List<UploadedCommentImage>
    ): JSONArray {
        val elements = JSONArray()
        val trimmedText = text.trim()
        if (trimmedText.isNotEmpty()) {
            elements.put(
                JSONObject().apply {
                    put("type", ELEMENT_TYPE_TEXT)
                    put("content", trimmedText)
                }
            )
        }
        images.forEach { image ->
            elements.put(
                JSONObject().apply {
                    put("type", ELEMENT_TYPE_IMAGE)
                    put("src", image.url)
                    put("width", image.width)
                    put("height", image.height)
                    put("flag", image.flag)
                }
            )
        }
        return elements
    }

    internal fun parseCommentNode(node: JSONObject): ArticleComment? {
        val id = node.optString("id")
        if (id.isEmpty()) return null

        val userInfo = node.optJSONObject("userInfo")
        val replyUserInfo = node.optJSONObject("replyUserInfo")

        val children = node.optJSONArray("children")
        val replies = mutableListOf<ArticleComment>()
        if (children != null) {
            for (i in 0 until children.length()) {
                val child = children.optJSONObject(i) ?: continue
                parseCommentNode(child)?.let(replies::add)
            }
        }

        val rawAvatar = userInfo?.optString("userAvatar").orEmpty().trim()
        val avatarUrl = when {
            rawAvatar.isEmpty() || rawAvatar == "null" -> null
            rawAvatar.startsWith("//") -> "https:$rawAvatar"
            else -> rawAvatar
        }

        return ArticleComment(
            id = id,
            author = userInfo?.optString("userNick").orEmpty().ifEmpty { "匿名用户" },
            avatarUrl = avatarUrl,
            location = node.optString("city").removePrefix("IT之家").trim(),
            publishTime = formatCommentTime(node.optString("postTime")),
            floor = node.optString("floorStr"),
            content = extractContent(node),
            supportCount = node.optInt("support"),
            againstCount = node.optInt("against"),
            images = extractImages(node),
            isSupported = node.optBoolean("isSupport", false) || node.optBoolean("isAgreed", false),
            isAgainst = node.optBoolean("isAgainst", false),
            replies = replies,
            remainingReplyCount = node.optInt("expandCount", 0).coerceAtLeast(0),
            replyToAuthor = replyUserInfo?.optString("userNick").orEmpty().ifEmpty { null }
        )
    }

    /**
     * 从 `elements` 中拼接正文（支持 Text / Link / Topic / At 等文本片段）。
     * 兼容历史字段：若无 elements，则回退到 `content` 字段。
     */
    private fun extractContent(node: JSONObject): String {
        val elements = node.optJSONArray("elements")
        if (elements != null) {
            val builder = StringBuilder()
            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                when (el.optInt("type", -1)) {
                    ELEMENT_TYPE_TEXT,
                    ELEMENT_TYPE_LINK,
                    ELEMENT_TYPE_TOPIC,
                    ELEMENT_TYPE_AT -> {
                        val segment = el.optString("content")
                        if (segment != "[图片]") {
                            builder.append(segment)
                        }
                    }
                }
            }
            return builder.toString().trim()
        }
        return node.optString("content")
            .replace("<[^>]+>".toRegex(), "")
            .trim()
    }

    /**
     * 提取 `elements` 中 `type == 1` 的评论附图。
     */
    private fun extractImages(node: JSONObject): List<CommentImage> {
        val elements = node.optJSONArray("elements") ?: return emptyList()
        val images = mutableListOf<CommentImage>()
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            if (el.optInt("type", -1) == ELEMENT_TYPE_IMAGE) {
                val src = normalizeCommentImageUrl(
                    el.optString("src").ifBlank { el.optString("url") }
                )
                if (src.isNotBlank()) {
                    images.add(
                        CommentImage(
                            url = src,
                            width = el.optInt("width", 0),
                            height = el.optInt("height", 0)
                        )
                    )
                }
            }
        }
        return images
    }

    /**
     * 将 PC 接口时间格式统一为 `10-03 16:44`。
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
