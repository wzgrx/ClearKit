package io.github.clearkit.starter.hook.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.clearkit.starter.model.adapt.AdaptCardText
import java.lang.ref.WeakReference

/**
 * 【宿主】里的适配卡片（顶部浮出，不挡操作）：
 * - 新版本需要现场适配时：标题“正在适配【宿主】 x.y.z”，进度条和“DexKit 解析 · 查找状态 n / 总数”实时更新；
 * - 适配完成：进度条填满变绿，显示找到几个、几个功能已生效，几秒后自动收起；

 * 只在需要重新解析时出现（和 QAuxiliary、PureDuPan 一样：缓存 / 适配表还有效就不提示）；已经适配好的版本不显示。
 * - 超时：提示打开【模块】 App 完成适配。
 *
 * 卡片跟着当前页面走：适配途中换了页面（开屏 → 首页），由入口调用 [moveTo] 挂到新页面上，状态不丢。
 */
object AdaptCard {
    private const val TAG = "ClearStarter"
    private const val LAYER = 11
    private const val RESULT_MS = 4500L
    /** 用户点了“后台进行”后，完成时只短暂显示结果。 */
    private const val READY_MS = 3000L

    private enum class Mode { ADAPTING, DONE, TIMEOUT }

    private data class State(
        val mode: Mode,
        val title: String,
        val subtitle: String,
        val done: Int = 0,
        val total: Int = 0,
        val note: String? = null,
        val action: String? = null,
        val autoCloseMs: Long = 0,
    )

    private val main = Handler(Looper.getMainLooper())
    private var state: State? = null
    /**
     * 当前挂着的卡片。单例里只弱引用（不拖住已关闭的页面）；卡片挂在页面上时由它的视图（tag）强引用着，
     * 不会在显示途中被回收（以前只有弱引用，包装对象被回收后到时间关不掉）。
     */
    private var cardRef: WeakReference<Card>? = null
    private var card: Card?
        get() = cardRef?.get()
        set(value) { cardRef = value?.let { WeakReference(it) } }
    private var dismissed = false
    private var onOpenApp: (() -> Unit)? = null
    private val autoClose = Runnable { close(animate = true) }

    /** 开始现场适配。 */
    /** 卡片正显示着（别的顶部提示据此让开）。 */
    val showing: Boolean get() = card != null

    fun adapting(activity: Activity?, versionName: String) {
        dismissed = false
        show(activity, State(Mode.ADAPTING, "正在适配【宿主】 $versionName", "【模块】在手机上解析这个版本，几秒到十几秒，期间可以照常使用", action = "后台进行"))
    }

    /** 有可用的适配表，装好或更新后第一次启动时逐项核对。 */
    fun verifying(activity: Activity?, versionName: String) {
        dismissed = false
        show(activity, State(Mode.ADAPTING, "正在适配【宿主】 $versionName", "【模块】用内置适配表逐项核对，一两秒就好，期间可以照常使用", action = "后台进行"))
    }

    /** 解析进度。卡片被用户收起后不再弹出来，只更新状态。 */
    fun progress(done: Int, total: Int) {
        val s = state ?: return
        if (s.mode != Mode.ADAPTING) return
        state = s.copy(done = done, total = total)
        card?.render(state!!, animate = true)
    }

    /** 适配完成（新表已换上）。 */
    fun adapted(activity: Activity?, versionName: String, found: Int, total: Int, active: Int) {
        val wasDismissed = dismissed
        dismissed = false
        show(
            activity,
            State(
                Mode.DONE, AdaptCardText.adaptedTitle(versionName), AdaptCardText.result(found, total, active), found, total,
                AdaptCardText.missingNote(found, total), "好的", if (wasDismissed) READY_MS else RESULT_MS,
            ),
        )
    }

    /** 等太久还没好。[openApp] 打开【模块】 App。 */
    fun timeout(activity: Activity?, versionName: String, openApp: () -> Unit) {
        dismissed = false
        onOpenApp = openApp
        show(activity, State(Mode.TIMEOUT, "【宿主】 $versionName 还没适配完成", "打开【模块】会在本机适配，完成后这里自动生效", action = "打开【模块】"))
    }

