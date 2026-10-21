package io.github.clearkit.starter.hook.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.clearkit.starter.model.adapt.AdaptCardText
import io.github.clearkit.starter.model.adapt.AdaptReport
import java.lang.ref.WeakReference

/**
 * 【宿主】里的顶部卡片（6.13 重做）：适配进度、适配结果、重开、超时、启动提示都用这一张，永远在顶部、排队不叠。
 *
 * - 适配中：进度条和“本机解析 · n / 总数”，可以“后台进行”（收起，结果出来再弹）。
 * - 结果分三档（[AdaptReport.tier]）：全部生效（几秒自动收起）、重开后全部生效（列出功能名，“立即重开 / 稍后”）、
 *   有功能这一版用不了（列出功能名和原因，“查看详情”进 App）。点“稍后”收成右上角小胶囊“重开生效 n”，点它再弹出来。
 * - 启动提示（[loaded]）：一行，平时 3 秒、有问题 6 秒；这次运行里弹过适配结果就不再弹。
 * - 别的提示（功能被自动停用……）用 [notice]：卡片正显示时排队，收起后再显示。
 *
 * 全屏 / 横屏、小窗、分屏、画中画里先不显示，回到普通页面（[moveTo]）再显示。卡片可以向上滑走。
 * 卡片跟着当前页面走：适配途中换了页面（开屏 → 首页），由入口调用 [moveTo] 挂到新页面上，状态不丢。
 */
object AdaptCard {
    private const val TAG = "ClearStarter"
    private const val LAYER = 11
    private const val RESULT_MS = 4500L
    private const val READY_MS = 3000L
    private const val NOTICE_MS = 3000L
    private const val NOTICE_WARN_MS = 6000L

    /** 卡片上按钮要做的事（入口接上运行时、App 跳转）。 */
    interface Actions {
        fun restart(activity: Activity)
        fun open(activity: Activity, route: String)
        fun retry()
        /** 现在还有几项重开后才生效（小胶囊上的数字）。 */
        fun restartPending(): Int
    }

    @Volatile
    var actions: Actions? = null

    private enum class Mode { ADAPTING, RESULT, RESTARTING, TIMEOUT, NOTICE }

    private data class State(
        val mode: Mode,
        val title: String,
        val subtitle: String,
        val done: Int = 0,
        val total: Int = 0,
        val report: AdaptReport? = null,
        val detail: String? = null,
        val warn: Boolean = false,
        val headAction: String? = null,
        val onHeadAction: (() -> Unit)? = null,
        val autoCloseMs: Long = 0,
    )

    private val main = Handler(Looper.getMainLooper())
    private var state: State? = null
    private var queued: State? = null
    private var cardRef: WeakReference<Card>? = null
    private var card: Card?
        get() = cardRef?.get()
        set(value) { cardRef = value?.let { WeakReference(it) } }
    private var pillRef: WeakReference<Pill>? = null
    /** 用户收起了适配中的卡片（后台进行）。 */
    private var dismissed = false
    /** “重开后生效”的列表展开了。 */
    private var expanded = false
    /** 这次运行里弹过适配结果：启动提示不再重复说。 */
    private var resultShown = false
    /** 点了“稍后”：显示小胶囊。 */
    private var later: AdaptReport? = null
    private val autoClose = Runnable { close(animate = true) }

    /** 卡片正显示着或等着显示（别的提示据此排队）。 */
    val showing: Boolean get() = state != null

    // ---- 对外 ----

    fun adapting(activity: Activity?, versionName: String) {
        dismissed = false
        show(activity, State(Mode.ADAPTING, "正在适配【宿主】 $versionName", "发现新版本，正在手机上解析，几秒就好", headAction = "后台进行", onHeadAction = ::background))
    }

    /** 解析进度。卡片被收起后只更新状态，不弹出来。 */
    fun progress(done: Int, total: Int) {
        val s = state ?: return
        if (s.mode != Mode.ADAPTING) return
        state = s.copy(done = done, total = total)
        card?.render(state!!, animate = true)
    }

