package io.github.clearkit.starter.hook.ui

/** 底部提示条多久自动消失（可单测）。 */
object ToastTiming {
    /** 带按钮（撤销、查看……）的提示至少留这么久，留够看清再伸手去点的时间。 */
    const val MIN_WITH_ACTION_MS = 6000L

    /** 手指离开提示后至少再留这么久（按住看的时候不消失，松手不立刻消失）。 */
    const val AFTER_RELEASE_MS = 2500L

    /**
     * 自动消失前的时长：调用方给的 [requestedMs]；带按钮时不少于 [MIN_WITH_ACTION_MS]；
     * 系统无障碍设置里“操作时间”更长时（[recommendedMs]，没有时 null）按系统的。
     */
    fun timeout(requestedMs: Long, hasAction: Boolean, recommendedMs: Long?): Long {
        val base = if (hasAction) maxOf(requestedMs, MIN_WITH_ACTION_MS) else requestedMs.coerceAtLeast(0L)
        return maxOf(base, recommendedMs ?: 0L)
    }

    /** 松手后还留多久：按下时剩下的时间，但不少于 [AFTER_RELEASE_MS]。 */
    fun afterRelease(remainingMs: Long): Long = maxOf(remainingMs, AFTER_RELEASE_MS)
}
