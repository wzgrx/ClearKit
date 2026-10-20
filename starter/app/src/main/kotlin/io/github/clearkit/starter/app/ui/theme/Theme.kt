package io.github.clearkit.starter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import io.github.clearkit.starter.model.settings.MapSettingsReader
import io.github.clearkit.starter.model.settings.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 5.0 设计的颜色（和【宿主】里浮层的 HostTheme 同一套值）：清透的灰绿底、一个青绿强调色。 */
@Immutable
data class BcColors(
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    /** 浅底上的强调文字（比 [accent] 深一点，保证对比度）。 */
    val accentInk: Color,
    val ground: Color,
    val surface: Color,
    val muted: Color,
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val line: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    /** 分段控件的槽、关着的开关轨道。 */
    val seg: Color,
    /** 分段控件选中的那一段。 */
    val segOn: Color,
    /** 关着的开关、单选圈的描边（非文字对比 ≥ 3:1）。 */
    val outline: Color,
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
    val isDark: Boolean,
)

val LightColors = BcColors(
    accent = Color(0xFF0B6E69),
    onAccent = Color.White,
    accentSoft = Color(0xFFDCF1EE),
    accentInk = Color(0xFF08524E),
    ground = Color(0xFFF3F5F4),
    surface = Color.White,
    muted = Color(0xFFEEF1F0),
    text = Color(0xFF15201E),
    textSecondary = Color(0xFF55625E),
    textDisabled = Color(0xFF8A9693),
    line = Color(0xFFE4EAE7),
    ok = Color(0xFF0B6E69),
    warn = Color(0xFF7A5200),
    danger = Color(0xFFB3261E),
    seg = Color(0xFFE6EBE9),
    segOn = Color.White,
    outline = Color(0xFF6F7C78),
    chipBorder = Color(0xFFD5DDDA),
    warnSoft = Color(0xFFFBEFD6),
    onWarnSoft = Color(0xFF5C3D00),
    dangerSoft = Color(0xFFFBE4E1),
    onDangerSoft = Color(0xFF8A1C12),
    hero = Color(0xFF0B6E69),
    onHero = Color.White,
    onHeroSecondary = Color(0xFFD6F2EE),
    isDark = false,
)

val DarkColors = BcColors(
    accent = Color(0xFF4FD1C5),
    onAccent = Color(0xFF00302C),
    accentSoft = Color(0xFF1E3F3C),
    accentInk = Color(0xFF7FE3DA),
    ground = Color(0xFF0F1413),
    surface = Color(0xFF182020),
    muted = Color(0xFF222B2A),
    text = Color(0xFFE9F0EE),
    textSecondary = Color(0xFF9AA8A4),
    textDisabled = Color(0xFF66736F),
    line = Color(0xFF263130),
    ok = Color(0xFF4FD1C5),
    warn = Color(0xFFF2C46B),
    danger = Color(0xFFFFB4AB),
    seg = Color(0xFF1B2423),
    segOn = Color(0xFF33413F),
    outline = Color(0xFF8C9A96),
    chipBorder = Color(0xFF33403E),
    warnSoft = Color(0xFF2E2512),
    onWarnSoft = Color(0xFFF2D79B),
    dangerSoft = Color(0xFF3A1714),
    onDangerSoft = Color(0xFFFFB4AB),
    hero = Color(0xFF133B38),
    onHero = Color(0xFFE9F0EE),
    onHeroSecondary = Color(0xFFB5CFCB),
    isDark = true,
)

/** 场景色：只用在场景图标底，帮人一眼找到地方；强调色仍然只有一个。 */
enum class Hue(val lightBg: Long, val lightFg: Long, val darkBg: Long, val darkFg: Long) {
    HOME(0xFFDCF1EE, 0xFF0B6E69, 0xFF1E3F3C, 0xFF4FD1C5),
    VIDEO(0xFFDDE8FA, 0xFF1F5FBF, 0xFF1B2B45, 0xFF8AB4F8),
    DYNAMIC(0xFFECE4FB, 0xFF6B45C2, 0xFF2C2340, 0xFFC3A8F5),
    COMMENT(0xFFFCE6D8, 0xFFA9501A, 0xFF3A2618, 0xFFF4B08A),
    LIVE(0xFFFBE2EA, 0xFFAD3A5E, 0xFF3A1D27, 0xFFF3A2BC),
    SPACE(0xFFE2F1DA, 0xFF3F7A2A, 0xFF1F3019, 0xFFA6D98F),
    NET(0xFFDDEEF3, 0xFF1D6A86, 0xFF16303A, 0xFF86CDE5),
    MEDIA(0xFFF5EAD2, 0xFF86621A, 0xFF33291A, 0xFFE5C27A),
    FLOAT(0xFFE5E8F6, 0xFF4A55A2, 0xFF22253A, 0xFFAEB6F0),
    LOOK(0xFFF4E2F1, 0xFF8E3D86, 0xFF36203A, 0xFFE3A6DA),
    GRAY(0xFFEEF1F0, 0xFF55625E, 0xFF222B2A, 0xFF9AA8A4);

