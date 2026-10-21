package io.github.wzgrx.clearkit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 一套界面颜色（规范 v1 “颜色”）：中性色两个模块共用，只有强调色一组按模块换（[ClearAccent]）。
 * 字段名沿用哔哩净界 5.0 起的叫法，模块里的旧代码不用改名。
 */
@Immutable
data class ClearColors(
    /** 主按钮、开关、选中。 */
    val accent: Color,
    val onAccent: Color,
    /** 浅强调底：选中卡片、标签、底栏选中项。 */
    val accentSoft: Color,
    /** 浅底上的强调字（比 [accent] 深一点，保证对比度）。 */
    val accentInk: Color,
    /** 页面底。 */
    val ground: Color,
    /** 卡片。 */
    val surface: Color,
    /** 输入框、图标底、分段控件的槽。 */
    val muted: Color,
    val text: Color,
    /** 说明。 */
    val textSecondary: Color,
    /** 时间、路径这类最弱的字。 */
    val textTertiary: Color,
    /** 不能点的字。 */
    val textDisabled: Color,
    /** 分隔线。 */
    val line: Color,
    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val danger: Color,
    /** 分段控件的槽。 */
    val seg: Color,
    /** 分段控件选中的那一段。 */
    val segOn: Color,
    /** 关着的开关、单选圈的描边（非文字对比 ≥ 3:1）。 */
    val outline: Color,
    /** 关着的开关轨道。 */
    val switchOff: Color,
    /** 未选 chip 的描边。 */
    val chipBorder: Color,
    /** “重开生效”这类提醒的底色和文字。 */
    val warnSoft: Color,
    val onWarnSoft: Color,
    val dangerSoft: Color,
    val onDangerSoft: Color,
    /** 首页状态卡。 */
    val hero: Color,
    val onHero: Color,
    val onHeroSecondary: Color,
    /** 玻璃底栏、浮层：取不到下面的画面时的底色和描边。 */
    val glass: Color,
    val glassLine: Color,
    /** 弹层后面的遮罩。 */
    val scrim: Color,
    val isDark: Boolean,
)

/** 一组强调色：浅色、深色各一份。 */
@Immutable
data class ClearAccent(val light: AccentSet, val dark: AccentSet) {
    @Immutable
    data class AccentSet(
        val accent: Color,
        val onAccent: Color,
        val accentSoft: Color,
        val accentInk: Color,
        val hero: Color,
        val onHero: Color,
        val onHeroSecondary: Color,
    )

    companion object {
        /** 哔哩净界：青绿。 */
        val Teal = ClearAccent(
            AccentSet(Color(0xFF0B6E69), Color.White, Color(0xFFDCF1EE), Color(0xFF08524E), Color(0xFF0B6E69), Color.White, Color(0xFFCDEDE9)),
            AccentSet(Color(0xFF4FD1C5), Color(0xFF00302C), Color(0xFF1C3B39), Color(0xFF7FE3DA), Color(0xFF123734), Color(0xFFE9F4F2), Color(0xFFA9CCC7)),
        )

        /** 红薯净界：玫红。 */
        val Rose = ClearAccent(
            AccentSet(Color(0xFFD6336C), Color.White, Color(0xFFFDECF2), Color(0xFFB8245A), Color(0xFFD6336C), Color.White, Color(0xFFFFDCE7)),
            AccentSet(Color(0xFFFF6F98), Color(0xFF3A0A1C), Color(0xFF3A1E2A), Color(0xFFFF8DAE), Color(0xFF45192A), Color(0xFFFFF0F4), Color(0xFFE9B3C5)),
        )

        /** 用户自选的强调色：浅色直接用，深色调亮；浅底、深字、状态卡都由它和底色混出来。 */
        fun from(seed: Color): ClearAccent {
            val lightOn = if (seed.luminance() > 0.5f) Color(0xFF1B1D21) else Color.White
            val light = AccentSet(
                accent = seed, onAccent = lightOn,
                accentSoft = mix(Color.White, seed, 0.12f), accentInk = mix(seed, Color.Black, 0.25f),
                hero = seed, onHero = lightOn, onHeroSecondary = lightOn.copy(alpha = 0.8f),
            )
            val darkAccent = mix(seed, Color.White, 0.45f)
            val darkGround = Color(0xFF0F1012)
            val dark = AccentSet(
                accent = darkAccent, onAccent = if (darkAccent.luminance() > 0.5f) Color(0xFF1B1D21) else Color.White,
                accentSoft = mix(darkGround, seed, 0.24f), accentInk = mix(darkAccent, Color.White, 0.3f),
                hero = mix(darkGround, seed, 0.35f), onHero = Color(0xFFEDEEF0), onHeroSecondary = Color(0xFFB5BBC2),
            )
            return ClearAccent(light, dark)
        }
    }
}

