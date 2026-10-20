package io.github.clearkit.starter.hook.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * 页面上的临时反馈（设计稿 17）：底部结果提示条（可带一个按钮：撤销、查看……）和顶部进度胶囊。
 *
 * 触摸：提示加在当前最上层窗口（[HostWindows.overlayFor]：模块的面板、宿主的评论弹层或页面本身）的 decor 上，
 * 同时登记到这个窗口的 [FloatingTouchGate]（宿主弹层拿不到 Window 时只靠提示自己接住触摸）。
 * 显示期间，按在提示可见范围里的手势由窗口层直接交给提示，【宿主】的页面收不到（不会点穿到下面的
 * 底部互动栏）；其它地方照常交给页面。按住提示时暂停自动消失，松手后至少再留一会儿。
 */
object HostToast {
    private const val TAG = "ClearStarter"
    /** 提示盖在悬浮按钮上面（悬浮按钮是 0 层）。 */
    private const val LAYER = 10

    private val main = Handler(Looper.getMainLooper())
    private var snack: WeakReference<Snack>? = null
    private var capsule: WeakReference<Capsule>? = null

    /**
     * 提示条，可带一个按钮；会替换掉上一条。默认在底部；[top] 为 true 时放在状态栏下面
     * （6.9.2，“模块已加载”这类一打开就弹的提示：底部正好是【宿主】的底栏和播放控件，挡手）。
     */
    fun show(activity: Activity, message: String, detail: String? = null, action: String? = null, durationMs: Long = 4000, top: Boolean = false, onAction: (() -> Unit)? = null) {
        val overlay = HostWindows.overlayFor(activity) ?: return
        val decor = overlay.decor
        snack?.get()?.close("replaced")
        val t = HostTheme.of(activity)
        val ui = Ui(t)
        val texts = ui.vertical(
            1,
            ui.text(message, TextSize.BODY, t.onInverse, t.medium, maxLines = 2),
            *listOfNotNull(detail?.let { ui.text(it, TextSize.CAPTION, (t.onInverse and 0x00FFFFFF) or 0xB3000000.toInt(), maxLines = 1) }).toTypedArray(),
        )
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ui.rounded(t.inverse, 16f)
            setPadding(t.dp(16), t.dp(if (action != null) 4 else 10), t.dp(8), t.dp(if (action != null) 4 else 10))
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (action != null) { topMargin = t.dp(6); bottomMargin = t.dp(6) }
            })
            elevation = t.dp(6).toFloat()
        }
        val timeout = ToastTiming.timeout(durationMs, action != null, recommendedTimeout(activity, durationMs, action != null))
        val s = Snack(row, message, timeout)
        if (action != null) {
            val btn = ui.text(action, TextSize.BODY, t.inverseAccent, t.bold).apply {
                gravity = Gravity.CENTER
                setPadding(t.dp(14), 0, t.dp(14), 0)
                // 看起来 36dp 高，触控区 48dp。
                ui.compactTarget(this, (t.onInverse and 0x00FFFFFF) or 0x24000000, 36)
                setOnClickListener {
                    if (s.closing) return@setOnClickListener
                    Log.i(TAG, "toast action: $action ($message)")
                    s.close("action")
                    onAction?.invoke()
                }
            }
            row.addView(btn)
        }
        decor.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, if (top) Gravity.TOP else Gravity.BOTTOM).apply {
            if (top) setMargins(t.dp(12), maxOf(SystemInsets.top(decor), statusBarHeight(activity)) + t.dp(8), t.dp(12), 0)
            else setMargins(t.dp(12), 0, t.dp(12), t.dp(20) + SystemInsets.bottom(decor))
        })
        row.alpha = 0f
        row.translationY = t.dp(if (top) -24 else 24).toFloat()
        row.animate().alpha(1f).translationY(0f).setDuration(180).start()
        s.open(overlay.window)
        snack = WeakReference(s)
        Log.i(TAG, "toast shown: $message${action?.let { " [$it]" } ?: ""}, ${timeout}ms, window=${overlay.window?.attributes?.title ?: decor.javaClass.simpleName}")
    }

    /** 页面刚建好时窗口边距可能还没算出来：至少让开状态栏。 */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(activity: Activity): Int {
        val id = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) activity.resources.getDimensionPixelSize(id) else 0
    }

    /** 系统无障碍设置里的“操作时间”（Android 10+），没有时 null。 */
    private fun recommendedTimeout(activity: Activity, durationMs: Long, hasAction: Boolean): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val am = activity.getSystemService(AccessibilityManager::class.java) ?: return null
        val flags = AccessibilityManager.FLAG_CONTENT_TEXT or (if (hasAction) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0)
        return am.getRecommendedTimeoutMillis(durationMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(), flags).toLong()
    }

    /** 一条底部提示：自动消失的计时、按住暂停、窗口层触摸登记。 */
    private class Snack(private val row: View, private val message: String, private val timeoutMs: Long) {
        private val expire = Runnable { close("timeout") }
        private var deadline = 0L
        private var remaining = 0L
        private var pressed = false
        private val touch = OverlayTouchTarget(row, LAYER) { down -> onPress(down) }
        val closing: Boolean get() = touch.closing

        @SuppressLint("ClickableViewAccessibility")
        fun open(window: android.view.Window?) {
            // 提示区域里没被按钮接住的触摸也在这里吃掉，不漏给下面的页面（窗口闸门失效时的兜底）。
            row.setOnTouchListener { _, _ -> true }
            row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    // 窗口关掉、页面销毁等：不再留在闸门里。
                    main.removeCallbacks(expire)
                    touch.detach()
                }
            })
            // 宿主弹层拿不到 Window：只靠上面的触摸监听接住按在提示上的手势。
            window?.let(touch::attach)
            schedule(timeoutMs)
        }

        private fun schedule(delay: Long) {
            main.removeCallbacks(expire)
            deadline = SystemClock.uptimeMillis() + delay
            main.postDelayed(expire, delay)
        }

        private fun onPress(down: Boolean) {
            if (closing || down == pressed) return
            pressed = down
            if (down) {
                main.removeCallbacks(expire)
                remaining = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            } else {
                schedule(ToastTiming.afterRelease(remaining))
            }
        }

        fun close(reason: String) {
            if (touch.closing) return
            touch.closing = true
            main.removeCallbacks(expire)
            Log.i(TAG, "toast closed ($reason): $message")
            fadeOutAndRemove(row) { touch.detach() }
        }
    }

    private fun fadeOutAndRemove(view: View, after: () -> Unit) {
        view.animate().alpha(0f).setDuration(150).withEndAction {
            (view.parent as? ViewGroup)?.removeView(view)
            after()
        }.start()
    }

    /** 顶部进度胶囊。 */
    private class Capsule(val row: View, val ring: RingView, val text: TextView) {
        val touch = OverlayTouchTarget(row, LAYER)
    }

    /**
     * Top capsule "正在保存 4/8"; call again to update, [hideProgress] to remove. [onTap] makes the
     * capsule tappable (e.g. "点按取消"); it is replaced on every call.
     */
    fun progress(activity: Activity, label: String, fraction: Float, onTap: (() -> Unit)? = null) {
        val overlay = HostWindows.overlayFor(activity) ?: return
        val decor = overlay.decor
        val existing = capsule?.get()
        if (existing != null && existing.row.parent === decor && !existing.touch.closing) {
            existing.text.text = label
            existing.ring.fraction = fraction
            existing.row.setOnClickListener(onTap?.let { tap -> View.OnClickListener { tap() } })
            existing.row.isClickable = true
            return
        }
        existing?.let { removeCapsule(it) }
        val t = HostTheme.of(activity)
        val ui = Ui(t)
        val ring = RingView(activity, t).apply { this.fraction = fraction }
        val text = ui.text(label, TextSize.LABEL, 0xFFFFFFFF.toInt())
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 看起来 40dp 高，可点（取消）的范围 48dp。
            background = ui.insetPill(0xEB1F1F21.toInt(), 40, pressed = false)
            minimumHeight = t.dp(48)
            setPadding(t.dp(8), t.dp(10), t.dp(16), t.dp(10))
            addView(ring, LinearLayout.LayoutParams(t.dp(28), t.dp(28)))
            addView(text, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = t.dp(10) })
            elevation = t.dp(6).toFloat()
            if (onTap != null) setOnClickListener { onTap() }
            // 没有点按动作时也接住按在胶囊上的触摸，不漏给下面的页面。
            isClickable = true
            contentDescription = label
        }
        val topInset = SystemInsets.top(decor)
        decor.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = topInset + t.dp(52)
        })
        val c = Capsule(row, ring, text)
        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { c.touch.detach() }
        })
        overlay.window?.let(c.touch::attach)
        capsule = WeakReference(c)
    }

    fun hideProgress() {
        capsule?.get()?.let(::removeCapsule)
        capsule = null
    }

    private fun removeCapsule(c: Capsule) {
        if (c.touch.closing) return
        c.touch.closing = true
        fadeOutAndRemove(c.row) { c.touch.detach() }
    }

    @android.annotation.SuppressLint("ViewConstructor") // 宿主进程里纯代码构造，从不从 XML 加载，不需要 (Context, AttributeSet) 构造
    class RingView(context: android.content.Context, private val t: HostTheme) : View(context) {
        var fraction = 0f
            set(value) { field = value.coerceIn(0f, 1f); invalidate() }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = t.dp(3).toFloat(); color = 0x40FFFFFF }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = t.dp(3).toFloat(); color = 0xFFFF5A6E.toInt(); strokeCap = Paint.Cap.ROUND }
        private val box = RectF()

        override fun onDraw(canvas: Canvas) {
            val inset = t.dp(4).toFloat()
            box.set(inset, inset, width - inset, height - inset)
            canvas.drawOval(box, track)
            canvas.drawArc(box, -90f, 360f * fraction, false, arc)
        }
    }
}
