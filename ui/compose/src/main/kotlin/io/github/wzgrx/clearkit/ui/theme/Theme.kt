package io.github.wzgrx.clearkit.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.wzgrx.clearkit.ui.motion.PressIndication
import io.github.wzgrx.clearkit.ui.motion.RubberBandOverscrollFactory

val LocalClearColors = staticCompositionLocalOf { clearColors(ClearAccent.Teal, dark = false) }
val LocalClearType = staticCompositionLocalOf { ClearType() }
/** 高密度（默认）：行 48 点高、说明一行；“宽松”时 60 点高、说明全显示。 */
val LocalClearCompact = staticCompositionLocalOf { true }
/** 减少动效：页面切换不做动画、没有回弹和按压缩放。 */
val LocalClearReduceMotion = staticCompositionLocalOf { false }
/** 底栏、浮层用不用液态玻璃（关了用不透明的面板色）。 */
val LocalClearGlass = staticCompositionLocalOf { true }
/** 界面文字的转换（哔哩净界的简体 / 繁体）；组件里的文字都经过它。 */
val LocalTextTransform = staticCompositionLocalOf<(String) -> String> { { it } }

/** 组件里取主题：`Clear.colors.accent`、`Clear.type.item`。 */
object Clear {
    val colors: ClearColors @Composable get() = LocalClearColors.current
    val type: ClearType @Composable get() = LocalClearType.current
    val compact: Boolean @Composable get() = LocalClearCompact.current
    val reduceMotion: Boolean @Composable get() = LocalClearReduceMotion.current
    val glass: Boolean @Composable get() = LocalClearGlass.current
}

/**
 * 家族统一的主题：颜色、字号、密度、动效偏好，再把 Material 3 的配色映射过去（对话框、输入框不出现 M3 默认的淡紫）。
 * 按压反馈换成轻微缩小 + 弹回，列表到头是橡皮筋回弹（[reduceMotion] 时都不用）。
 */
@Composable
fun ClearTheme(
    colors: ClearColors,
    compact: Boolean = true,
    reduceMotion: Boolean = false,
    glass: Boolean = true,
    textTransform: (String) -> String = { it },
    content: @Composable () -> Unit,
) {
    val motion = if (reduceMotion) arrayOf() else arrayOf(
        LocalIndication provides PressIndication,
        LocalOverscrollFactory provides RubberBandOverscrollFactory,
    )
    CompositionLocalProvider(
        LocalClearColors provides colors,
        LocalClearType provides ClearType(),
        LocalClearCompact provides compact,
        LocalClearReduceMotion provides reduceMotion,
        LocalClearGlass provides glass,
        LocalTextTransform provides textTransform,
    ) {
        MaterialTheme(colorScheme = materialScheme(colors)) {
            CompositionLocalProvider(*motion, content = content)
        }
    }
}

/** Material 3 的完整配色按规范映射。 */
fun materialScheme(c: ClearColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accentInk,
        secondary = c.accent, onSecondary = c.onAccent, secondaryContainer = c.accentSoft, onSecondaryContainer = c.accentInk,
        tertiary = c.accent, onTertiary = c.onAccent,
        background = c.ground, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.muted, onSurfaceVariant = c.textSecondary, surfaceTint = c.surface,
        outline = c.outline, outlineVariant = c.line, error = c.danger,
        surfaceContainerLowest = c.ground, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.muted, surfaceContainerHighest = c.muted,
    )
} else {
    lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accentInk,
        secondary = c.accent, onSecondary = c.onAccent, secondaryContainer = c.accentSoft, onSecondaryContainer = c.accentInk,
        tertiary = c.accent, onTertiary = c.onAccent,
        background = c.ground, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.muted, onSurfaceVariant = c.textSecondary, surfaceTint = c.surface,
        outline = c.outline, outlineVariant = c.line, error = c.danger,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.muted,
    )
}
