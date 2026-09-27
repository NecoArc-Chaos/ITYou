package com.necoarc.ityou.data.repository

import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
import com.necoarc.ityou.data.parser.HtmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ArticleRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /**
     * 获取文章列表：优先尝试解析 IT 之家 RSS，网络异常时回退到精选文章列表
     */
    suspend fun getArticles(category: ArticleCategory = ArticleCategory.ALL): Result<List<Article>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://www.ithome.com/rss/")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ITYou-App/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val xml = response.body?.string().orEmpty()
                    val parsed = HtmlParser.parseRss(xml)
                    if (parsed.isNotEmpty()) {
                        return@withContext Result.success(parsed)
                    }
                }
                Result.success(getFallbackArticles())
            } catch (e: Exception) {
                Result.success(getFallbackArticles())
            }
        }

    /**
     * 获取文章详情
     */
    suspend fun getArticleDetail(articleId: String, url: String): Result<ArticleDetail> =
        withContext(Dispatchers.IO) {
            try {
                if (url.isNotEmpty()) {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ITYou-App/1.0")
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val html = response.body?.string().orEmpty()
                        val detail = HtmlParser.parseArticleDetail(html, articleId, url)
                        if (detail.contentBlocks.isNotEmpty()) {
                            return@withContext Result.success(detail)
                        }
                    }
                }
                Result.success(getFallbackDetail(articleId))
            } catch (e: Exception) {
                Result.success(getFallbackDetail(articleId))
            }
        }

    private fun getFallbackArticles(): List<Article> {
        return listOf(
            Article(
                id = "800101",
                title = "谷歌正式推送 Android 16 预览版：Material 3 Expressive 带来大圆角与更灵动的动效体验",
                summary = "今天谷歌向全球开发者推送了全新的 Android 16 开发者预览版本，不仅带来了底层性能提升，还全面落地了 Material 3 Expressive 设计规范。",
                coverImageUrl = "https://picsum.photos/seed/android16/600/400",
                author = "IT之家 (远洋)",
                publishTime = "10分钟前",
                category = ArticleCategory.DIGITAL,
                commentCount = 142
            ),
            Article(
                id = "800102",
                title = "英伟达发布全新架构 RTX 50 系列显卡：光线追踪性能翻倍，功耗表现抢眼",
                summary = "黄仁勋在最新发布会上正式揭晓了 RTX 50 系列显卡，采用台积电定制先进工艺制程，能效比取得重大突破。",
                coverImageUrl = "https://picsum.photos/seed/rtx50/600/400",
                author = "IT之家 (孤城)",
                publishTime = "42分钟前",
                category = ArticleCategory.PC,
                commentCount = 388
            ),
            Article(
                id = "800103",
                title = "DeepSeek 发布新一代开源推理大模型：多项基准测试超越前代，支持手机本地运行",
                summary = "国内知名开源人工智能团队今日开源了全新推理架构模型，模型体积缩减 60%，在移动端 NPU 上可以实现高帧率推理。",
                coverImageUrl = "https://picsum.photos/seed/deepseek/600/400",
                author = "IT之家 (刺猬)",
                publishTime = "1小时前",
                category = ArticleCategory.AI,
                commentCount = 512
            ),
            Article(
                id = "800104",
                title = "苹果 iOS 19 首批爆料出炉：Siri 迎来全面重构，UI 视觉语言进一步圆润化",
                summary = "知名科技博主透露，iOS 19 将彻底革新系统视觉，采用更大更醒目的微件系统，与 Apple Intelligence 进行深度绑定。",
                coverImageUrl = "https://picsum.photos/seed/apple/600/400",
                author = "IT之家 (玄度)",
                publishTime = "2小时前",
                category = ArticleCategory.SMARTPHONE,
                commentCount = 205
            )
        )
    }

    private fun getFallbackDetail(articleId: String): ArticleDetail {
        return ArticleDetail(
            id = articleId,
            title = "谷歌正式推送 Android 16 预览版：Material 3 Expressive 带来大圆角与更灵动的动效体验",
            author = "IT之家 (远洋)",
            publishTime = "2026-09-27 11:30",
            source = "IT之家",
            contentBlocks = listOf(
                ContentBlock.Paragraph("IT之家 9 月 27 日消息，谷歌今天面向开发者推出了新一代移动操作系统 Android 16 的首个预览版本。"),
                ContentBlock.Heading("Material 3 Expressive 全面落地", level = 2),
                ContentBlock.Paragraph("在本次更新中，视觉层面最显著的改变就是 Material 3 Expressive（表现力设计语言）的全面集成。"),
                ContentBlock.BlockQuote("Expressive 设计语言让界面从克制走向充满生命力，通过更自然的弹性动效、更大的几何圆角，打造更愉悦的用户体验。"),
                ContentBlock.Image(
                    url = "https://picsum.photos/seed/android16_inner/800/500",
                    caption = "全新 Expressive 控件与大圆角交互卡片"
                ),
                ContentBlock.Heading("核心底层与多核调度优化", level = 2),
                ContentBlock.Paragraph("除 UI 表现力升级外，Android 16 进一步重构了 ART 运行时的垃圾回收机制，使后台内存占用降低约 18%，应用冷启动速度提高 20% 以上。")
            ),
            commentCount = 142,
            originalUrl = "https://www.ithome.com"
        )
    }
}
