package com.necoarc.ityou.data.share

import android.content.Context
import android.content.Intent

/**
 * 文章分享。
 *
 * 使用系统分享面板（[Intent.ACTION_SEND]）而非内置分享 UI：
 * 用户已熟悉系统面板，且能直接复用设备上已安装的任意应用作为分享目标，
 * 无需申请额外权限、也无需引入第三方 SDK。
 */
object ArticleSharer {

    /**
     * 拉起系统分享面板。
     *
     * @param title 文章标题，作为分享文本的首行
     * @param url 文章链接
     */
    fun share(context: Context, title: String, url: String) {
        val text = buildShareText(title = title, url = url)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE_TEXT
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startChooser(context, intent, title)
    }

    /**
     * 构造分享文本。
     *
     * 有标题时形如「标题\n链接」，让接收方能立刻看懂内容；
     * 标题为空（详情尚未加载完）时退化为只分享链接。
     */
    internal fun buildShareText(title: String, url: String): String {
        val trimmedTitle = title.trim()
        return if (trimmedTitle.isEmpty()) url else "$trimmedTitle\n$url"
    }

    private fun startChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, chooserTitle(title)).apply {
            // 从非 Activity 上下文发起时必须带 NEW_TASK，
            // 否则在部分机型上会抛 AndroidRuntimeException。
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(chooser)
        }
    }

    private fun chooserTitle(title: String): String =
        if (title.isBlank()) "分享文章" else "分享：$title"

    private const val MIME_TYPE_TEXT = "text/plain"
}