    /**
     * 适配结果。[card] 为假（用户关了适配卡片）时：全部正常就不弹，有问题只给一行提示。
     */
    fun result(activity: Activity?, report: AdaptReport, card: Boolean = true) {
        val wasDismissed = dismissed
        dismissed = false
        expanded = false
        resultShown = true
        if (!card) {
            if (report.tier == AdaptReport.Tier.OK) { if (state?.mode == Mode.ADAPTING) close(animate = true); return }
            val (line, warn) = AdaptCardText.loadedLine(report, firstForVersion = false)
            show(activity, State(Mode.NOTICE, AdaptCardText.resultTitle(report), line, warn = warn, report = report, headAction = "看看", onHeadAction = { showResult(report) }, autoCloseMs = NOTICE_WARN_MS))
            return
        }
        showResult(report, activity, if (wasDismissed) READY_MS else RESULT_MS)
    }

    private fun showResult(report: AdaptReport, activity: Activity? = null, okCloseMs: Long = RESULT_MS) {
        later = null
        removePill()
        show(
            activity ?: card?.activity?.get(),
            State(
                Mode.RESULT, AdaptCardText.resultTitle(report), AdaptCardText.resultSubtitle(report), report.found, report.total, report,
                headAction = if (report.tier == AdaptReport.Tier.OK) "好的" else null, onHeadAction = { close(animate = true) },
                autoCloseMs = if (report.tier == AdaptReport.Tier.OK) okCloseMs else 0,
            ),
        )
    }

    /** 等太久还没好。 */
    fun timeout(activity: Activity?, versionName: String, detail: String) {
        dismissed = false
        show(activity, State(Mode.TIMEOUT, "$versionName 还没适配完成", "现在照常能用，适配好后自动生效", detail = detail, warn = true))
    }

    /** 启动时的一行提示（已加载、生效几项）；这次运行里弹过适配结果、或卡片正忙时不显示。 */
    fun loaded(activity: Activity, title: String, detail: String, warn: Boolean, action: String, onAction: () -> Unit) {
        if (resultShown || state != null) return
        show(activity, State(Mode.NOTICE, title, detail, warn = warn, headAction = action, onHeadAction = onAction, autoCloseMs = if (warn) NOTICE_WARN_MS else NOTICE_MS))
    }

    /** 其它提示（功能被自动停用等）：卡片正显示时排队。 */
    fun notice(activity: Activity, title: String, detail: String?, action: String?, durationMs: Long = NOTICE_WARN_MS, onAction: (() -> Unit)?) {
        val s = State(Mode.NOTICE, title, detail.orEmpty(), warn = true, headAction = action, onHeadAction = onAction, autoCloseMs = durationMs)
        if (state != null) { queued = s; return }
        show(activity, s)
    }

    /** 当前页面换了（开屏 → 首页、进出详情、从全屏退出）：还在显示的卡片和小胶囊挂到新页面上。 */
    fun moveTo(activity: Activity) {
        later?.let { r -> if (state == null) attachPill(activity, r) }
        val s = state ?: return
        if (dismissed) return
        val current = card
        if (current != null && current.activity.get() === activity && current.view.isAttachedToWindow) return
        if (!suitable(activity)) return
        current?.detach()
        attach(activity, s, entering = current == null)
    }

    // ---- 内部 ----

    private fun background() {
        dismissed = true
        close(animate = true, keepState = true)
    }

