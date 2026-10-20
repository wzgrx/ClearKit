package io.github.clearkit.starter.hook.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue

/**
 * 【宿主】进程里自己画的界面的配色（和 App 的 5.0 主题同一套值）。【宿主】的 Context 解析不了模块资源，
 * 所以界面都在代码里搭。
 */
class HostTheme private constructor(val context: Context, val dark: Boolean) {
    private fun c(light: String, night: String) = Color.parseColor(if (dark) night else light)

    val accent = c("#0B6E69", "#4FD1C5")
    val onAccent = c("#FFFFFF", "#00302C")
    val accentText = c("#0B6E69", "#4FD1C5")
    val accentSoft = c("#DCF1EE", "#1E3F3C")
    val onAccentSoft = c("#08524E", "#7FE3DA")
    val ground = c("#F3F5F4", "#0F1413")
    val surface = c("#FFFFFF", "#182020")
    val surfaceMuted = c("#EEF1F0", "#222B2A")
    val text = c("#15201E", "#E9F0EE")
    val textSecondary = c("#55625E", "#9AA8A4")
    val textDisabled = c("#8A9693", "#66736F")
    val divider = c("#E4EAE7", "#263130")
    val handle = c("#C9D2CF", "#3A4644")
    /** 分段控件的槽、关着的开关轨道；选中那一段。 */
    val seg = c("#E6EBE9", "#1B2423")
    val segOn = c("#FFFFFF", "#33413F")
    /** 关着的开关描边和滑块（非文字对比 ≥ 3:1）。 */
    val outline = c("#6F7C78", "#8C9A96")
    val chipBorder = c("#D5DDDA", "#33403E")
    val ok = c("#0B6E69", "#4FD1C5")
    val warn = c("#7A5200", "#F2C46B")
    val inverse = c("#15201E", "#E9F0EE")
    val onInverse = c("#FFFFFF", "#15201E")
    /** 反相条（深底）上的强调色按钮。 */
    val inverseAccent = c("#4FD1C5", "#0B6E69")
    val onInverseAccent = c("#00302C", "#FFFFFF")
    val scrim = Color.argb(122, 10, 16, 15)
    val warnSoft = c("#FBEFD6", "#2E2512")
    val onWarnSoft = c("#5C3D00", "#F2D79B")
    val danger = c("#B3261E", "#FFB4AB")
    val dangerSoft = c("#FBE4E1", "#3A1714")
    val okSoft = c("#DCF1EE", "#1E3F3C")
    val pressed = c("#E6EBE9", "#26302F")
    /** 设置行高亮（搜索定位）。 */
    val highlight = c("#FBE7A6", "#5C4A12")

    fun dp(value: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
    fun dp(value: Int): Int = dp(value.toFloat())

    val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    companion object {
        fun of(context: Context): HostTheme {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return HostTheme(context, night == Configuration.UI_MODE_NIGHT_YES)
        }
    }
}

/** Text sizes in sp (design language board 00). */
object TextSize {
    const val SECTION = 18f
    const val ITEM = 16f
    const val BODY = 14f
    const val LABEL = 13f
    const val CAPTION = 12f
}