    fun bg(dark: Boolean) = Color(if (dark) darkBg else lightBg)
    fun fg(dark: Boolean) = Color(if (dark) darkFg else lightFg)
}

@Immutable
data class BcType(
    val pageTitle: TextStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    val sceneTitle: TextStyle = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    val cardTitle: TextStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    val heroNumber: TextStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
    val blockTitle: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp),
    val sectionLabel: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    val itemTitle: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
)

val LocalBcColors = staticCompositionLocalOf { LightColors }
/** 高密度（默认）：行 48 点高、说明一行；“宽松”时恢复 60 点高、说明全显示。 */
val LocalBcCompact = staticCompositionLocalOf { true }
/** 减少动效：页面切换不做动画。 */
val LocalBcReduceMotion = staticCompositionLocalOf { false }
val LocalBcType = staticCompositionLocalOf { BcType() }

object Bc {
    val colors: BcColors @Composable get() = LocalBcColors.current
    val type: BcType @Composable get() = LocalBcType.current
    val compact: Boolean @Composable get() = LocalBcCompact.current
    val reduceMotion: Boolean @Composable get() = LocalBcReduceMotion.current
}

/** 强调色 RRGGBB / AARRGGBB（可带 #）→ 颜色；空或写错返回 null（用默认青绿）。 */
fun accentColor(text: String): Color? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val v = hex.toLongOrNull(16) ?: return null
    return Color(if (hex.length == 6) 0xFF000000L or v else v).copy(alpha = 1f)
}

private fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** 换强调色：浅色直接用，深色调亮；浅底色由强调色和底色混出来；强调色上的文字按亮度选黑白。 */
fun BcColors.withAccent(base: Color): BcColors {
    val accent = if (isDark) mix(base, Color.White, 0.45f) else base
    val soft = if (isDark) mix(ground, base, 0.22f) else mix(Color.White, base, 0.10f)
    val on = if (accent.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White
    val ink = if (isDark) mix(accent, Color.White, 0.3f) else mix(base, Color.Black, 0.25f)
    val hero = if (isDark) mix(ground, base, 0.35f) else base
    return copy(accent = accent, accentSoft = soft, onAccent = on, accentInk = ink, ok = accent, hero = hero)
}

/** Material 3 的完整配色按 V2.1 映射，对话框、开关、输入框不出现 M3 默认的淡紫色。 */
fun materialScheme(c: BcColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
        secondary = c.accent, onSecondary = c.onAccent, secondaryContainer = c.accentSoft, onSecondaryContainer = c.accent,
        tertiary = c.accent, onTertiary = c.onAccent,
        background = c.ground, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.muted, onSurfaceVariant = c.textSecondary, surfaceTint = c.surface,
        outline = c.outline, outlineVariant = c.line, error = c.danger,
        surfaceContainerLowest = c.ground, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.muted, surfaceContainerHighest = c.muted,
    )
} else {
    lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
        secondary = c.accent, onSecondary = c.onAccent, secondaryContainer = c.accentSoft, onSecondaryContainer = c.accent,
        tertiary = c.accent, onTertiary = c.onAccent,
        background = c.ground, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.muted, onSurfaceVariant = c.textSecondary, surfaceTint = c.surface,
        outline = c.outline, outlineVariant = c.line, error = c.danger,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.muted,
    )
}

/** 按“外观与主题”（设计 022）的设置：主题方式、强调色、界面密度、减少动效。设置改了立即生效。 */
@Composable
fun BcTheme(content: @Composable () -> Unit) {
    val values by io.github.clearkit.starter.app.ModuleApp.instance.settings.values.collectAsState()
    val reader = MapSettingsReader { values[it] }
    val a = Settings.Appearance
    val dark = when (reader[a.themeMode]) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val base = if (dark) DarkColors else LightColors
    val colors = accentColor(reader[a.accent])?.let { base.withAccent(it) } ?: base
    // 界面文字（简体 / 繁体）：换了就整棵界面重新组合
    val locale = reader[a.locale]
    io.github.clearkit.starter.hook.ui.ZhText.mode = locale
    androidx.compose.runtime.key(locale) {
    CompositionLocalProvider(
        LocalBcColors provides colors, LocalBcType provides BcType(),
        LocalBcCompact provides (reader[a.density] != "comfortable"), LocalBcReduceMotion provides reader[a.reduceMotion],
    ) {
        MaterialTheme(colorScheme = materialScheme(colors), content = content)
    }
    }
}