    /**
     * 当前页面上有没有正在显示的开屏：
     * - 开屏广告都有“跳过 2”“跳过广告 2”这样的倒计时按钮：看得见的、以“跳过”开头的短文字；
     * - 或者类名在 splash 包里、控件 id 带 splash，并且占了大半个屏幕（品牌开屏）。
     * 小红书的控件 id 是混淆过的，所以主要靠第一条；【宿主】两条都能认出来。
     */
    private fun splashShowing(activity: Activity): Boolean {
        val root = activity.window?.decorView ?: return false
        val minArea = root.width.toLong() * root.height * 6 / 10
        fun walk(v: View, depth: Int): Boolean {
            if (depth > 18 || v.visibility != View.VISIBLE || !v.isShown) return false
            if (v is TextView) {
                val text = v.text?.toString()?.trim().orEmpty()
                if (text.startsWith("跳过") && text.length <= 8) return true
            }
            if (v.width.toLong() * v.height >= minArea) {
                val name = v.javaClass.name.lowercase()
                val idName = if (v.id != View.NO_ID) runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()?.lowercase().orEmpty() else ""
                if (".splash." in name || "splash" in name.substringAfterLast('.') || "splash" in idName) return true
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) if (walk(v.getChildAt(i), depth + 1)) return true
            return false
        }
        return runCatching { walk(root, 0) }.getOrDefault(false)
    }

    /** 全屏 / 横屏、小窗、分屏、画中画里先不弹，免得挡住正在看的内容。 */
    private fun suitable(activity: Activity): Boolean =
        !activity.isFinishing && !activity.isDestroyed &&
            activity.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE &&
            !activity.isInMultiWindowMode && !activity.isInPictureInPictureMode

    private fun show(activity: Activity?, s: State, splashWaits: Int = 0) {
        state = s
        main.removeCallbacks(autoClose)
        // 开屏（广告）还在：等它过去再弹，最多等 8 秒
        if (activity != null && card == null && splashWaits < 12 && splashShowing(activity)) {
            // 进度会更新状态（换成新对象），所以按“还是同一种卡片”判断，用最新的状态重试
            main.postDelayed({ state?.takeIf { it.mode == s.mode && card == null }?.let { show(activity, it, splashWaits + 1) } }, 700)
            return
        }
        val current = card
        if (current != null && current.view.isAttachedToWindow && (activity == null || current.activity.get() === activity)) {
            current.render(s, animate = true)
        } else {
            current?.detach()
            if (activity != null && !dismissed && suitable(activity)) attach(activity, s, entering = true)
        }
        if (s.autoCloseMs > 0) main.postDelayed(autoClose, s.autoCloseMs)
        Log.i(TAG, "adapt card: ${s.mode} ${s.title} · ${s.subtitle}")
    }

    private fun attach(activity: Activity, s: State, entering: Boolean) {
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

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(activity: Activity): Int {
        val id = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) activity.resources.getDimensionPixelSize(id) else 0
    }

    /** 收起卡片；[keepState] 为真时（后台进行）状态留着，进度和结果照常更新。 */
    private fun close(animate: Boolean, keepState: Boolean = false) {
        main.removeCallbacks(autoClose)
        val c = card
        card = null
        val activity = c?.activity?.get()
        if (!keepState) state = null
        if (c != null) {
            if (!animate) c.detach()
            else c.view.animate().alpha(0f).translationY(-c.t.dp(24).toFloat()).setDuration(200).withEndAction { c.detach() }.start()
        }
        // 排队的提示、点了“稍后”的小胶囊，在卡片收起后出来
        if (!keepState && activity != null) {
            later?.let { attachPill(activity, it) }
            queued?.let { q -> queued = null; main.postDelayed({ show(activity, q) }, 260) }
        }
    }

    private fun restartNow(s: State) {
        val activity = card?.activity?.get() ?: return
        later = null
        removePill()
        show(activity, State(Mode.RESTARTING, "正在重开【宿主】", "几秒后自动打开，不用手动点；中间会短暂回到桌面", report = s.report))
        actions?.restart(activity)
    }

    private fun restartLater(r: AdaptReport) {
        later = r
        close(animate = true)
    }

    /** 重开失败（没有根权限、系统也没让自动打开）：说清楚，不假装成功。 */
    fun restartFailed(activity: Activity) {
        show(activity, State(Mode.NOTICE, "没能自动重开", "请从最近任务里关掉【宿主】，再重新打开", warn = true, autoCloseMs = 8000))
    }

