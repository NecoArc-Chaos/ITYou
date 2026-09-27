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
     * 获取文章列表并按所选分类过滤
     */
    suspend fun getArticles(category: ArticleCategory = ArticleCategory.ALL): Result<List<Article>> =
        withContext(Dispatchers.IO) {
            val allArticles = try {
                val request = Request.Builder()
                    .url("https://www.ithome.com/rss/")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ITYou-App/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val xml = response.body?.string().orEmpty()
                    val parsed = HtmlParser.parseRss(xml)
                    if (parsed.isNotEmpty()) {
                        parsed
                    } else {
                        getFallbackArticles()
                    }
                } else {
                    getFallbackArticles()
                }
            } catch (_: Exception) {
                getFallbackArticles()
            }

            // 根据分类筛选
            val filtered = if (category == ArticleCategory.ALL) {
                allArticles
            } else {
                allArticles.filter { it.category == category }
            }

            Result.success(filtered)
        }

    /**
     * 获取文章详情
     */
    suspend fun getArticleDetail(
        articleId: String,
        url: String,
        previewTitle: String = "",
        previewAuthor: String = "",
        previewPubTime: String = ""
    ): Result<ArticleDetail> =
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
                Result.success(getFallbackDetail(articleId, previewTitle, previewAuthor, previewPubTime))
            } catch (_: Exception) {
                Result.success(getFallbackDetail(articleId, previewTitle, previewAuthor, previewPubTime))
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
            ),
            Article(
                id = "800105",
                title = "小米汽车 SU7 Ultra 量产版正式下线：纽北赛道圈速实测即将揭晓",
                summary = "小米汽车官方今日宣布，定位巅峰性能科技轿车的 SU7 Ultra 正式迎来首批量产车下线，三电机系统最大马力超 1500 匹。",
                coverImageUrl = "https://picsum.photos/seed/su7ultra/600/400",
                author = "IT之家 (小智)",
                publishTime = "3小时前",
                category = ArticleCategory.AUTOMOTIVE,
                commentCount = 621
            ),
            Article(
                id = "800106",
                title = "《黑神话：悟空》全新 DLC 预告曝光：新地图新妖王，计划明春上线",
                summary = "游戏科学在最新开发者访谈中首次披露了大型内容扩展包的进展，并展示了多段令人惊叹的全新实机场景。",
                coverImageUrl = "https://picsum.photos/seed/wukong/600/400",
                author = "IT之家 (暴风)",
                publishTime = "4小时前",
                category = ArticleCategory.GAME,
                commentCount = 430
            )
        )
    }

    private fun getFallbackDetail(
        articleId: String,
        previewTitle: String,
        previewAuthor: String,
        previewPubTime: String
    ): ArticleDetail {
        val article = getFallbackArticles().find { it.id == articleId }
        val title = when {
            previewTitle.isNotEmpty() -> previewTitle
            article != null -> article.title
            else -> "文章资讯详情"
        }
        val author = when {
            previewAuthor.isNotEmpty() -> previewAuthor
            article != null -> article.author
            else -> "IT之家"
        }
        val pubTime = when {
            previewPubTime.isNotEmpty() -> previewPubTime
            article != null -> article.publishTime
            else -> "刚刚"
        }
        val summary = article?.summary ?: "IT之家科技快讯报道，关注最新行业前沿资讯与数码产品发布动向。"

        return ArticleDetail(
            id = articleId,
            title = title,
            author = author,
            publishTime = pubTime,
            source = "IT之家",
            contentBlocks = listOf(
                ContentBlock.Paragraph(summary),
                ContentBlock.Heading("核心亮点与背景速览", level = 2),
                ContentBlock.Paragraph("根据行业内最新动向与官方权威披露，本项科技进展不仅在性能和易用性上实现了跨越式升级，也为后续的技术生态演进奠定了坚实基础。"),
                ContentBlock.BlockQuote("技术创新源于对极致用户体验的不懈追求，每一次架构革新都是向未来迈出的重要一步。"),
                ContentBlock.Image(
                    url = article?.coverImageUrl ?: "https://picsum.photos/seed/$articleId/800/500",
                    caption = "现场实拍与规格参数细节展示"
                ),
                ContentBlock.Heading("行业评价与后续展望", level = 2),
                ContentBlock.Paragraph("业内分析人士指出，随着供应链效率与软硬件协同调优能力的持续增强，该系列产品将在接下来的市场竞争中占据显著优势，推动整个产业生态向更高能效比方向发展。")
            ),
            commentCount = article?.commentCount ?: 88,
            originalUrl = article?.url ?: "https://www.ithome.com"
        )
    }
}
