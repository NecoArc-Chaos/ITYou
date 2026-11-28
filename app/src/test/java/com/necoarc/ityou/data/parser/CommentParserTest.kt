package com.necoarc.ityou.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentParserTest {

    @Test
    fun parseComments_extractsHotAndNormalComments() {
        val sampleJson = """
            {
                "success": true,
                "content": {
                    "hotComments": [
                        {
                            "id": 76940865,
                            "city": "IT之家广东深圳网友",
                            "postTime": "2026-10-02T00:44:09.480",
                            "support": 11,
                            "against": 1,
                            "userInfo": {
                                "userNick": "Oo招笑红尘哥oO",
                                "userAvatar": "https://avatar.ithome.com/avatars/001/80/44/65_60.jpg"
                            },
                            "floorStr": "2楼1#",
                            "elements": [{ "type": 0, "content": "审美比比亚迪高多了" }],
                            "children": [],
                            "expandCount": 0
                        }
                    ],
                    "comments": [
                        {
                            "id": 76943078,
                            "city": "IT之家辽宁沈阳网友",
                            "postTime": "2026-10-02T11:24:38.897",
                            "support": 0,
                            "against": 0,
                            "userInfo": {
                                "userNick": "雅典娜之叹息",
                                "userAvatar": "https://avatar.ithome.com/avatars/001/21/98/31_60.jpg"
                            },
                            "floorStr": "10楼",
                            "elements": [{ "type": 0, "content": "主要是海外" }],
                            "children": [],
                            "expandCount": 0
                        },
                        {
                            "id": 76940813,
                            "city": "IT之家重庆网友",
                            "postTime": "2026-10-02T00:27:00.000",
                            "support": 4,
                            "against": 6,
                            "userInfo": { "userNick": "UniCon" },
                            "floorStr": "1楼",
                            "elements": [{ "type": 0, "content": "这个价位段算卖的不错的了" }],
                            "children": [],
                            "expandCount": 0
                        }
                    ]
                }
            }
        """.trimIndent()

        val page = CommentParser.parseComments(sampleJson)

        // 热门 + 普通合并
        assertEquals(3, page.comments.size)

        // 热门评论排在最前
        val hot = page.comments[0]
        assertEquals("76940865", hot.id)
        assertEquals("Oo招笑红尘哥oO", hot.author)
        assertEquals("https://avatar.ithome.com/avatars/001/80/44/65_60.jpg", hot.avatarUrl)
        assertEquals("广东深圳网友", hot.location) // 去掉 "IT之家" 前缀
        assertEquals("10-02 00:44", hot.publishTime) // 时间格式化
        assertEquals("2楼1#", hot.floor)
        assertEquals("审美比比亚迪高多了", hot.content)
        assertEquals(11, hot.supportCount)
        assertEquals(1, hot.againstCount)
    }

    @Test
    fun parseComments_parsesNestedChildrenAndExpandCount() {
        val sampleJson = """
            {
                "success": true,
                "content": {
                    "hotComments": [],
                    "comments": [
                        {
                            "id": 100,
                            "userInfo": { "userNick": "楼主" },
                            "elements": [{ "type": 0, "content": "主评论内容" }],
                            "support": 5,
                            "against": 0,
                            "expandCount": 3,
                            "children": [
                                {
                                    "id": 101,
                                    "userInfo": { "userNick": "回复者甲" },
                                    "replyUserInfo": { "userNick": "楼主" },
                                    "elements": [{ "type": 0, "content": "第一条回复" }],
                                    "support": 1,
                                    "against": 0,
                                    "children": []
                                },
                                {
                                    "id": 102,
                                    "userInfo": { "userNick": "回复者乙" },
                                    "replyUserInfo": { "userNick": "回复者甲" },
                                    "elements": [{ "type": 0, "content": "第二条回复" }],
                                    "support": 0,
                                    "against": 0,
                                    "children": []
                                }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()

        val page = CommentParser.parseComments(sampleJson)

        assertEquals(1, page.comments.size)
        val parent = page.comments[0]
        assertEquals("100", parent.id)
        assertEquals("主评论内容", parent.content)

        // 楼中楼内联解析
        assertEquals(2, parent.replies.size)
        assertEquals("回复者甲", parent.replies[0].author)
        assertEquals("第一条回复", parent.replies[0].content)
        assertEquals("回复者乙", parent.replies[1].author)

        // "回复 @某人" 昵称来自 replyUserInfo
        assertEquals("楼主", parent.replies[0].replyToAuthor)
        assertEquals("回复者甲", parent.replies[1].replyToAuthor)
        // 主评论本身不是回复
        assertNull(parent.replyToAuthor)

        // expandCount = 剩余未内联回复数
        assertEquals(3, parent.remainingReplyCount)
        assertEquals(5, parent.totalReplyCount)
    }

    @Test
    fun parseComments_mergesHotAndNormalAndDeduplicates() {
        // 热门与普通列表出现同一条评论时应去重（id 相同）
        val sampleJson = """
            {
                "success": true,
                "content": {
                    "hotComments": [
                        { "id": 1, "userInfo": { "userNick": "热门" }, "elements": [{ "type": 0, "content": "同时出现在热门与普通" }], "children": [] }
                    ],
                    "comments": [
                        { "id": 1, "userInfo": { "userNick": "热门" }, "elements": [{ "type": 0, "content": "同时出现在热门与普通" }], "children": [] },
                        { "id": 2, "userInfo": { "userNick": "普通" }, "elements": [{ "type": 0, "content": "仅普通评论" }], "children": [] }
                    ]
                }
            }
        """.trimIndent()

        val page = CommentParser.parseComments(sampleJson)

        assertEquals(2, page.comments.size)
        assertEquals("1", page.comments[0].id) // 热门在前
        assertEquals("2", page.comments[1].id)
    }

    @Test
    fun parseComments_handlesFailureAndEmpty() {
        // 接口失败：success != true
        assertTrue(CommentParser.parseComments("""{"success":false}""").comments.isEmpty())

        // 非法 JSON
        assertTrue(CommentParser.parseComments("not json").comments.isEmpty())

        // 空评论
        val empty = """{"success":true,"content":{"hotComments":[],"comments":[]}}"""
        val page = CommentParser.parseComments(empty)
        assertTrue(page.comments.isEmpty())
    }

    @Test
    fun parseCommentContent_extractsRepliesWithReplyToAuthor() {
        val sampleJson = """
            {
                "success": true,
                "content": {
                    "newsId": 1009494,
                    "comment": {
                        "id": 76951218,
                        "userInfo": { "userNick": "楼主" },
                        "elements": [{ "type": 0, "content": "主评论" }],
                        "children": [
                            {
                                "id": 76951333,
                                "city": "IT之家广东广州网友",
                                "postTime": "2026-10-03T16:44:10.530",
                                "support": 1,
                                "against": 0,
                                "userInfo": { "userNick": "沢弥" },
                                "replyUserInfo": { "userNick": "易团俊" },
                                "floorStr": "1#",
                                "elements": [{ "type": 0, "content": "怎么看出来是无线方案？" }],
                                "children": []
                            }
                        ]
                    }
                }
            }
        """.trimIndent()

        val replies = CommentParser.parseCommentContent(sampleJson)

        assertEquals(1, replies.size)
        val reply = replies[0]
        assertEquals("76951333", reply.id)
        assertEquals("沢弥", reply.author)
        assertEquals("易团俊", reply.replyToAuthor)
        assertEquals("怎么看出来是无线方案？", reply.content)
        assertEquals("广东广州网友", reply.location)
        assertEquals("10-03 16:44", reply.publishTime)
        assertEquals(1, reply.supportCount)
    }

    @Test
    fun parseCommentContent_handlesFailure() {
        assertTrue(CommentParser.parseCommentContent("""{"success":false}""").isEmpty())
        assertTrue(CommentParser.parseCommentContent("bad json").isEmpty())
        assertTrue(CommentParser.parseCommentContent("""{"success":true}""").isEmpty())
    }

    @Test
    fun parseComments_fallsBackToContentFieldWhenElementsMissing() {
        val sampleJson = """
            {
                "success": true,
                "content": {
                    "hotComments": [],
                    "comments": [
                        { "id": 1, "userInfo": { "userNick": "测试" }, "content": "带<strong>标签</strong>的正文", "children": [] }
                    ]
                }
            }
        """.trimIndent()

        val page = CommentParser.parseComments(sampleJson)
        assertEquals("带标签的正文", page.comments[0].content)
    }

    @Test
    fun parseComments_extractsEmbeddedImagesAndIgnoresImagePlaceholderText() {
        val sampleJson = """
            {
                "success": true,
                "content": {
                    "hotComments": [],
                    "comments": [
                        {
                            "id": 9901,
                            "userInfo": { "userNick": "图佬", "userAvatar": "//avatar.ithome.com/a.jpg" },
                            "elements": [
                                { "type": 0, "content": "真机上手图来了 [坏笑]" },
                                { "type": 1, "src": "2026/11/sample.jpg", "width": 800, "height": 600 },
                                { "type": 0, "content": "[图片]" }
                            ],
                            "children": []
                        }
                    ]
                }
            }
        """.trimIndent()

        val page = CommentParser.parseComments(sampleJson)
        assertEquals(1, page.comments.size)
        val comment = page.comments[0]
        assertEquals("https://avatar.ithome.com/a.jpg", comment.avatarUrl)
        assertEquals("真机上手图来了 [坏笑]", comment.content)
        assertEquals(1, comment.images.size)
        assertEquals("https://qimg.ithome.com/2026/11/sample.jpg", comment.images[0].url)
        assertEquals(800, comment.images[0].width)
        assertEquals(600, comment.images[0].height)
    }

    @Test
    fun parseImageUploadResponse_handlesNormalAndDoubleEncodedJson() {
        val standardJson = """
            {
                "success": true,
                "content": {
                    "url": "//qimg.ithome.com/2026/11/upload.png",
                    "width": 1080,
                    "height": 720,
                    "flag": 1
                }
            }
        """.trimIndent()

        val standard = CommentParser.parseImageUploadResponse(standardJson).getOrThrow()
        assertEquals("https://qimg.ithome.com/2026/11/upload.png", standard.url)
        assertEquals(1080, standard.width)
        assertEquals(720, standard.height)
        assertEquals(1, standard.flag)

        // 双重 JSON 字符串编码响应
        val doubleEncoded = "\"{\\\"success\\\":true,\\\"data\\\":{\\\"src\\\":\\\"2026/11/pic.jpg\\\",\\\"width\\\":400,\\\"height\\\":300}}\""
        val decoded = CommentParser.parseImageUploadResponse(doubleEncoded).getOrThrow()
        assertEquals("https://qimg.ithome.com/2026/11/pic.jpg", decoded.url)
        assertEquals(400, decoded.width)

        val failed = CommentParser.parseImageUploadResponse("""{"success":false,"errorMessage":"图片过大"}""")
        assertTrue(failed.isFailure)
        assertEquals("图片过大", failed.exceptionOrNull()?.message)
    }

    @Test
    fun parseSubmitResponse_and_parseVoteResponse_workCorrectly() {
        val submitJson = """
            {
                "success": true,
                "message": "评论发表成功",
                "content": {
                    "id": 88001,
                    "parentCommentId": 0,
                    "userInfo": { "userNick": "测试用户" },
                    "elements": [{ "type": 0, "content": "支持国产" }],
                    "support": 0,
                    "against": 0,
                    "children": []
                }
            }
        """.trimIndent()

        val submitted = CommentParser.parseSubmitResponse(submitJson).getOrThrow()
        assertEquals("评论发表成功", submitted.message)
        assertEquals("88001", submitted.comment?.id)
        assertEquals("支持国产", submitted.comment?.content)

        val voteCount = CommentParser.parseVoteResponse("""{"success":true,"message":"42"}""").getOrThrow()
        assertEquals(42, voteCount)

        val voteErr = CommentParser.parseVoteResponse("""{"success":false,"message":"请勿重复投票"}""")
        assertTrue(voteErr.isFailure)
        assertEquals("请勿重复投票", voteErr.exceptionOrNull()?.message)
    }

    @Test
    fun parseComments_extractsCanCommentAndCannotCommentTip() {
        val sampleJson = """
            {
                "success": true,
                "content": {
                    "canComment": false,
                    "cannotCommentTip": "等级达到【5级】即可发表评论哦，您还需414经验值，每天坚持登录、阅读新闻即可提升等级，详见《<a href=\"https://m.ithome.com/html/app/open.html?url=ithome%3A%2F%2Fopenurl%3Furl%3Dhttps%253A%252F%252Fimg.ithome.com%252Fapp%252Ftips%252Ftomylevel.html\" target=\"_blank\">我的等级</a>》。",
                    "hotComments": [],
                    "comments": []
                }
            }
        """.trimIndent()

        val page = CommentParser.parseComments(sampleJson)
        assertEquals(false, page.canComment)
        val notice = page.cannotCommentTip!!
        assertEquals(
            "等级达到【5级】即可发表评论哦，您还需414经验值，每天坚持登录、阅读新闻即可提升等级，详见《我的等级》。",
            notice.text
        )
        assertEquals(1, notice.links.size)
        assertEquals("我的等级", notice.links[0].text)
        assertEquals("https://img.ithome.com/app/tips/tomylevel.html", notice.links[0].url)
    }
}
