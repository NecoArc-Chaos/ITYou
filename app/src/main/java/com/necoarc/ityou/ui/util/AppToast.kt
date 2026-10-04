package com.necoarc.ityou.ui.util

import android.content.Context
import android.widget.Toast

/**
 * 系统 Toast 的统一入口。
 *
 * 为什么用系统 Toast 而不是 Compose 浮层：
 * - 它由系统窗口管理，**不参与应用的重组与布局**，不会遮挡内容、
 *   也不会因为重组而闪烁或重复出现；
 * - 生命周期由系统负责，会自动消失、无需手写延时逻辑，
 *   退出页面时也不会遗留未清理的协程；
 * - 无需处理 Activity 销毁后的状态残留问题。
 *
 * 关于位置：在 Android 11（API 30）及以上，`Toast.setGravity()` 会被系统忽略，
 * Toast 固定显示在系统规定的位置（底部）。
 * 本项目 targetSdk 为 37，因此**不应依赖自定义位置**，
 * 这里也刻意不去调用 `setGravity`，把定位交由系统决定。
 */
object AppToast {

    /** 短提示时长（约 2 秒）。 */
    private const val DURATION_SHORT = Toast.LENGTH_SHORT

    /** 长提示时长（约 3.5 秒），用于错误信息这类需要多看一眼的内容。 */
    private const val DURATION_LONG = Toast.LENGTH_LONG

    /**
     * 展示一条普通提示。
     *
     * @param long 为 true 时使用较长时长，适合错误信息
     */
    fun show(context: Context, message: String, long: Boolean = false) {
        if (message.isBlank()) return
        Toast.makeText(
            context.applicationContext,
            message,
            if (long) DURATION_LONG else DURATION_SHORT
        ).show()
    }
}
