package io.github.wzgrx.clearkit.view

/**
 * 宿主端界面的颜色（规范 v1，和 App 端 clearkit-ui 的 ClearColors 同一套值），ARGB 整数。
 * 中性色两个模块一样，强调色按模块（[ViewAccent.Teal] 哔哩净界、[ViewAccent.Rose] 红薯净界）。
 */
data class ClearViewColors(
    val accent: Int,
    val onAccent: Int,
    val accentSoft: Int,
    val accentInk: Int,
    val ground: Int,
    val surface: Int,
    val muted: Int,
    val text: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val textDisabled: Int,
    val line: Int,
    val seg: Int,
    val segOn: Int,
    val outline: Int,
    val switchOff: Int,
    val chipBorder: Int,
    val ok: Int,
    val okSoft: Int,
    val warn: Int,
    val warnSoft: Int,
    val onWarnSoft: Int,
    val danger: Int,
    val dangerSoft: Int,
    val onDangerSoft: Int,
    val scrim: Int,
    val handle: Int,
    val pressed: Int,
    val dark: Boolean,
)

/** 一个模块的强调色（浅色、深色各一组：强调、强调上的字、浅底、浅底上的字）。 */
class ViewAccent(val light: IntArray, val dark: IntArray) {
    companion object {
        val Teal = ViewAccent(intArrayOf(0xFF0B6E69.toInt(), 0xFFFFFFFF.toInt(), 0xFFDCF1EE.toInt(), 0xFF08524E.toInt()), intArrayOf(0xFF4FD1C5.toInt(), 0xFF00302C.toInt(), 0xFF1C3B39.toInt(), 0xFF7FE3DA.toInt()))
        val Rose = ViewAccent(intArrayOf(0xFFD6336C.toInt(), 0xFFFFFFFF.toInt(), 0xFFFDECF2.toInt(), 0xFFB8245A.toInt()), intArrayOf(0xFFFF6F98.toInt(), 0xFF3A0A1C.toInt(), 0xFF3A1E2A.toInt(), 0xFFFF8DAE.toInt()))
    }
}

fun clearViewColors(accent: ViewAccent, dark: Boolean): ClearViewColors {
    val a = if (dark) accent.dark else accent.light
    return if (dark) ClearViewColors(
        accent = a[0], onAccent = a[1], accentSoft = a[2], accentInk = a[3],
        ground = 0xFF0F1012.toInt(), surface = 0xFF1A1B1E.toInt(), muted = 0xFF24262A.toInt(),
        text = 0xFFEDEEF0.toInt(), textSecondary = 0xFFA3A7AE.toInt(), textTertiary = 0xFF7D828A.toInt(), textDisabled = 0xFF5F636A.toInt(),
        line = 0xFF2C2E33.toInt(), seg = 0xFF24262A.toInt(), segOn = 0xFF383B41.toInt(), outline = 0xFF8C9096.toInt(),
        switchOff = 0xFF3A3D43.toInt(), chipBorder = 0xFF34373D.toInt(),
        ok = 0xFF4CC38A.toInt(), okSoft = 0xFF16362A.toInt(), warn = 0xFFF2B64B.toInt(), warnSoft = 0xFF33280F.toInt(), onWarnSoft = 0xFFF7D594.toInt(),
        danger = 0xFFFF8A80.toInt(), dangerSoft = 0xFF3A1A18.toInt(), onDangerSoft = 0xFFFFB4AB.toInt(),
        scrim = 0x8C000000.toInt(), handle = 0xFF3A3D43.toInt(), pressed = 0xFF2A2C31.toInt(), dark = true,
    ) else ClearViewColors(
        accent = a[0], onAccent = a[1], accentSoft = a[2], accentInk = a[3],
        ground = 0xFFF4F5F7.toInt(), surface = 0xFFFFFFFF.toInt(), muted = 0xFFEEF0F2.toInt(),
        text = 0xFF1B1D21.toInt(), textSecondary = 0xFF5E636B.toInt(), textTertiary = 0xFF80858D.toInt(), textDisabled = 0xFFA2A6AD.toInt(),
        line = 0xFFE4E6EA.toInt(), seg = 0xFFEEF0F2.toInt(), segOn = 0xFFFFFFFF.toInt(), outline = 0xFF6F747C.toInt(),
        switchOff = 0xFFCDD1D6.toInt(), chipBorder = 0xFFD5D8DD.toInt(),
        ok = 0xFF1E7F4F.toInt(), okSoft = 0xFFE3F3EA.toInt(), warn = 0xFF9A5B00.toInt(), warnSoft = 0xFFFBEFD6.toInt(), onWarnSoft = 0xFF5C3D00.toInt(),
        danger = 0xFFC62828.toInt(), dangerSoft = 0xFFFDE7E5.toInt(), onDangerSoft = 0xFF8A1C12.toInt(),
        scrim = 0x590F1012, handle = 0xFFCDD1D6.toInt(), pressed = 0xFFE6E8EB.toInt(), dark = false,
    )
}

/** 圆角（dp，规范 v1）：块 8 · 图标底 12 · 输入 14 · 小卡 16 · 卡片 20 · 状态卡 24 · 弹层 28。 */
object ViewRadius {
    const val BLOCK = 8f
    const val ICON = 12f
    const val FIELD = 14f
    const val SMALL = 16f
    const val CARD = 20f
    const val HERO = 24f
    const val SHEET = 28f
}

/** 字号（sp，规范 v1）。 */
object ViewType {
    const val PAGE_TITLE = 26f
    const val TITLE = 20f
    const val SECTION = 17f
    const val ITEM = 15f
    const val BODY = 14f
    const val LABEL = 13f
    const val CAPTION = 12f
    const val SMALL = 11f
}
