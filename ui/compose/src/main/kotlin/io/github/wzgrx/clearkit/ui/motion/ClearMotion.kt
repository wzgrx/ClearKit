package io.github.wzgrx.clearkit.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

/**
 * 页面转场（净界家族共用，来自红薯净界调好的那一套）：
 * - 进子页：新页从右侧 1/5 处滑入、稍后淡入，旧页左移 1/10 并很快淡出；横移用临界阻尼弹簧（约 300ms 停稳，不回弹）。
 *   旧页先淡出、新页再淡入，两页几乎不会同时半透明，不会有重影；新页第一帧慢一点（排版、编译）也藏在淡入前面。
 * - 返回：反向，弹簧更硬一点（约 250ms）。
 * - 页签之间：fade-through（先淡出、再淡入并从 0.96 放大），约 210ms，不横移。
 * - 底栏：进子页时往下滑走，回来时带一点回弹滑上来。
 * 新页淡入从 1% 而不是 0 开始：完全透明的内容系统直接跳过不画，第一次真正绘制（文字、圆角块）就会挤在
 * 淡入开始的那一帧（K90 上示意图页那一帧渲染线程 11–14ms）；从 1% 开始，这些准备在前面几帧做完，看不出来。
 */
object ClearMotion {
    /** Material 3 强调减速（emphasized decelerate）。 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material 3 强调加速（emphasized accelerate）。 */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val PUSH = 300
    const val POP = 250
    const val FADE_OUT = 90
    const val TAB_OUT = 70
    const val TAB_IN = 140

    /** 底栏、提示条的显隐时长，和进子页对齐。 */
    const val BAR = 250

    /** 页面横移用弹簧（临界阻尼，不回弹）：快速连点、中途返回时从当前位置和速度接着走，不会跳。 */
    val PushSpring = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)
    val PopSpring = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 560f, visibilityThreshold = IntOffset.VisibilityThreshold)

    /** 底栏显示（带一点回弹）和收起。 */
    val BarShow = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)
    val BarHide = spring<Float>(dampingRatio = 1f, stiffness = 650f)

    /** 新页淡入的起点（不是 0，见上）。 */
    const val ENTER_ALPHA = 0.01f

    fun pushEnter(): EnterTransition = slideInHorizontally(PushSpring) { it / 5 } +
        fadeIn(tween(PUSH - FADE_OUT, delayMillis = FADE_OUT, easing = LinearOutSlowInEasing), initialAlpha = ENTER_ALPHA)

    fun pushExit(): ExitTransition = slideOutHorizontally(PushSpring) { -it / 10 } +
        fadeOut(tween(FADE_OUT, easing = FastOutLinearInEasing))

    fun popEnter(): EnterTransition = slideInHorizontally(PopSpring) { -it / 10 } +
        fadeIn(tween(POP - FADE_OUT, delayMillis = FADE_OUT, easing = LinearOutSlowInEasing), initialAlpha = ENTER_ALPHA)

    fun popExit(): ExitTransition = slideOutHorizontally(PopSpring) { it / 5 } +
        fadeOut(tween(FADE_OUT, easing = FastOutLinearInEasing))

    fun tabIn(): EnterTransition = fadeIn(tween(TAB_IN, delayMillis = TAB_OUT, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(TAB_IN, delayMillis = TAB_OUT, easing = LinearOutSlowInEasing), initialScale = 0.96f)

    fun tabOut(): ExitTransition = fadeOut(tween(TAB_OUT, easing = FastOutLinearInEasing))
}