    /** 当前页面换了（开屏 → 首页、进出详情）：卡片还在显示时挂到新页面上。 */
    fun moveTo(activity: Activity) {
        val s = state ?: return
        if (dismissed) return
        val current = card
        if (current != null && current.activity.get() === activity && current.view.isAttachedToWindow) return
        current?.detach()
        attach(activity, s, entering = false)
    }

    private fun show(activity: Activity?, s: State) {
        state = s
        main.removeCallbacks(autoClose)
        val current = card
        if (current != null && current.view.isAttachedToWindow && (activity == null || current.activity.get() === activity)) {
            current.render(s, animate = true)
        } else {
            current?.detach()
            if (activity != null) attach(activity, s, entering = true)
        }
        if (s.autoCloseMs > 0) main.postDelayed(autoClose, s.autoCloseMs)
        Log.i(TAG, "adapt card: ${s.mode} ${s.title} · ${s.subtitle}")
    }

    private fun attach(activity: Activity, s: State, entering: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) return
        val overlay = HostWindows.overlayFor(activity) ?: return
        val c = Card(activity)
        c.render(s, animate = false)
        c.view.tag = c
        val t = c.t
        val decor = overlay.decor
        // 页面刚切换时（开屏 → 首页）新窗口还没拿到状态栏高度，读出来是 0：先用系统的状态栏高度兜底，排好版后按实际值再校正
        fun topMargin() = maxOf(SystemInsets.top(decor), statusBarHeight(activity)) + t.dp(8)
        decor.addView(c.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(t.dp(12), topMargin(), t.dp(12), 0)
        })
        c.view.post {
            val lp = c.view.layoutParams as? FrameLayout.LayoutParams ?: return@post
            val want = topMargin()
            if (lp.topMargin != want) { lp.topMargin = want; c.view.layoutParams = lp }
        }
        c.open(overlay.window)
        card = c
        if (entering) {
            c.view.alpha = 0f
            c.view.translationY = -t.dp(40).toFloat()
            c.view.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(SpringInterpolator(0.72f)).start()
        }
    }

    /** 系统资源里的状态栏高度（窗口还没分到 insets 时用；全屏页面上卡片也不会顶到刘海里）。 */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(activity: Activity): Int {
        val id = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) activity.resources.getDimensionPixelSize(id) else 0
    }

    private fun close(animate: Boolean) {
        main.removeCallbacks(autoClose)
        val c = card
        card = null
        if (state?.mode != Mode.ADAPTING) state = null
        c ?: return
        if (!animate) { c.detach(); return }
        c.view.animate().alpha(0f).translationY(-c.t.dp(24).toFloat()).setDuration(200).withEndAction { c.detach() }.start()
    }

    private fun onAction(s: State) {
        when (s.mode) {
            Mode.ADAPTING -> { dismissed = true; close(animate = true) }
            Mode.DONE -> close(animate = true)
            Mode.TIMEOUT -> { close(animate = true); onOpenApp?.invoke() }
        }
    }

    /** 一张卡片的视图。 */
    private class Card(activity: Activity) {
        val activity = WeakReference(activity)
        val t = HostTheme.of(activity)
        private val ui = Ui(t)
        private val title = ui.text("", TextSize.ITEM, t.text, t.bold, maxLines = 1)
        private val subtitle = ui.text("", TextSize.CAPTION, t.textSecondary, maxLines = 2)
        private val bar = ProgressBar(activity, t)
        private val counter = ui.text("", TextSize.CAPTION, t.textSecondary, maxLines = 1)
        private val percent = ui.text("", TextSize.CAPTION, t.textSecondary, t.medium, maxLines = 1)
        private val note = ui.text("", TextSize.CAPTION, t.warn, maxLines = 2)
        private val action = ui.text("", TextSize.LABEL, t.accentText, t.medium, maxLines = 1).apply {
            gravity = Gravity.CENTER
            setPadding(t.dp(12), 0, t.dp(12), 0)
            ui.compactTarget(this, t.accentSoft, 30)
        }
        private val badge = TextView(activity).apply {
            text = "薯"
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            typeface = t.bold
            background = ui.rounded(t.accent, 9f)
        }
        private val touch: OverlayTouchTarget
        val view: LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rounded(t.surface, 20f, t.divider, 0.5f)
            elevation = t.dp(10).toFloat()
            setPadding(t.dp(14), t.dp(12), t.dp(10), t.dp(12))
            val head = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(badge, LinearLayout.LayoutParams(t.dp(30), t.dp(30)))
                addView(ui.vertical(2, title, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = t.dp(10) })
                addView(action)
            }
            addView(head)
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, t.dp(6)).apply { topMargin = t.dp(12); marginEnd = t.dp(4) })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(counter, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(percent)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = t.dp(6); marginEnd = t.dp(4) })
            addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = t.dp(4) })
        }

        init {
            touch = OverlayTouchTarget(view, LAYER)
        }

        fun render(s: State, animate: Boolean) {
            title.text = s.title
            subtitle.text = s.subtitle
            action.text = s.action.orEmpty()
            action.visibility = if (s.action == null) View.GONE else View.VISIBLE
            action.setOnClickListener { onAction(s) }
            note.text = s.note.orEmpty()
            note.visibility = if (s.note == null) View.GONE else View.VISIBLE
            val done = s.mode == Mode.DONE
            badge.text = if (done) "✓" else "薯"
            badge.background = ui.rounded(if (done) t.ok else t.accent, 9f)
            when (s.mode) {
                Mode.ADAPTING -> {
                    counter.text = AdaptCardText.counter(s.done, s.total)
                    percent.text = if (s.total > 0) "${(AdaptCardText.fraction(s.done, s.total) * 100).toInt()}%" else ""
                    bar.set(AdaptCardText.fraction(s.done, s.total), indeterminate = s.total <= 0, color = t.accent, animate = animate)
                    bar.visibility = View.VISIBLE
                }
                Mode.DONE -> {
                    counter.text = "本机解析 · 查找状态 ${s.done} / ${s.total}"
                    percent.text = "完成"
                    bar.set(1f, indeterminate = false, color = if (s.note == null) t.ok else t.warn, animate = animate)
                    bar.visibility = View.VISIBLE
                }
                Mode.TIMEOUT -> {
                    counter.text = ""
                    percent.text = ""
                    bar.visibility = View.GONE
                }
            }
            view.contentDescription = listOf(s.title, s.subtitle, counter.text, s.note.orEmpty()).filter { it.isNotEmpty() }.joinToString("，")
        }

        @SuppressLint("ClickableViewAccessibility")
        fun open(window: android.view.Window?) {
            // 卡片上没被按钮接住的触摸在这里吃掉，不漏给下面的页面
            view.setOnTouchListener { _, _ -> true }
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    // 页面销毁、卡片关掉：不再留在窗口闸门里，也不再被记着
                    touch.detach()
                    bar.stop()
                    if (card === this@Card) card = null
                }
            })
            window?.let(touch::attach)
        }

        fun detach() {
            touch.detach()
            bar.stop()
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    /** 圆角进度条：位置用弹簧追过去；不知道总数时一小段光带来回扫（不确定进度）。 */
    private class ProgressBar(context: Context, private val t: HostTheme) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private var shown = 0f
        private var color = t.accent
        private var indeterminate = false
        private var sweep = 0f
        private val spring = ViewSpring(260f, 1f, threshold = 0.001f) { shown = it; invalidate() }
        private var sweeper: ValueAnimator? = null

        fun set(fraction: Float, indeterminate: Boolean, color: Int, animate: Boolean) {
            this.color = color
            this.indeterminate = indeterminate
            if (indeterminate) startSweep() else stopSweep()
            if (animate && !indeterminate) spring.animate(shown, fraction) else { spring.cancel(); shown = fraction }
            invalidate()
        }

        fun stop() { spring.cancel(); stopSweep() }

        private fun startSweep() {
            if (sweeper != null) return
            sweeper = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { sweep = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        private fun stopSweep() { sweeper?.cancel(); sweeper = null }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stop()
        }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            paint.color = t.surfaceMuted
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, r, r, paint)
            paint.color = color
            if (indeterminate) {
                val seg = width * 0.3f
                val x = -seg + (width + seg) * sweep
                rect.set(x.coerceAtLeast(0f), 0f, (x + seg).coerceAtMost(width.toFloat()), height.toFloat())
                if (rect.width() > 0f) canvas.drawRoundRect(rect, r, r, paint)
            } else {
                val w = (width * shown.coerceIn(0f, 1f)).coerceAtLeast(if (shown > 0f) height.toFloat() else 0f)
                rect.set(0f, 0f, w, height.toFloat())
                if (w > 0f) canvas.drawRoundRect(rect, r, r, paint)
            }
        }
    }
}
