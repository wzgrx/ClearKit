package io.github.clearkit.starter.hook.ui

/**
 * 悬浮按钮里不依赖 View 的计算（可单测）：位置换算、吸边、键盘避让、手势判定、淡出透明度。
 * 规则对照旧版 FloatingEntryPosition / FloatingEntryGesturePolicy / FloatingEntryScrollFadePolicy。
 */
object FloatingLogic {
    /** 滚动时淡到的透明度（设计文档第 5 节：淡到 30%）。 */
    const val FADED_ALPHA = 0.3f
    /** 停止滚动多久后恢复。 */
    const val FADE_RESTORE_MS = 600L
    /** 闲置多久后收成边缘把手（阶段 0 已上线 3 秒，设置说明里也写的 3 秒）。 */
    const val COLLAPSE_AFTER_MS = 3000L
    /** 键盘上方留的间距（dp）。 */
    const val KEYBOARD_GAP_DP = 8
    /**
     * 闲置收起后的宽限期：这段时间里按在球刚才的位置仍算按在球上（只展开），
     * 不让这一下落到下面的图片上触发【宿主】自己的长按菜单。收起动画 260ms，留足一点余量。
     */
    const val COLLAPSE_GRACE_MS = 800L

    /** 纵向比例 → 顶边位置（px），[minY]..[maxY] 是顶边可以去的范围。 */
    fun topFor(fraction: Float, minY: Int, maxY: Int): Int {
        if (maxY <= minY) return minY
        val f = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0.5f
        return (minY + (maxY - minY) * f + 0.5f).toInt()
    }

    /** 顶边位置 → 纵向比例。 */
    fun fractionFor(top: Int, minY: Int, maxY: Int): Float {
        if (maxY <= minY) return 0f
        return ((top - minY).toFloat() / (maxY - minY)).coerceIn(0f, 1f)
    }

    /**
     * 松手后靠哪边：设置固定左 / 右时用设置；“拖到哪边靠哪边”时看按钮中心在屏幕哪一半。
     */
    fun snapLeft(side: String, centerX: Float, parentWidth: Int, currentLeft: Boolean): Boolean = when (side) {
        "left" -> true
        "right" -> false
        else -> if (parentWidth <= 0) currentLeft else centerX < parentWidth / 2f
    }

    /**
     * 软键盘弹出时显示的位置：按钮底边要在键盘顶边上方 [gap] 以上；没被挡住时不动。
     * [imeTop] 为 null 表示键盘没弹出。只是临时显示，不写回记住的位置。
     */
    fun avoidKeyboard(top: Int, height: Int, imeTop: Int?, gap: Int, minY: Int): Int {
        if (imeTop == null) return top
        val limit = imeTop - gap - height
        return if (top > limit) maxOf(minY, limit) else top
    }

    /** 滚动时的透明度：不高于 [FADED_ALPHA]，本来就更透明时保持原样。 */
    fun fadedAlpha(base: Float): Float = minOf(base.coerceIn(0f, 1f), FADED_ALPHA)

    /** 设置里的不透明度（60–100）→ alpha。 */
    fun alphaFor(opacityPercent: Int): Float = opacityPercent.coerceIn(60, 100) / 100f

    /** 触控区：至少 48dp，比视觉尺寸大 4dp。 */
    fun touchSize(visualDp: Int): Int = maxOf(48, visualDp.coerceIn(40, 56) + 4)

    /**
     * 窗口层判断按下点是否归悬浮按钮：落在按钮现在的范围里；或按钮刚因闲置收起、
     * 还在宽限期（[recentUntil] 之前，和 MotionEvent.eventTime 同为开机毫秒）内，落在它展开时的范围里。
     */
    fun claims(x: Float, y: Float, now: Long, current: TouchBox?, recent: TouchBox?, recentUntil: Long): Boolean =
        current?.contains(x, y) == true || (recent != null && now < recentUntil && recent.contains(x, y))

    /** 减少动效：设置打开，或系统关闭了动画（旧版 FloatingEntrySessionState.shouldReduceMotion）。 */
    fun reduceMotion(setting: Boolean, animatorsEnabled: Boolean): Boolean = setting || !animatorsEnabled

    /** 角标文字：多于 1 项才显示，超过 9 写“9+”（旧版 FloatingEntryView.updateBadgeCount）。 */
    fun badgeLabel(count: Int): String? = when {
        count <= 1 -> null
        count > 9 -> "9+"
        else -> count.toString()
    }

    /** 读屏标签：名称、可保存的项数、收起和锁定状态。 */
    fun describe(label: String, badge: Int, collapsed: Boolean, locked: Boolean): String = buildString {
        append(label)
        if (badge > 1) append("，可保存 ").append(badge).append(" 项")
        if (collapsed) append("，已收起，点击展开")
        if (locked) append("，位置已锁定")
        append("，长按管理")
    }

    // ---- 摆放 ----

