package io.github.clearkit.starter.hook.ui

/**
 * 边缘胶囊（设计 38–49）里不依赖 View 的计算：尺寸、底部“暂时隐藏”落点的判定。单位 dp 或 px 见参数名。
 */
object CapsuleLogic {
    /** 胶囊看得见的宽、高（dp）：小 / 中 / 大。 */
    fun visualDp(size: String): Pair<Int, Int> = when (size) {
        "small" -> 30 to 48
        "large" -> 42 to 64
        else -> 36 to 56
    }

    /** 触摸范围（dp）：至少 48×56，比看得见的大一圈。 */
    fun touchDp(size: String, collapsed: Boolean): Pair<Int, Int> {
        if (collapsed) return 48 to 56
        val (w, h) = visualDp(size)
        return maxOf(48, w + 12) to maxOf(56, h + 8)
    }

    /** 收起后的把手（dp）。 */
    const val HANDLE_W = 6
    const val HANDLE_H = 40

    /** 底部落点：屏幕底部居中、宽 [zoneWidth] 高 [zoneHeight]，底边离可用区域底部 [bottomGap]。 */
    fun hideZone(parentWidth: Int, safeBottom: Int, zoneWidth: Int, zoneHeight: Int, bottomGap: Int): TouchBox {
        val left = (parentWidth - zoneWidth) / 2f
        val bottom = (safeBottom - bottomGap).toFloat()
        return TouchBox(left, bottom - zoneHeight, left + zoneWidth, bottom)
    }

    /** 拖动中胶囊中心落在落点范围（四周再放宽 [slop]）里就算命中。 */
    fun inHideZone(centerX: Float, centerY: Float, zone: TouchBox, slop: Float): Boolean =
        centerX in (zone.left - slop)..(zone.right + slop) && centerY in (zone.top - slop)..(zone.bottom + slop)

    /** 横竖屏各记一份位置。 */
    fun orientationKey(width: Int, height: Int): String = if (width > height) "landscape" else "portrait"
}
