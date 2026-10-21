package io.github.wzgrx.clearkit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 圆角（规范 v1）：示意图块 8 · 图标底 12 · 分段、输入 14 · 小卡 16 · 卡片 20 · 状态卡 24 · 弹层、抽屉 28；按钮、芯片是胶囊。 */
object Radius {
    val block = 8.dp
    val icon = 12.dp
    val field = 14.dp
    val small = 16.dp
    val card = 20.dp
    val hero = 24.dp
    val sheet = 28.dp
}

/** 间距（规范 v1）：图标和字 4 · 芯片之间 8 · 卡片之间 12 · 页边距 16 · 大段之间 20 · 弹层底部留白 24。 */
object Space {
    val tight = 4.dp
    val chip = 8.dp
    val card = 12.dp
    val gutter = 16.dp
    val section = 20.dp
    val sheetBottom = 24.dp
}

/** 页面左右留白：卡片、提示条、按钮都按这个对齐。 */
val PageGutter = Space.gutter

/** 卡片内的左右留白。 */
val CardPadding = 16.dp

/** 卡片圆角。 */
val CardRadius = Radius.card

/**
 * 场景色（规范 v1 “场景色”）：只用在图标底，帮人一眼找到地方；强调色仍然只有一个。
 * 八个色相 + 灰，深浅各一组（底、前景）。
 */
enum class ClearHue(val lightBg: Long, val lightFg: Long, val darkBg: Long, val darkFg: Long) {
    RED(0xFFFDE7E5, 0xFFC0392B, 0xFF3A1D1D, 0xFFFF8F86),
    ORANGE(0xFFFDEBDD, 0xFFB85A12, 0xFF3A2716, 0xFFFFB27A),
    AMBER(0xFFFBF0D3, 0xFF8A6200, 0xFF352C12, 0xFFF2C46B),
    GREEN(0xFFE2F3E6, 0xFF237A3B, 0xFF17321F, 0xFF7ED69B),
    TEAL(0xFFDCF1EE, 0xFF0B6E69, 0xFF13332F, 0xFF6FD8CB),
    BLUE(0xFFE1ECFD, 0xFF2457A6, 0xFF172A44, 0xFF8DB8FF),
    PURPLE(0xFFEEE7FD, 0xFF6542B5, 0xFF2B2042, 0xFFC3A6FF),
    PINK(0xFFFDECF2, 0xFFB8245A, 0xFF3A1E2A, 0xFFFF8DAE),
    GRAY(0xFFEEF0F2, 0xFF5E636B, 0xFF24262A, 0xFFA3A7AE);

    fun bg(dark: Boolean) = Color(if (dark) darkBg else lightBg)
    fun fg(dark: Boolean) = Color(if (dark) darkFg else lightFg)
}