    /** 状态栏下方留的距离（dp），避开页面标题栏。 */
    const val TOP_MARGIN_DP = 56
    /** 导航栏上方留的距离（dp），避开底部操作栏。 */
    const val BOTTOM_MARGIN_DP = 72
    /** 离屏幕边的距离（dp）；贴边半圆为 0。 */
    const val EDGE_MARGIN_DP = 12

    /** 顶边可以去的范围（px）。 */
    data class VerticalRange(val minY: Int, val maxY: Int)

    /**
     * 按钮顶边的纵向范围。放在别的窗口里（[fixedContainer]，例如评论弹层）时整个容器都能去；
     * 否则避开状态栏下 [topMargin]、导航栏上 [bottomMargin]，空间不够时上下限重合。
     */
    fun verticalRange(
        parentHeight: Int,
        viewHeight: Int,
        insetTop: Int,
        insetBottom: Int,
        topMargin: Int,
        bottomMargin: Int,
        fixedContainer: Boolean,
    ): VerticalRange {
        if (fixedContainer) return VerticalRange(0, (parentHeight - viewHeight).coerceAtLeast(0))
        val minY = insetTop + topMargin
        val maxY = parentHeight - insetBottom - viewHeight - bottomMargin
        return VerticalRange(minY, maxY.coerceAtLeast(minY))
    }

    /** 离屏幕边的距离（px）：贴边半圆贴着边，其它留 [edgeMargin]。 */
    fun edgeMargin(shape: String, edgeMargin: Int): Int = if (shape == "half") 0 else edgeMargin

    /** 横坐标：收起时贴着边；展开时离边 [margin]。[touchWidth] 是按钮触控区宽度。 */
    fun xFor(collapsed: Boolean, left: Boolean, parentWidth: Int, touchWidth: Int, margin: Int): Float =
        if (collapsed) (if (left) 0f else (parentWidth - touchWidth).toFloat())
        else if (left) margin.toFloat() else (parentWidth - touchWidth - margin).toFloat()

    /** 拖动时某一轴的位置：起点加手指位移，不出父容器。 */
    fun dragTo(start: Float, raw: Float, down: Float, parentSize: Int, viewSize: Int): Float =
        (start + raw - down).coerceIn(0f, (parentSize - viewSize).toFloat().coerceAtLeast(0f))

    /**
     * 要不要跳过这次摆放：目标和上一次一样，并且已经在那儿（或这次本来就要动画过去、动画已经在路上）。
     * 自己写回位置后设置会推回来一次，这样不会打断正在播的吸边动画。
     */
    fun placeUnchanged(target: Pair<Float, Float>, last: Pair<Float, Float>?, currentX: Float, currentY: Float, animate: Boolean): Boolean =
        target == last && (currentX == target.first && currentY == target.second || animate)

    /** 键盘顶边（父容器坐标）；[imeHeight] 为 null（没弹出）时 null。 */
    fun keyboardTop(parentHeight: Int, imeHeight: Int?): Int? = imeHeight?.let { parentHeight - it }

    /** Android 10 及以下没有输入法 insets：窗口可见区比父容器矮出 [threshold] 以上才算键盘弹出。 */
    fun legacyImeHeight(parentHeight: Int, visibleBottom: Int, threshold: Int): Int? =
        (parentHeight - visibleBottom).takeIf { it > threshold }

    // ---- 手势结果 ----

    /** 抬手之后做什么。 */
    enum class Release {
        /** 收起时第一下只展开，不当作点击。 */
        EXPAND,
        /** 回到记住的位置（锁定时的拖动、长按后抬手、没拖也不算点击）。 */
        PLACE,
        /** 拖完：决定靠哪边、记住位置并吸边。 */
        SETTLE,
        /** 点按。 */
        TAP,
    }

    fun release(collapsed: Boolean, dragged: Boolean, locked: Boolean, click: Boolean): Release = when {
        collapsed -> Release.EXPAND
        dragged -> if (locked) Release.PLACE else Release.SETTLE
        click -> Release.TAP
        else -> Release.PLACE
    }

    // ---- 长按管理菜单 ----

    enum class MenuItem { TOGGLE_LOCK, HIDE_SESSION, HIDE_PAGE_TYPE, MOVE_SIDE, RESET_POSITION, APPEARANCE, RANGE, DOWNLOADS, SETTINGS }

    /** 长按菜单的副标题。 */
    fun menuSubtitle(locked: Boolean): String = if (locked) "长按管理这个按钮 · 位置已锁定（本次）" else "长按管理这个按钮"

    /** “移到另一边”的文字（菜单和读屏自定义动作共用）。 */
    fun moveLabel(left: Boolean): String = if (left) "移到右侧" else "移到左侧"

