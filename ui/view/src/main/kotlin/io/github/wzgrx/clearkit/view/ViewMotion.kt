package io.github.wzgrx.clearkit.view

import android.animation.TimeInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 页面转场（规范 v1，和 App 端 PageStack 的 PageMotion 同一套）：照 HyperOS 系统转场，新页整页滑入，
 * 下层左移 [PARALLAX] 并压暗到 [DIM]，[PAGE_MS] 走完小米的物理曲线（[physic]）。宿主进程里的设置窗、抽屉切页用它。
 */
object ViewMotion {
    const val PAGE_MS = 500L

    /** 下层左移的比例（相对页面宽度）。 */
    const val PARALLAX = 0.25f

    /** 下层完全被盖住时遮罩（黑色）的不透明度。 */
    const val DIM = 0.5f

    /** 小米的物理插值器：欠阻尼弹簧的位移曲线，[response] 秒一个周期、[damping] 阻尼比，在 0..1 的时间上走完。 */
    fun physic(response: Float = 0.8f, damping: Float = 0.95f): TimeInterpolator {
        val k = (2.0 * PI / response).let { it * it }
        val c = damping * 4.0 * PI / response
        val w = sqrt(4.0 * k - c * c) / 2.0
        val r = -c / 2.0
        val c2 = r / w
        return TimeInterpolator { t -> (exp(r * t) * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat().coerceAtMost(1f) }
    }
}
