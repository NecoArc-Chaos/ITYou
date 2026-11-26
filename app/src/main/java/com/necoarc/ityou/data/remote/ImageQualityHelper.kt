package com.necoarc.ityou.data.remote

/**
 * 图片画质解析工具：让设置中的「高清图片模式」真实生效。
 *
 * - 开启高清模式时：剔除 IT之家 图床可能附带的缩略图/压缩参数（`x-bce-process`），
 *   尽可能拉取原图；
 * - 关闭高清模式（省流模式）时：对 IT之家 图床（`img.ithome.com`）追加适配移动端屏幕的
 *   轻量缩放与压缩参数，降低列表和正文流量占用。
 */
object ImageQualityHelper {

    private const val ITHOME_IMG_HOST = "img.ithome.com"
    private const val BCE_PROCESS_PARAM = "x-bce-process="
    private const val PROCESS_THUMBNAIL = "x-bce-process=image/resize,w_360/quality,q_75"
    private const val PROCESS_DETAIL_COMPACT = "x-bce-process=image/resize,w_720/quality,q_80"

    fun resolveUrl(
        rawUrl: String?,
        highQuality: Boolean,
        isThumbnail: Boolean = false
    ): String? {
        if (rawUrl.isNullOrBlank()) return null
        val url = rawUrl.trim()
        if (!url.startsWith("http")) return url
        if (!url.contains(ITHOME_IMG_HOST, ignoreCase = true)) return url

        // 剥离既有的 x-bce-process 参数，避免重复叠加
        val stripped = stripQueryParam(url, BCE_PROCESS_PARAM)
        if (highQuality) {
            return stripped
        }

        val processParam = if (isThumbnail) PROCESS_THUMBNAIL else PROCESS_DETAIL_COMPACT
        val separator = if ('?' in stripped) '&' else '?'
        return "$stripped$separator$processParam"
    }

    private fun stripQueryParam(url: String, paramPrefix: String): String {
        val queryIdx = url.indexOf('?')
        if (queryIdx < 0) return url
        val base = url.substring(0, queryIdx)
        val query = url.substring(queryIdx + 1)
        val kept = query.split('&')
            .filter { it.isNotEmpty() && !it.startsWith(paramPrefix) }
            .joinToString("&")
        return if (kept.isEmpty()) base else "$base?$kept"
    }
}
