package io.github.clearkit.starter.hook.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * 系统栏（含输入法）占掉的上下边距，贴在【宿主】页面顶部、底部的提示和按钮条用。Android 11 起用 [WindowInsets.Type]，
 * 以前用旧的 systemWindowInset（同样包含输入法）。还没贴到窗口上时为 0。
 */
object SystemInsets {
    fun top(view: View): Int {
        val insets = view.rootWindowInsets ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(TYPES).top else @Suppress("DEPRECATION") insets.systemWindowInsetTop
    }

    fun bottom(view: View): Int {
        val insets = view.rootWindowInsets ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(TYPES).bottom else @Suppress("DEPRECATION") insets.systemWindowInsetBottom
    }

    private val TYPES: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) WindowInsets.Type.systemBars() or WindowInsets.Type.ime() else 0
}