internal fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** 强调色 RRGGBB / AARRGGBB（可带 #）→ 颜色；空或写错返回 null（用模块的默认强调色）。 */
fun parseAccent(text: String): Color? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val v = hex.toLongOrNull(16) ?: return null
    return Color(if (hex.length == 6) 0xFF000000L or v else v).copy(alpha = 1f)
}

/** 规范里的中性色 + [accent]。 */
fun clearColors(accent: ClearAccent, dark: Boolean): ClearColors {
    val a = if (dark) accent.dark else accent.light
    return if (dark) ClearColors(
        accent = a.accent, onAccent = a.onAccent, accentSoft = a.accentSoft, accentInk = a.accentInk,
        ground = Color(0xFF0F1012), surface = Color(0xFF1A1B1E), muted = Color(0xFF24262A),
        text = Color(0xFFEDEEF0), textSecondary = Color(0xFFA3A7AE), textTertiary = Color(0xFF7D828A), textDisabled = Color(0xFF5F636A),
        line = Color(0xFF2C2E33),
        ok = Color(0xFF4CC38A), okSoft = Color(0xFF16362A), warn = Color(0xFFF2B64B), danger = Color(0xFFFF8A80),
        seg = Color(0xFF24262A), segOn = Color(0xFF383B41), outline = Color(0xFF8C9096), switchOff = Color(0xFF3A3D43), chipBorder = Color(0xFF34373D),
        warnSoft = Color(0xFF33280F), onWarnSoft = Color(0xFFF7D594), dangerSoft = Color(0xFF3A1A18), onDangerSoft = Color(0xFFFFB4AB),
        hero = a.hero, onHero = a.onHero, onHeroSecondary = a.onHeroSecondary,
        glass = Color(0xBD26282D), glassLine = Color(0x1AFFFFFF), scrim = Color(0x8C000000),
        isDark = true,
    ) else ClearColors(
        accent = a.accent, onAccent = a.onAccent, accentSoft = a.accentSoft, accentInk = a.accentInk,
        ground = Color(0xFFF4F5F7), surface = Color.White, muted = Color(0xFFEEF0F2),
        text = Color(0xFF1B1D21), textSecondary = Color(0xFF5E636B), textTertiary = Color(0xFF80858D), textDisabled = Color(0xFFA2A6AD),
        line = Color(0xFFE4E6EA),
        ok = Color(0xFF1E7F4F), okSoft = Color(0xFFE3F3EA), warn = Color(0xFF9A5B00), danger = Color(0xFFC62828),
        seg = Color(0xFFEEF0F2), segOn = Color.White, outline = Color(0xFF6F747C), switchOff = Color(0xFFCDD1D6), chipBorder = Color(0xFFD5D8DD),
        warnSoft = Color(0xFFFBEFD6), onWarnSoft = Color(0xFF5C3D00), dangerSoft = Color(0xFFFDE7E5), onDangerSoft = Color(0xFF8A1C12),
        hero = a.hero, onHero = a.onHero, onHeroSecondary = a.onHeroSecondary,
        glass = Color(0xCCFFFFFF), glassLine = Color(0x0F000000), scrim = Color(0x590F1012),
        isDark = false,
    )
}
