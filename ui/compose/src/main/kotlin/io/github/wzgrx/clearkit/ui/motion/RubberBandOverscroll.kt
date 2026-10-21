package io.github.wzgrx.clearkit.ui.motion

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 橡皮筋回弹（替换系统的拉伸效果）：
 * - 拖到头继续拉，内容跟着走但越拉越费劲（iOS 的阻尼曲线 `(1 - 1 / (x·c / d + 1))·d`，d 是可视区的长度）；
 *   往回拖时先收回拉出去的部分，再正常滚动。
 * - 松手时有拉出去的部分，按手指速度用弹簧弹回；惯性滑到边界时，按剩下的速度冲出一小段再弹回来。
 * - 只移动绘制层（`placeWithLayer` 里读位移），不触发重新测量、布局。
 *
 * 上下、左右都生效（各自一个方向），系统关了动画时不用它（见 [RubberBandOverscrollFactory]）。
 */
class RubberBandOverscroll : OverscrollEffect {
    private var x by mutableFloatStateOf(0f)
    private var y by mutableFloatStateOf(0f)
    private var animating by mutableIntStateOf(0)
    private var width = 1f
    private var height = 1f

    override val isInProgress: Boolean get() = x != 0f || y != 0f || animating != 0

    override val node: DelegatableNode = OffsetNode()

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        if (source != NestedScrollSource.UserInput) return performScroll(delta)
        // 1. 往回拖：先收回已经拉出去的部分
        var dx = delta.x
        var dy = delta.y
        var takenX = 0f
        var takenY = 0f
        if (x != 0f && dx != 0f && sign(dx) != sign(x)) {
            val raw = Rubber.raw(x, width)
            val next = raw + dx
            if (sign(next) == sign(raw)) { x = Rubber.shown(next, width); takenX = dx; dx = 0f } else { x = 0f; takenX = -raw; dx = next }
        }
        if (y != 0f && dy != 0f && sign(dy) != sign(y)) {
            val raw = Rubber.raw(y, height)
            val next = raw + dy
            if (sign(next) == sign(raw)) { y = Rubber.shown(next, height); takenY = dy; dy = 0f } else { y = 0f; takenY = -raw; dy = next }
        }
        // 2. 剩下的交给列表滚动
        val consumed = performScroll(Offset(dx, dy))
        // 3. 滚到头没用完的部分变成拉伸
        val leftX = dx - consumed.x
        val leftY = dy - consumed.y
        if (leftX != 0f) x = Rubber.shown(Rubber.raw(x, width) + leftX, width)
        if (leftY != 0f) y = Rubber.shown(Rubber.raw(y, height) + leftY, height)
        return Offset(takenX + consumed.x + leftX, takenY + consumed.y + leftY)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        if (x != 0f || y != 0f) {
            // 松手时拉出去了：带着手指速度弹回原位，不再惯性滚动
            settle(velocity.x, velocity.y)
            return
        }
        val left = performFling(velocity)
        // 惯性滑到边界还剩速度：冲出一小段再弹回
        if (left.x != 0f || left.y != 0f) settle(left.x * EDGE_VELOCITY_FACTOR, left.y * EDGE_VELOCITY_FACTOR)
    }

    private suspend fun settle(vx: Float, vy: Float) = coroutineScope {
        if (x != 0f || vx != 0f) launch { animateAxis(x, vx) { x = it } }
        if (y != 0f || vy != 0f) launch { animateAxis(y, vy) { y = it } }
    }

    private suspend fun animateAxis(from: Float, velocity: Float, set: (Float) -> Unit) {
        animating++
        try {
            AnimationState(from, velocity).animateTo(0f, SettleSpring, sequentialAnimation = false) { set(value) }
            set(0f)
        } finally {
            // 被新的拖动打断时停在当前位置，由拖动接着处理，不跳回 0
            animating--
        }
    }

    private inner class OffsetNode : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val p = measurable.measure(constraints)
            width = p.width.toFloat().coerceAtLeast(1f)
            height = p.height.toFloat().coerceAtLeast(1f)
            return layout(p.width, p.height) {
                p.placeWithLayer(0, 0) {
                    translationX = x
                    translationY = y
                }
            }
        }
    }

    private companion object {
        /** 惯性撞到边界时用多少剩余速度冲出去（全用会冲得太远）。 */
        const val EDGE_VELOCITY_FACTOR = 0.35f
        /** 弹回：临界阻尼，不来回晃。 */
        val SettleSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f, visibilityThreshold = 0.5f)
    }
}

/** 阻尼曲线和它的反函数：手指拉了 [raw] 像素，内容显示移动多少；反过来由显示位移推手指距离。 */
internal object Rubber {
    private const val C = 0.55f

    fun shown(raw: Float, dimension: Float): Float {
        if (raw == 0f) return 0f
        val d = dimension.coerceAtLeast(1f)
        return sign(raw) * (1f - 1f / (abs(raw) * C / d + 1f)) * d
    }

    fun raw(shown: Float, dimension: Float): Float {
        if (shown == 0f) return 0f
        val d = dimension.coerceAtLeast(1f)
        val s = (abs(shown) / d).coerceAtMost(0.999f)
        return sign(shown) * (1f / (1f - s) - 1f) * d / C
    }
}

/** 全局的回弹效果（在根部 `LocalOverscrollFactory provides` 它）。 */
data object RubberBandOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = RubberBandOverscroll()
}