    /**
     * 长按菜单的各项和文字（旧版 sessionState.locked）：锁定时不显示会挪动按钮的两项（移到另一边、恢复默认位置）。
     * [settingsBall] 是设置页的设置球（“不再显示”的说法不同）。
     */
    fun menuItems(settingsBall: Boolean, locked: Boolean, left: Boolean): List<Pair<MenuItem, String>> = buildList {
        add(MenuItem.TOGGLE_LOCK to if (locked) "解除本次位置锁定" else "本次锁定位置")
        add(MenuItem.HIDE_SESSION to "本次隐藏")
        add(MenuItem.HIDE_PAGE_TYPE to if (settingsBall) "设置页不再显示" else "在此类页面不再显示")
        if (!locked) {
            add(MenuItem.MOVE_SIDE to moveLabel(left))
            add(MenuItem.RESET_POSITION to "恢复默认位置")
        }
        add(MenuItem.APPEARANCE to "外观设置")
        add(MenuItem.RANGE to "显示范围")
        add(MenuItem.DOWNLOADS to "打开下载中心")
        add(MenuItem.SETTINGS to "打开设置")
    }

    /** 锚定卡的标题：视频页是“当前视频”，直播间是“当前直播”，其它是“当前页面”。 */
    fun anchorTitle(page: String): String = when (page) { "video" -> "当前视频"; "live" -> "当前直播"; else -> "当前页面" }
}

/** 窗口坐标里的一块矩形（左上含、右下不含，和 View 的命中规则一致）。 */
data class TouchBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

/** 窗口闸门里按下点归谁（可单测）。 */
object GateOrder {
    /**
     * [targets] 按登记先后排列；层级（[layer]）高的先问，同层后登记的先问（后加的画在上面），
     * 返回第一个 [claims] 的目标，都不要时 null（交给宿主）。
     */
    fun <T> pick(targets: List<T>, layer: (T) -> Int, claims: (T) -> Boolean): T? {
        if (targets.isEmpty()) return null
        val order = targets.indices.sortedWith(compareByDescending<Int> { layer(targets[it]) }.thenByDescending { it })
        for (i in order) if (claims(targets[i])) return targets[i]
        return null
    }
}

/**
 * 窗口层的手势归属：在 ACTION_DOWN 时用 [pick] 决定整串手势归谁（null 表示交给宿主），
 * 之后的移动、多指、抬起都跟着这个决定，直到 ACTION_UP / ACTION_CANCEL。
 * 宿主拿着手势时，第二根手指落在按钮上也仍交给宿主（不在半路抢手势）；按钮拿着手势时，
 * 即使它中途离开窗口，剩下的事件也不转给宿主（宿主没收到过 DOWN，半截手势会让它状态错乱）。
 */
class GestureOwner<T : Any> {
    var owner: T? = null
        private set

    /** 返回这一个事件该交给谁；null = 宿主。 */
    fun route(actionMasked: Int, pick: () -> T?): T? {
        if (actionMasked == ACTION_DOWN) owner = pick()
        val current = owner
        if (actionMasked == ACTION_UP || actionMasked == ACTION_CANCEL) owner = null
        return current
    }

    private companion object {
        // 与 MotionEvent 的取值相同；写在这里让纯 JVM 单测不依赖 android.jar 的常量。
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_CANCEL = 3
    }
}

/**
 * 手势判定：第二根手指、取消、超过 slop 的移动都不会变成点击；拖动后不触发长按
 * （旧版 FloatingEntryGesturePolicy）。
 */
class FloatingGesture {
    private var pointerId: Int? = null
    private var cancelled = true
    var dragged = false
        private set

    fun down(id: Int) { pointerId = id; cancelled = false; dragged = false }

    /** 返回是否已进入拖动。 */
    fun move(id: Int, distance: Float, slop: Float): Boolean {
        if (cancelled || pointerId != id) return false
        if (!distance.isFinite() || distance >= slop) dragged = true
        return dragged
    }

    /** 多指、窗口失焦、ACTION_CANCEL。 */
    fun cancel() { cancelled = true }

    fun canDrag(id: Int) = !cancelled && pointerId == id
    fun canClick(id: Int) = canDrag(id) && !dragged
    fun canLongPress() = pointerId != null && !cancelled && !dragged
    fun finish() { pointerId = null; cancelled = true; dragged = false }
}

/**
 * 判断一串手势是不是一次干净的单击（阶段 6A6 点封面直达）：单指、移动不超过 [slop]、按住不超过 [maxDurationMs]。
 * 时间和 MotionEvent.eventTime 一样是开机毫秒。
 */
class TapTracker(private val slop: Float, private val maxDurationMs: Long) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var clean = false

    fun down(x: Float, y: Float, time: Long, eligible: Boolean) {
        downX = x; downY = y; downTime = time; clean = eligible
    }

    fun move(x: Float, y: Float) {
        if (clean && kotlin.math.hypot(x - downX, y - downY) > slop) clean = false
    }

    /** 第二根手指、ACTION_CANCEL。 */
    fun cancel() { clean = false }

    /** 抬起：是一次干净的单击返回 true。 */
    fun up(x: Float, y: Float, time: Long): Boolean {
        move(x, y)
        val ok = clean && time - downTime in 0..maxDurationMs
        clean = false
        return ok
    }

    companion object {
        /**
         * 单击最长按多久。宿主卡片的长按可能比系统默认的 500ms 更早触发，这里取 350ms，
         * 按得再久一点就当作可能是长按，一律交给宿主。
         */
        const val MAX_TAP_MS = 350L
    }
}
