package io.github.wzgrx.clearkit.ui.motion

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangularShape
import io.github.wzgrx.clearkit.ui.theme.Clear

/**
 * 液态玻璃材质（浮在内容上的底栏、提示条用，规范 v1 “动效 · 底栏”）：
 * - Android 13+：取下面内容的图像，提高饱和度、模糊，再在边缘做透镜折射，加一圈高光和柔和阴影；
 * - Android 12：只模糊，不折射；
 * - 更早的系统、或用户关了玻璃效果：半透明的玻璃色加细边，不取下面的内容（没有额外开销）。
 *
 * 下面的内容由 [LocalGlassBackdrop] 提供（外壳在页面容器上 `layerBackdrop`），没有时也按不透明处理。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

object Glass {
    /** 设备支不支持取背景做模糊（Android 12+）。 */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * 给这块区域加玻璃底。[shape] 要用 kyant 的圆角形状（`Capsule()`、`RoundedRectangle(16.dp)`），
 * 折射按它的圆角计算。[enabled] 为 false、主题关了玻璃或没有背景来源时用玻璃色加细边。
 */
@Composable
fun Modifier.liquidGlass(
    shape: RoundedRectangularShape,
    enabled: Boolean = true,
    blur: Dp = 10.dp,
    refractionHeight: Dp = 14.dp,
    refractionAmount: Dp = 28.dp,
): Modifier {
    val colors = Clear.colors
    val backdrop = LocalGlassBackdrop.current
    if (!enabled || !Clear.glass || backdrop == null || !Glass.supported) {
        return this.background(colors.glass, shape).border(0.5.dp, colors.glassLine, shape)
    }
    // 面板色只盖一层薄的，让下面的颜色透上来；深色模式下稍厚一点，文字更稳
    val tint = if (colors.isDark) colors.surface.copy(alpha = 0.55f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blur.toPx())
            lens(refractionHeight.toPx(), refractionAmount.toPx())
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(tint) },
    )
}

/** 规范 v1 的几条弹簧。 */
object ClearSprings {
    /** 页面切换：stiffness 500、阻尼 0.85。 */
    val page = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)
    /** 底栏选中项的“水滴”。 */
    val pill = spring<Float>(dampingRatio = 0.7f, stiffness = 520f)
    /** 弹层推上来。 */
    val sheet = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}
