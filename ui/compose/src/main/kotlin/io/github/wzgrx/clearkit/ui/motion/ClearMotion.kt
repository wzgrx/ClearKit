package io.github.wzgrx.clearkit.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * 净界家族共用的动效参数。页面之间的转场在页面栈里（见 shell 的 PageStack、PageMotion：照 HyperOS 系统转场，
 * 整页滑入、下层左移压暗，500ms 物理曲线）；这里是其它小动画用的时长和曲线。
 */
object ClearMotion {
    /** Material 3 强调减速（emphasized decelerate）。 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material 3 强调加速（emphasized accelerate）。 */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 提示条、重启条的显隐时长。 */
    const val BAR = 250
}