    // ---- 小胶囊 ----

    private fun attachPill(activity: Activity, r: AdaptReport) {
        val n = actions?.restartPending() ?: r.restart.size
        if (n <= 0) { later = null; removePill(); return }
        val current = pillRef?.get()
        if (current != null && current.activity.get() === activity && current.view.isAttachedToWindow) return
        current?.detach()
        if (!suitable(activity)) return
        val overlay = HostWindows.overlayFor(activity) ?: return
        val p = Pill(activity, n) { showResult(r, activity) }
        // 贴右边、默认在屏幕上部偏下（只压着能滚动的内容，不挡顶部的搜索和消息按钮）；可以上下拖，位置记在【宿主】自己的偏好里
        val screen = activity.resources.displayMetrics.heightPixels
        val saved = runCatching { activity.getSharedPreferences(PILL_PREFS, Context.MODE_PRIVATE).getFloat(PILL_Y, 0.38f) }.getOrDefault(0.38f)
        overlay.decor.addView(p.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, (screen * saved.coerceIn(0.12f, 0.85f)).toInt(), 0, 0)
        })
        p.open(overlay.window)
        pillRef = WeakReference(p)
    }

    private fun removePill() {
        pillRef?.get()?.detach()
        pillRef = null
    }

    private const val PILL_PREFS = "module_ui"
    private const val PILL_Y = "restartPillY"

    /** 贴在右边的小胶囊：左侧圆角、右侧贴边；点一下弹出结果卡片，上下拖动换位置（松手时记住）。 */
    private class Pill(activity: Activity, count: Int, onClick: () -> Unit) {
        val activity = WeakReference(activity)
        val t = HostTheme.of(activity)
        private val ui = Ui(t)
        val view: TextView = ui.text("重开生效 $count", TextSize.LABEL, t.onAccent, t.bold).apply {
            gravity = Gravity.CENTER
            setPadding(t.dp(14), 0, t.dp(12), 0)
            minHeight = t.dp(40)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(t.accent)
                val r = t.dp(20).toFloat()
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
            elevation = t.dp(6).toFloat()
            isClickable = true
            contentDescription = "还有 $count 项重开【宿主】后生效，点这里查看并重开"
            setOnClickListener { onClick() }
        }
        private val touch = OverlayTouchTarget(view, LAYER)

        @SuppressLint("ClickableViewAccessibility")
        fun open(window: android.view.Window?) {
            val slop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop
            var downY = 0f
            var startTop = 0
            var dragging = false
            view.setOnTouchListener { v, e ->
                val lp = v.layoutParams as? FrameLayout.LayoutParams ?: return@setOnTouchListener false
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downY = e.rawY; startTop = lp.topMargin; dragging = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = e.rawY - downY
                        if (!dragging && kotlin.math.abs(dy) > slop) dragging = true
                        if (dragging) {
                            val parentH = (v.parent as? View)?.height ?: v.rootView.height
                            lp.topMargin = (startTop + dy).toInt().coerceIn((parentH * 0.12f).toInt(), (parentH * 0.85f).toInt())
                            v.layoutParams = lp
                        }
                    }
                    MotionEvent.ACTION_UP -> if (dragging) {
                        val parentH = ((v.parent as? View)?.height ?: v.rootView.height).coerceAtLeast(1)
                        runCatching { v.context.getSharedPreferences(PILL_PREFS, Context.MODE_PRIVATE).edit().putFloat(PILL_Y, lp.topMargin.toFloat() / parentH).apply() }
                    } else v.performClick()
                }
                true
            }
            window?.let(touch::attach)
        }

        fun detach() {
            touch.detach()
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    // ---- 卡片 ----

    /** 一张卡片的视图：每次 [render] 按状态重新排内容，进度条复用（动画连续）。 */
    private class Card(activity: Activity) {
        val activity = WeakReference(activity)
        val t = HostTheme.of(activity)
        private val ui = Ui(t)
        private val bar = ProgressBar(activity, t)
        private val touch: OverlayTouchTarget
        val view: LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rounded(t.surface, 20f, t.divider, 0.5f)
            elevation = t.dp(10).toFloat()
        }

        init {
            touch = OverlayTouchTarget(view, LAYER)
        }

        fun render(s: State, animate: Boolean) {
            val compact = s.mode == Mode.NOTICE
            view.removeAllViews()
            (bar.parent as? ViewGroup)?.removeView(bar)
            view.setPadding(t.dp(if (compact) 12 else 16), t.dp(if (compact) 10 else 14), t.dp(if (compact) 8 else 14), t.dp(if (compact) 10 else 14))
            view.addView(head(s, compact))
            val r = s.report
            when (s.mode) {
                Mode.ADAPTING -> {
                    addBar(AdaptCardText.fraction(s.done, s.total), indeterminate = s.total <= 0, color = t.accent, animate = animate)
                    view.addView(counterRow(AdaptCardText.counter(s.done, s.total), if (s.total > 0) "${(AdaptCardText.fraction(s.done, s.total) * 100).toInt()}%" else ""), ui.lp(top = 6))
                    view.addView(infoBox("期间可以照常使用。适配好后，大部分功能当场生效。"), ui.lp(top = 10))
                }
                Mode.RESULT -> if (r != null) renderResult(r, animate)
                Mode.RESTARTING -> {
                    addBar(0f, indeterminate = true, color = t.accent, animate = false)
                    view.addView(ui.text("有根权限：强行停止后重新打开。没有根权限：先定好自动打开，再关掉【宿主】。", TextSize.CAPTION, t.textSecondary), ui.lp(top = 10))
                }
                Mode.TIMEOUT -> {
                    s.detail?.let { view.addView(infoBox(it), ui.lp(top = 12)) }
                    view.addView(buttons("重试", false, { AdaptCard.close(animate = true); actions?.retry() }, "打开【模块】", true) { open("adapt") }, ui.lp(top = 12))
                    view.addView(link("以后在后台不受限制（去放行）") { open("module") }, ui.lp(top = 2))
                }
                Mode.NOTICE -> Unit
            }
            view.contentDescription = listOfNotNull(s.title, s.subtitle, s.detail).filter { it.isNotEmpty() }.joinToString("，")
        }

        private fun renderResult(r: AdaptReport, animate: Boolean) {
            // 进度条只在有功能用不了时变橙；要重开不算适配有问题
            val color = if (r.unavailable.isEmpty()) t.ok else t.warn
            addBar(AdaptCardText.fraction(r.found, r.total).coerceAtLeast(if (r.found >= r.total) 1f else 0f), indeterminate = false, color = color, animate = animate)
            view.addView(counterRow(AdaptCardText.foundLine(r), if (r.found >= r.total) "完成" else ""), ui.lp(top = 6))
            if (r.refreshNeeded) view.addView(ui.text("已经显示出来的内容，下拉刷新后生效", TextSize.CAPTION, t.textSecondary), ui.lp(top = 2))
            if (r.restart.isNotEmpty()) {
                val all = expanded || r.restart.size <= 5
                val box = ui.vertical(4,
                    ui.text("重开后生效 ${r.restart.size} 项", TextSize.LABEL, t.onWarnSoft, t.bold),
                    ui.text(if (all) r.restart.joinToString(" · ") else AdaptCardText.restartList(r), TextSize.LABEL, t.onWarnSoft),
                ).apply {
                    background = ui.rounded(t.warnSoft, 12f)
                    setPadding(t.dp(12), t.dp(10), t.dp(12), t.dp(10))
                    if (!all) addView(link("全部 ${r.restart.size} 项") { expanded = true; render(state ?: return@link, animate = false) })
                }
                view.addView(box, ui.lp(top = 12))
            }
            if (r.unavailable.isNotEmpty()) {
                val list = LinearLayout(view.context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ui.rounded(t.surface, 12f, t.divider, 1f)
                }
                r.unavailable.take(3).forEachIndexed { i, issue ->
                    list.addView(ui.vertical(2, ui.text(issue.title, TextSize.BODY, t.text, t.bold), ui.text(issue.reason, TextSize.LABEL, t.textSecondary)).apply {
                        setPadding(t.dp(12), t.dp(9), t.dp(12), t.dp(9))
                        if (i > 0) background = ui.rounded(t.surface, 0f)
                    })
                }
                if (r.unavailable.size > 3) list.addView(link("全部 ${r.unavailable.size} 项（在【模块】里看）") { open("adapt") })
                view.addView(list, ui.lp(top = 12))
                if (r.restart.isEmpty()) view.addView(ui.text("下个版本的【模块】会补上；也可以在【模块】里导出诊断报告反馈。", TextSize.CAPTION, t.textSecondary), ui.lp(top = 8))
            }
            if (r.restart.isNotEmpty()) {
                val playing = isPlaying()
                // 正在播放时“稍后”是主按钮（不打断）；否则“立即重开”是主按钮
                view.addView(buttons("稍后", playing, { restartLater(r) }, "立即重开", !playing) { state?.let { restartNow(it) } }, ui.lp(top = 12))
                view.addView(ui.text(AdaptCardText.restartWarning(playing), TextSize.CAPTION, if (playing) t.warn else t.textSecondary).apply { gravity = Gravity.CENTER }, ui.lp(top = 6))
            } else if (r.unavailable.isNotEmpty()) {
                view.addView(buttons("知道了", false, { AdaptCard.close(animate = true) }, "查看详情", true) { open("adapt") }, ui.lp(top = 12))
            }
        }

        private fun isPlaying(): Boolean = runCatching {
            (view.context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).isMusicActive
        }.getOrDefault(false)

        private fun open(route: String) {
            val a = activity.get() ?: return
            AdaptCard.close(animate = true)
            actions?.open(a, route)
        }

        private fun head(s: State, compact: Boolean): View {
            val badge = Badge(view.context, t, when {
                s.mode == Mode.TIMEOUT || (s.mode == Mode.NOTICE && s.warn) -> Badge.Kind.WARN
                s.mode == Mode.RESULT && s.report?.tier == AdaptReport.Tier.OK -> Badge.Kind.OK
                else -> Badge.Kind.STAR
            })
            val title = ui.text(s.title, if (compact) TextSize.BODY else TextSize.ITEM, t.text, t.bold, maxLines = 2)
            val sub = ui.text(s.subtitle, if (compact) TextSize.CAPTION else TextSize.LABEL, if (s.warn && compact) t.warn else t.textSecondary, maxLines = 3)
            return LinearLayout(view.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(badge, LinearLayout.LayoutParams(t.dp(30), t.dp(30)))
                addView(ui.vertical(2, title, sub), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = t.dp(10) })
                s.headAction?.let { label ->
                    addView(ui.text(label, TextSize.LABEL, if (s.warn) t.onWarnSoft else t.onAccentSoft, t.medium, maxLines = 1).apply {
                        gravity = Gravity.CENTER
                        setPadding(t.dp(12), 0, t.dp(12), 0)
                        ui.compactTarget(this, if (s.warn) t.warnSoft else t.accentSoft, 30)
                        isClickable = true
                        setOnClickListener { s.onHeadAction?.invoke() }
                    })
                }
            }
        }

        private fun addBar(fraction: Float, indeterminate: Boolean, color: Int, animate: Boolean) {
            bar.set(fraction, indeterminate, color, animate)
            view.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, t.dp(6)).apply { topMargin = t.dp(12) })
        }

        private fun counterRow(left: String, right: String) = LinearLayout(view.context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(ui.text(left, TextSize.CAPTION, t.textSecondary, maxLines = 1), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.text(right, TextSize.CAPTION, t.textSecondary, t.medium, maxLines = 1))
        }

        private fun infoBox(text: String) = ui.text(text, TextSize.LABEL, t.text).apply {
            background = ui.rounded(t.surfaceMuted, 10f)
            setPadding(t.dp(10), t.dp(8), t.dp(10), t.dp(8))
        }

        private fun link(label: String, onClick: () -> Unit) = ui.text(label, TextSize.LABEL, t.accentText, t.medium).apply {
            minHeight = t.dp(40)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { onClick() }
        }

        /** 两个按钮一行：[primaryLeft] / [primaryRight] 决定谁是实心主按钮。 */
        private fun buttons(left: String, primaryLeft: Boolean, onLeft: () -> Unit, right: String, primaryRight: Boolean, onRight: () -> Unit) =
            LinearLayout(view.context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(ui.button(left, primaryLeft, onLeft), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.button(right, primaryRight, onRight), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, if (primaryRight) 2f else 1f).apply { marginStart = t.dp(10) })
            }

        @SuppressLint("ClickableViewAccessibility")
        fun open(window: android.view.Window?) {
            // 向上滑走（等于“后台进行”或“稍后”）；没被按钮接住的触摸在这里吃掉，不漏给下面的页面
            var downY = 0f
            view.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> downY = e.rawY
                    MotionEvent.ACTION_MOVE -> v.translationY = (e.rawY - downY).coerceAtMost(0f)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (v.translationY < -t.dp(36)) swipedAway() else v.animate().translationY(0f).setDuration(160).start()
                    }
                }
                true
            }
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    touch.detach()
                    bar.stop()
                    if (card === this@Card) card = null
                }
            })
            window?.let(touch::attach)
        }

        private fun swipedAway() {
            val s = state
            when {
                s == null -> AdaptCard.close(animate = true)
                s.mode == Mode.ADAPTING -> background()
                s.mode == Mode.RESULT && s.report != null && s.report.restart.isNotEmpty() -> restartLater(s.report)
                s.mode == Mode.RESTARTING -> view.animate().translationY(0f).setDuration(160).start()
                else -> AdaptCard.close(animate = true)
            }
        }

        fun detach() {
            touch.detach()
            bar.stop()
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    /** 左上角的徽标：家族的四角星（平时）、对勾（全部正常）、感叹号（要注意）。 */
    private class Badge(context: Context, private val t: HostTheme, private val kind: Kind) : View(context) {
        enum class Kind { STAR, OK, WARN }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val path = Path()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.style = Paint.Style.FILL
            paint.color = when (kind) { Kind.WARN -> t.warn; else -> t.accent }
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, w * 0.3f, w * 0.3f, paint)
            paint.color = t.onAccent
            val cx = w / 2
            val cy = h / 2
            when (kind) {
                Kind.STAR -> {
                    val o = w * 0.3f
                    val i = w * 0.08f
                    path.reset()
                    path.moveTo(cx, cy - o); path.lineTo(cx + i, cy - i); path.lineTo(cx + o, cy); path.lineTo(cx + i, cy + i)
                    path.lineTo(cx, cy + o); path.lineTo(cx - i, cy + i); path.lineTo(cx - o, cy); path.lineTo(cx - i, cy - i); path.close()
                    canvas.drawPath(path, paint)
                }
                Kind.OK -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = w * 0.09f
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    path.reset()
                    path.moveTo(w * 0.28f, h * 0.52f); path.lineTo(w * 0.44f, h * 0.67f); path.lineTo(w * 0.73f, h * 0.36f)
                    canvas.drawPath(path, paint)
                }
                Kind.WARN -> {
                    paint.color = t.surface
                    rect.set(cx - w * 0.045f, h * 0.24f, cx + w * 0.045f, h * 0.6f)
                    canvas.drawRoundRect(rect, w * 0.045f, w * 0.045f, paint)
                    canvas.drawCircle(cx, h * 0.74f, w * 0.055f, paint)
                }
            }
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

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (indeterminate) startSweep()
        }

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
