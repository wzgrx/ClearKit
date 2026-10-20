package io.github.clearkit.starter.hook.ui

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 弹簧的物理（纯函数，可单测）：质量为 1，刚度 [stiffness]，阻尼比 [dampingRatio]（1 为临界阻尼、不回弹，
 * 小于 1 会冲过头再回来）。按 1/240 秒的小步做半隐式欧拉积分，帧时间不均匀时也稳定。
 */
object SpringPhysics {
    private const val STEP = 1f / 240f

    data class State(val value: Float, val velocity: Float)

    fun advance(s: State, target: Float, stiffness: Float, dampingRatio: Float, seconds: Float): State {
        var x = s.value
        var v = s.velocity
        val c = 2f * dampingRatio * sqrt(stiffness)
        var left = seconds.coerceIn(0f, 0.05f)
        while (left > 0f) {
            val h = minOf(STEP, left)
            v += (-stiffness * (x - target) - c * v) * h
            x += v * h
            left -= h
        }
        return State(x, v)
    }

    /** 离目标不到 [threshold] 像素、速度也很小时算停住。 */
    fun settled(s: State, target: Float, threshold: Float): Boolean = abs(s.value - target) < threshold && abs(s.velocity) < threshold * 20f

    /**
     * 松手时按速度推算“滑到哪里停”（iOS 的投射）：速度 [velocity]（px/s）乘一个衰减常数，
     * 甩得快的悬浮按钮、浮层能按甩的方向判断去向。
     */
    fun project(value: Float, velocity: Float, decay: Float = 0.99f): Float = value + velocity / 1000f * decay / (1f - decay)
}

/**
 * 【宿主】进程里的弹簧动画（不依赖 androidx.dynamicanimation）：按屏幕刷新逐帧推进，可以带初速度（松手的手指速度），
 * 动画途中改目标会从当前位置和速度接着走，不会跳。
 */
class ViewSpring(
    private val stiffness: Float,
    private val dampingRatio: Float,
    private val threshold: Float = 0.5f,
    private val onUpdate: (Float) -> Unit,
) : Choreographer.FrameCallback {
    private var state = SpringPhysics.State(0f, 0f)
    private var target = 0f
    private var last = 0L
    private var onEnd: (() -> Unit)? = null
    var running = false
        private set
    val velocity: Float get() = if (running) state.velocity else 0f

    /** 从 [from] 开始弹到 [to]；[velocity] 为初速度（px/s），不给时沿用正在跑的速度。 */
    fun animate(from: Float, to: Float, velocity: Float = this.velocity, end: (() -> Unit)? = null) {
        state = SpringPhysics.State(from, velocity)
        target = to
        onEnd = end
        if (!running) {
            running = true
            last = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun cancel() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        onEnd = null
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (last == 0L) 1f / 120f else (frameTimeNanos - last) / 1e9f
        last = frameTimeNanos
        state = SpringPhysics.advance(state, target, stiffness, dampingRatio, dt)
        if (SpringPhysics.settled(state, target, threshold)) {
            running = false
            onUpdate(target)
            onEnd?.also { onEnd = null }?.invoke()
            return
        }
        onUpdate(state.value)
        Choreographer.getInstance().postFrameCallback(this)
    }
}

/**
 * 把弹簧从 0 弹到 1 的过程当作动画曲线（给 ValueAnimator / ViewPropertyAnimator 用）：
 * [dampingRatio] 为 1 时是不回弹的临界阻尼，小于 1 会冲过 1 再回来。弹簧的快慢按动画时长换算，
 * 保证在时长结束时停稳（结尾按终点值归一，正好落在 1）。
 */
class SpringInterpolator(private val dampingRatio: Float = 1f, private val settle: Float = 9f) : android.animation.TimeInterpolator {
    private val end = curve(1f)

    override fun getInterpolation(input: Float): Float = if (input >= 1f) 1f else curve(input) / end

    /** 归一化时间 [t]（0–1）时的位置；角频率取 [settle]，在 t = 1 时余下不到千分之一。 */
    private fun curve(t: Float): Float {
        val w = settle
        val z = dampingRatio
        return if (z >= 1f) {
            1f - (1f + w * t) * kotlin.math.exp(-w * t)
        } else {
            val wd = w * kotlin.math.sqrt(1f - z * z)
            1f - kotlin.math.exp(-z * w * t) * (kotlin.math.cos(wd * t) + z * w / wd * kotlin.math.sin(wd * t))
        }
    }
}
