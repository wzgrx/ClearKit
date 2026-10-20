package io.github.clearkit.starter.hook.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.ChoiceSetting
import io.github.clearkit.starter.model.settings.FloatSetting
import io.github.clearkit.starter.model.settings.IntSetting
import io.github.clearkit.starter.model.settings.FeatureCatalog
import io.github.clearkit.starter.model.settings.Setting
import io.github.clearkit.starter.model.settings.SettingChoices
import io.github.clearkit.starter.model.settings.SettingPages
import io.github.clearkit.starter.model.settings.SettingWrite
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.model.settings.StringSetSetting
import io.github.clearkit.starter.model.settings.StringSetting
import io.github.clearkit.starter.model.settings.TextFormat

/** 【宿主】里的设置窗要用的读写（运行时实现：经桥接交给 App 校验写入，见 hook/features 的 HostSettingsEntryFeature）。 */
interface SettingsStore {
    /** 本进程现在的值（含默认值）。 */
    fun value(setting: Setting<*>): Any

    /** 写一项；[value] 为 null 时恢复默认。[done] 在主线程回调：null = 成功，否则是原因。 */
    fun write(setting: Setting<*>, value: Any?, done: (String?) -> Unit)

    /** 这次运行里改过、要重开【宿主】才生效的键。 */
    val pendingRestart: Set<String>

    fun restart(activity: Activity)

    /** 打开【模块】 App 的某一页（null = 首页）。 */
    fun openApp(activity: Activity, route: String?)

    /**
     * 从目录里选的设置（[SettingChoices.CATALOG_SETS]、[SettingChoices.CATALOG_SINGLE]）现在能选的项（值 → 名称）；
     * null = 还没有目录（对应页面还没打开过）。
     */
    fun choices(setting: Setting<*>): List<Pair<String, String>>? = null

    /** 过滤页的样例结论（“最近 60 条里会藏 9 条：抽奖 4…”）；没有样例返回 null。 */
    fun sampleNote(route: String): String? = null
}

/**
 * 【宿主】里的设置窗：从【宿主】里打开的底部弹层，不用跳到 App。
 * 和 App 同一套分类：顶部按 [FeatureCatalog.pages] 一页一个大类，加一个“其他”（模块设置等）。
 * 每类先画示意图（PagePreviews），再列示意图里改不完的设置；开关、单选、数值、多选、列表、文字都能直接改，
 * 改动经桥接交给 App 校验写入后立刻在本进程生效。要重开才生效的改动在底部深色条里提示并可一键重开。
 * [scene] 指定先打开哪一类（路由），不传时回到上次看的那一类。
 */
class SettingsSheet(private val activity: Activity, private val store: SettingsStore, private val scene: String? = null) {
    private val t = HostTheme.of(activity)
    private val ui = Ui(t)
    private val dialog = Dialog(activity)
    private val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, t.dp(16)) }
    private val scroll = ScrollView(activity).apply { isVerticalScrollBarEnabled = true; addView(body) }
    private val restartText = ui.text("", TextSize.LABEL, t.onInverse)
    private val restartBar = LinearLayout(activity)
    private val chipsRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val chipsScroll = ui.hScroll(chipsRow)
    private val tabsBox = FrameLayout(activity)
    private val searchBox = EditText(activity)
    private var searching = false
    private var query = ""

    /** 能在这里改的设置：去掉内部状态、App 自己的外观和作用域。 */
    private val visible: List<Setting<*>> = Settings.all.filter { s ->
        s.key !in SettingPages.INTERNAL && s.key.substringBefore('.') !in APP_ONLY
    }

    /** 一块：App 里的一页设置。 */
    private data class Area(val title: String, val route: String)

    /** 一个大类：顶部的一个标签。 */
    private data class Scene(val id: String, val title: String, val areas: List<Area>)

    /** 大类：功能目录里一页一类，加一个“其他”放不属于任何一页的设置（模块设置等）。 */
    private val scenes: List<Scene> by lazy {
        val covered = FeatureCatalog.pages.map { it.route }.toSet()
        val rest = visible.filter { SettingPages.routeOf(it.key) !in covered }
            .groupBy { SettingPages.routeOf(it.key) ?: it.key.substringBefore('.') }
            .map { (route, list) -> Area(SettingPages.pageTitle(list.first().key), route) }
        FeatureCatalog.pages.map { Scene(it.route, it.title, listOf(Area(it.title, it.route))) } +
            listOfNotNull(Scene("other", "其他", rest).takeIf { rest.isNotEmpty() })
    }

    private fun settingsIn(area: Area): List<Setting<*>> =
        visible.filter { s -> SettingPages.routeOf(s.key) == area.route || (SettingPages.routeOf(s.key) == null && s.key.substringBefore('.') == area.route) }

    fun show() {
        val sheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(t.ground)
                val r = t.dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            clipToOutline = true
            elevation = t.dp(16).toFloat()
            isClickable = true
        }
        val handle = View(activity).apply { background = ui.rounded(t.handle, 2f) }
        sheet.addView(handle, LinearLayout.LayoutParams(t.dp(36), t.dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = t.dp(10); bottomMargin = t.dp(4) })

        val titles = ui.vertical(1, ui.text("【模块】", TextSize.SECTION, t.text, t.bold), ui.text("在这里改，不用跳出去", TextSize.CAPTION, t.textSecondary))
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(t.dp(20), t.dp(2), t.dp(8), t.dp(8))
            addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton(IconView.Kind.SEARCH, "搜索设置") { toggleSearch() })
            addView(iconButton(IconView.Kind.OPEN, "在【模块】中打开") { store.openApp(activity, currentRoute()); dialog.dismiss() })
            addView(iconButton(IconView.Kind.CLOSE, "关闭") { dialog.dismiss() })
        }
        sheet.addView(header)

        searchBox.apply {
            hint = "搜索设置，支持拼音首字母"
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TextSize.ITEM)
            setTextColor(t.text)
            setHintTextColor(t.textSecondary)
            background = ui.rounded(t.surface, 24f, t.accent, 2f)
            setPadding(t.dp(16), t.dp(10), t.dp(16), t.dp(10))
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); render() }
            })
        }
        sheet.addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, t.dp(48)).apply { setMargins(t.dp(16), 0, t.dp(16), t.dp(8)) })

        chipsRow.setPadding(t.dp(16), 0, t.dp(16), 0)
        sheet.addView(chipsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = t.dp(4) })
        sheet.addView(tabsBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sheet.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        restartBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(t.dp(16), t.dp(6), t.dp(6), t.dp(6))
            background = ui.rounded(t.inverse, 18f)
            elevation = t.dp(6).toFloat()
            addView(restartText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.text("立即重开", TextSize.LABEL, t.onInverseAccent, t.bold).apply {
                gravity = Gravity.CENTER
                setPadding(t.dp(16), 0, t.dp(16), 0)
                background = ui.pressable(ui.rounded(t.inverseAccent, 19f), 19f)
                isClickable = true
                setOnClickListener { dialog.dismiss(); store.restart(activity) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, t.dp(38)))
        }
        sheet.addView(restartBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(t.dp(12), t.dp(4), t.dp(12), t.dp(12))
        })

        val metrics = activity.resources.displayMetrics
        val width = minOf(metrics.widthPixels, t.dp(600))
        val height = (metrics.heightPixels * 0.86f).toInt()
        val root = FrameLayout(activity).apply {
            setBackgroundColor(t.scrim)
            setOnClickListener { dialog.dismiss() }
            addView(sheet, FrameLayout.LayoutParams(width, height, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            // 弹层高度跟着可用高度走：输入法弹出时窗口变矮，固定高度会把标题和搜索框顶出屏幕；搜索时拉到接近全屏，多显示结果
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> fitSheet(v.height, height) }
        }
        sheetView = sheet
        sheet.setPadding(0, 0, 0, SystemInsets.bottom(activity.window.decorView))
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { if (searching) toggleSearch() else dialog.dismiss(); true } else code == KeyEvent.KEYCODE_BACK
        }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setDimAmount(0f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }
        val first = scene ?: lastScene
        current = scenes.indexOfFirst { it.id == first }.coerceAtLeast(0)
        render()
        dialog.show()
    }

    private var current = 0
    private var sheetView: View? = null

    /** 弹层高度：平时是屏幕的 86%（[normal]），但不超过可用高度减去顶部留白；搜索时用满可用高度。 */
    private fun fitSheet(available: Int, normal: Int) {
        val sheet = sheetView ?: return
        if (available <= 0) return
        val gap = t.dp(if (searching) 8 else 24)
        val want = if (searching) available - gap else minOf(normal, available - gap)
        val lp = sheet.layoutParams ?: return
        if (lp.height != want) { lp.height = want; sheet.post { sheet.requestLayout() } }
    }

    private fun toggleSearch() {
        searching = !searching
        (sheetView?.parent as? View)?.let { fitSheet(it.height, (activity.resources.displayMetrics.heightPixels * 0.86f).toInt()) }
        searchBox.visibility = if (searching) View.VISIBLE else View.GONE
        if (searching) {
            searchBox.requestFocus()
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.showSoftInput(searchBox, 0)
        } else {
            searchBox.setText("")
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.hideSoftInputFromWindow(searchBox.windowToken, 0)
        }
        render()
    }

    private fun currentRoute(): String? = scenes.getOrNull(current)?.takeIf { it.id != "other" }?.areas?.firstOrNull()?.route

    private fun render() {
        body.removeAllViews()
        chipsScroll.visibility = if (searching) View.GONE else View.VISIBLE
        tabsBox.visibility = if (searching) View.GONE else View.VISIBLE
        if (searching) renderSearch() else renderScene()
        scroll.scrollTo(0, 0)
        updateRestartBar()
    }

    private fun renderScene() {
        lastScene = scenes[current].id
        chipsRow.removeAllViews()
        scenes.forEachIndexed { i, sc ->
            val on = i == current
            chipsRow.addView(ui.text(sc.title, TextSize.LABEL, if (on) t.ground else t.text, if (on) t.medium else null).apply {
                gravity = Gravity.CENTER
                setPadding(t.dp(14), 0, t.dp(14), 0)
                ui.compactTarget(this, if (on) t.text else t.surface, 36, if (on) null else t.chipBorder)
                isSelected = on
                setOnClickListener { if (current != i) { current = i; render() } }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (i > 0) marginStart = t.dp(8) })
        }
        tabsBox.removeAllViews()
        val areas = scenes[current].areas
        areas.forEach { area ->
            val preview = io.github.clearkit.starter.model.preview.PagePreviews.byRoute(area.route)
            // 有示意图的页：示意图里能点的多选不再重复列一遍
            // 示意图里就能改完的（开关、画全了的单选和多选）列表里不再重复，点“列出来”再显示（6.2 高密度）
            val covered = if (listAll) emptySet() else preview?.let { io.github.clearkit.starter.model.preview.PagePreviews.operable(it) }.orEmpty()
            val list = settingsIn(area).filterNot { it.key in covered }
            if (list.isEmpty() && preview == null) return@forEach
            body.addView(areaHeader(area))
            val sample = store.sampleNote(area.route)?.let { note -> ui.text(note, TextSize.LABEL, t.text).apply { setPadding(t.dp(16), t.dp(10), t.dp(16), t.dp(6)) } }
            val folded = if (preview == null) 0 else settingsIn(area).count { it.key in covered }
            val more = if (preview == null || (folded == 0 && !listAll)) null else ui.text(
                if (listAll) "收起示意图里已有的项" else "示意图里能点的 $folded 项没列出 · 列出来", TextSize.CAPTION, t.accentText,
            ).apply { setPadding(t.dp(16), t.dp(8), t.dp(16), t.dp(8)); isClickable = true; setOnClickListener { listAll = !listAll; render() } }
            card(body, listOfNotNull(preview?.let { previewView(it) }, sample) + list.map { control(it, null) } + listOfNotNull(more))
        }
        body.addView(note("这里的改动和【模块】里是同一份设置；标着“重开生效”的项改完要重开【宿主】。"))
    }

    /** 示意图里已有的项也在列表里列出来（默认不列，6.2 高密度）。 */
    private var listAll = false

    private fun writeAll(changes: Map<Setting<*>, Any>) {
        if (changes.isEmpty()) return
        var left = changes.size
        changes.forEach { (st, value) -> save(st, value) { if (--left == 0) render() } }
    }

    /**
     * 实物预览（6.0）：【宿主】页面的示意图，点块就改对应设置（划掉 = 隐藏，带标签 = 改了样子，“+” = 新加的）。
     * 和 App 用同一份结构（[io.github.clearkit.starter.model.preview.PagePreviews]）。
     */
    private fun previewView(page: io.github.clearkit.starter.model.preview.PagePreviews.Page): View {
        val pp = io.github.clearkit.starter.model.preview.PagePreviews
        val values = page.settings.associate { it.key to store.value(it) }
        fun tap(b: io.github.clearkit.starter.model.preview.PagePreviews.Block) {
            val now = page.settings.associate { it.key to store.value(it) }
            writeAll(pp.step(b, now).toMap())
        }
        fun build(b: io.github.clearkit.starter.model.preview.PagePreviews.Block): View {
            val on = b.bind?.let { pp.active(it, values) } ?: false
            return when (b.style) {
                io.github.clearkit.starter.model.preview.PagePreviews.Style.ROW -> ui.flow(4, b.children.map { build(it) })
                io.github.clearkit.starter.model.preview.PagePreviews.Style.MEDIA -> ui.vertical(6, ui.text(b.label, TextSize.CAPTION, 0xCCFFFFFF.toInt()), ui.flow(4, b.children.map { build(it) })).apply {
                    background = ui.rounded(0xFF1E2523.toInt(), 10f)
                    setPadding(t.dp(8), t.dp(8), t.dp(8), t.dp(8))
                }
                io.github.clearkit.starter.model.preview.PagePreviews.Style.GROUP, io.github.clearkit.starter.model.preview.PagePreviews.Style.TABS -> {
                    val off = on && b.effect == io.github.clearkit.starter.model.preview.PagePreviews.Effect.HIDE
                    val head = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(ui.text(b.label, TextSize.CAPTION, if (off) t.textDisabled else t.textSecondary, t.medium).apply {
                            if (off) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        b.bind?.let { _ -> addView(ui.text(if (off) "恢复" else "整块隐藏", TextSize.CAPTION, t.accentText).apply {
                            setPadding(t.dp(6), t.dp(4), t.dp(6), t.dp(4)); isClickable = true; setOnClickListener { tap(b) }
                        }) }
                    }
                    val box = if (off) ui.vertical(0, head) else ui.vertical(4, head, ui.flow(4, b.children.map { build(it) }))
                    box.apply { background = ui.rounded(if (off) t.seg else t.surface, 10f, t.chipBorder); setPadding(t.dp(8), t.dp(6), t.dp(8), t.dp(8)) }
                }
                else -> {
                    val overlay = b.style == io.github.clearkit.starter.model.preview.PagePreviews.Style.OVERLAY
                    val struck = on && b.effect == io.github.clearkit.starter.model.preview.PagePreviews.Effect.HIDE
                    val added = b.effect == io.github.clearkit.starter.model.preview.PagePreviews.Effect.ADD
                    val valued = b.effect == io.github.clearkit.starter.model.preview.PagePreviews.Effect.VALUE && pp.changed(b, values)
                    val label = buildString {
                        if (added && !on) append("+ ")
                        append(pp.label(b, values))
                        if (struck) append(" ✕")
                        if (on && b.effect == io.github.clearkit.starter.model.preview.PagePreviews.Effect.MARK && b.mark != null) append(" · ").append(b.mark)
                    }
                    // 开关没开时淡淡写出点了会怎样
                    val hint = if (!on && pp.hintWhenOff(b) && b.mark != null) " · ${b.mark}" else null
                    val context = b.bind == null
                    val fg = when { context -> if (overlay) 0x99FFFFFF.toInt() else t.textSecondary; overlay -> if (struck) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt(); struck || (added && !on) -> t.textDisabled; added || valued || (on && b.effect == io.github.clearkit.starter.model.preview.PagePreviews.Effect.MARK) -> t.accentText; else -> t.text }
                    val bg = when { context -> if (overlay) 0x1AFFFFFF else t.seg; overlay -> if (struck) 0x33FFFFFF else 0x40FFFFFF; struck -> t.seg; added && on -> t.accentSoft; valued -> t.accentSoft; else -> t.ground }
                    ui.text(label, TextSize.CAPTION, fg).apply {
                        if (hint != null) text = android.text.SpannableStringBuilder(label).append(hint).apply {
                            setSpan(android.text.style.ForegroundColorSpan(if (overlay) 0x80FFFFFF.toInt() else t.textDisabled), label.length, length, 0)
                        }
                        gravity = Gravity.CENTER
                        setPadding(t.dp(7), t.dp(4), t.dp(7), t.dp(4))
                        minHeight = t.dp(28)
                        if (struck) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                        background = ui.rounded(bg, 7f, if (overlay || context) null else t.chipBorder)
                        b.bind?.takeIf { pp.tappable(b) }?.let { isClickable = true; contentDescription = pp.label(b, values) + if (on) "，已改" else ""; setOnClickListener { tap(b) } }
                    }
                }
            }
        }
        val (n, _) = pp.count(page, values)
        val inner = ui.vertical(4, *page.blocks.map { build(it) }.toTypedArray()).apply {
            background = ui.rounded(t.ground, 12f, t.chipBorder)
            setPadding(t.dp(6), t.dp(6), t.dp(6), t.dp(6))
        }
        val hint = page.note ?: "点块改，再点恢复"
        return block("${page.title} · 示意图", if (n == 0) hint else "改了 $n 处 · $hint", inner)
    }

    private fun renderSearch() {
        val q = query.trim()
        if (q.isEmpty()) { body.addView(note("输入名称或说明里的词，比如“广告”“提示”")); return }
        val hits = visible.filter { s -> Pinyin.matches(s.title, q) || listOf(s.summary.orEmpty(), SettingPages.pageTitle(s.key)).any { it.contains(q, ignoreCase = true) } }
        if (hits.isEmpty()) { body.addView(sectionLabel("没找到“$q”")); body.addView(note("换个说法试试，或用拼音首字母")); return }
        val order = scenes.map { it.title }
        hits.take(MAX_RESULTS).groupBy { s -> scenes.firstOrNull { sc -> sc.areas.any { a -> s in settingsIn(a) } }?.title ?: "其他" }
            .entries.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) order.size else i } }
            .forEach { (title, list) ->
                body.addView(sectionLabel("$title · ${list.size} 项"))
                card(body, list.map { s -> control(s, SettingPages.pageTitle(s.key)) })
            }
        if (hits.size > MAX_RESULTS) body.addView(note("只显示前 $MAX_RESULTS 项，换个更具体的词"))
    }

    private fun areaHeader(area: Area): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(t.dp(24), t.dp(10), t.dp(12), t.dp(2))
        addView(ui.text(area.title, TextSize.BODY, t.text, t.bold), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ui.text("在【模块】里打开 ›", TextSize.CAPTION, t.accentText).apply {
            gravity = Gravity.CENTER
            minHeight = t.dp(40)
            setPadding(t.dp(8), 0, t.dp(8), 0)
            isClickable = true
            setOnClickListener { store.openApp(activity, area.route); dialog.dismiss() }
        })
    }

    private fun iconButton(kind: IconView.Kind, label: String, onClick: () -> Unit): View = IconView(activity, t, kind).apply {
        contentDescription = label
        background = ui.pressable(ui.rounded(Color.TRANSPARENT, 24f), 24f)
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(t.dp(48), t.dp(48))
    }

    // ---- 设置项 ----

    private fun isOn(s: Setting<*>): Boolean = when (val v = runCatching { store.value(s) }.getOrNull()) {
        is Boolean -> v
        is Set<*> -> v.isNotEmpty()
        else -> v != null && v != s.default
    }

    private fun summaryOf(s: Setting<*>, where: String?): String? {
        val parts = listOfNotNull(where, s.summary, if (s.needsRestart) "需重开生效" else null)
        return parts.joinToString(" · ").ifEmpty { null }
    }

    private fun save(s: Setting<*>, value: Any?, after: (Boolean) -> Unit = {}) {
        store.write(s, value) { error ->
            if (error != null) HostToast.show(activity, "没改成：${s.title}", error)
            updateRestartBar()
            after(error == null)
        }
    }

    private fun control(s: Setting<*>, where: String?): View {
        if (s.comingSoon) return block(s.title, listOfNotNull(where, "即将支持：设置已保留，功能还没做好").joinToString(" · "), null).apply { alpha = 0.55f }
        return when (s) {
            is BoolSetting -> {
                lateinit var row: ToggleRow
                row = ui.toggleRow(s.title, summaryOf(s, where), store.value(s) as Boolean) { on -> save(s, on) { ok -> if (!ok) row.checked = !on } }
                row.view.apply { setPadding(t.dp(16), t.dp(6), t.dp(16), t.dp(6)) }
            }
            is ChoiceSetting -> {
                val current = s.options.indexOfFirst { it.id == store.value(s) }.coerceAtLeast(0)
                // 选项少而短时直接摆出来；多了就一行显示当前值，点开选（省地方）
                if (s.options.size <= 3 && s.options.sumOf { it.label.length } <= 14) {
                    block(s.title, summaryOf(s, where), ui.choiceChips(s.options.map { it.label }, current) { i -> save(s, s.options[i].id) })
                } else choiceRow(s, where, current)
            }
            is IntSetting -> intControl(s, where)
            is FloatSetting -> floatControl(s, where)
            is StringSetSetting -> when {
                s.choices.isNotEmpty() -> choiceSetControl(s, where)
                s.key in SettingChoices.CATALOG_SETS -> catalogSetControl(s, where)
                else -> listControl(s, where)
            }
            is StringSetting -> when {
                s.key in SettingChoices.ORDERS -> orderControl(s, where, SettingChoices.ORDERS.getValue(s.key))
                s.key in SettingChoices.CATALOG_SINGLE -> catalogChoiceControl(s, where)
                else -> textControl(s, where)
            }
        }
    }

    private fun intControl(s: IntSetting, where: String?): View {
        var value = store.value(s) as Int
        val label = ui.text("", TextSize.ITEM, t.text, t.medium).apply { gravity = Gravity.CENTER; minWidth = t.dp(72) }
        fun show() { label.text = if (value == 0 && s.range.first <= 0 && s.presets.contains(0).not() && s.title.contains("倍速")) "跟随" else if (s.unit.isEmpty()) "$value" else "$value ${s.unit}" }
        show()
        val span = s.range.last.toLong() - s.range.first.toLong()
        val step = when { span <= 20 -> 1; span <= 200 -> 5; span <= 2000 -> 50; else -> 100 }
        fun set(v: Int) {
            val next = v.coerceIn(s.range)
            if (next == value) return
            val before = value
            value = next; show()
            save(s, next) { ok -> if (!ok) { value = before; show() } }
        }
        val stepper = ui.horizontal(8, ui.smallButton("－") { set((value.toLong() - step).coerceAtLeast(Int.MIN_VALUE.toLong()).toInt()) }, label,
            ui.smallButton("＋") { set((value.toLong() + step).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) })
        val content = if (s.presets.isEmpty()) stepper else ui.vertical(6, stepper,
            ui.flow(6, s.presets.map { p -> ui.smallButton(if (p == 0 && s.range.first <= 0) "不限" else if (s.unit.isEmpty()) "$p" else "$p ${s.unit}") { set(p) } }))
        return block(s.title, summaryOf(s, where), content)
    }

    private fun floatControl(s: FloatSetting, where: String?): View {
        val current = store.value(s) as Float
        if (s.presets.isEmpty()) return block(s.title, summaryOf(s, where), appLink(s, "现在是 $current"))
        val i = s.presets.indexOfFirst { it == current }
        return block(s.title, summaryOf(s, where), ui.choiceChips(s.presets.map { "$it" }, i) { j -> save(s, s.presets[j]) })
    }

    private fun choiceSetControl(s: StringSetSetting, where: String?): View {
        @Suppress("UNCHECKED_CAST")
        val selected = (store.value(s) as Set<String>).toMutableSet()
        val hide = SettingChoices.isHideSet(s)
        val chips = s.choices.map { o ->
            val toggle: (Boolean) -> Unit = { on ->
                if (on) selected += o.id else selected -= o.id
                save(s, selected.toSet())
            }
            if (hide) ui.hideChip(o.label, o.id in selected, onToggle = toggle) else ui.chip(o.label, o.id in selected, toggle)
        }
        val summary = if (hide) listOfNotNull(summaryOf(s, where), "点一下隐藏，再点恢复").joinToString(" · ") else summaryOf(s, where)
        return block(s.title, summary, ui.flow(6, chips))
    }

    /** 目录里的多选（底栏、顶部标签、“我的”页入口等）：选项是【宿主】报来的真实项目；不在目录里的旧选项单独列出，可以取消。 */
    private fun catalogSetControl(s: StringSetSetting, where: String?): View {
        @Suppress("UNCHECKED_CAST")
        val selected = (store.value(s) as Set<String>).toMutableSet()
        val entries = store.choices(s)
        val missing = selected.filter { id -> entries?.none { it.first == id } ?: true }
        val options = entries.orEmpty() + missing.mapIndexed { i, id -> id to "已不在目录里的旧选项 ${i + 1}" }
        val chips = options.map { (id, label) ->
            val toggle: (Boolean) -> Unit = { on ->
                if (on) selected += id else selected -= id
                save(s, selected.toSet())
            }
            if (SettingChoices.isHideSet(s)) ui.hideChip(label, id in selected, onToggle = toggle) else ui.chip(label, id in selected, toggle)
        }
        val summary = if (entries == null) listOfNotNull(where, "打开一次对应的页面后，这里会列出可选的项目").joinToString(" · ") else summaryOf(s, where)
        return block(s.title, summary, if (chips.isEmpty()) null else ui.flow(6, chips))
    }

    /** 目录里的单选（“我的”页自定义按钮替换哪个入口、跳到哪、用谁的图标）：第一项“不选”清空。 */
    private fun catalogChoiceControl(s: StringSetting, where: String?): View {
        val current = store.value(s) as String
        val entries = store.choices(s)
        val options = listOf("" to "不选") + entries.orEmpty() +
            listOfNotNull(current.takeIf { it.isNotEmpty() && entries?.none { e -> e.first == it } != false }?.let { it to "已不在目录里的旧选项" })
        val summary = if (entries == null) listOfNotNull(where, "打开一次“我的”页后，这里会列出可选的入口").joinToString(" · ") else summaryOf(s, where)
        val at = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        return block(s.title, summary, ui.choiceChips(options.map { it.second }, at) { i -> save(s, options[i].first) })
    }

    /** 排序（逗号分隔的标识）：按点的先后排，再点一次移出；没选的照原顺序排在后面。 */
    private fun orderControl(s: StringSetting, where: String?, items: List<Pair<String, String>>): View {
        var order = (store.value(s) as String).split(',').map { it.trim() }.filter { id -> items.any { it.first == id } }.distinct()
        val preview = ui.text("", TextSize.LABEL, t.textSecondary)
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        fun render() {
            val result = order + items.map { it.first }.filterNot { it in order }
            preview.text = "现在的顺序：" + result.joinToString(" → ") { id -> items.first { it.first == id }.second }
            box.removeAllViews()
            val chips = items.map { (id, name) ->
                val i = order.indexOf(id)
                ui.chip(if (i >= 0) "${i + 1}. $name" else name, i >= 0) { _ ->
                    val before = order
                    order = if (id in order) order - id else order + id
                    save(s, order.joinToString(",")) { ok -> if (!ok) order = before; render() }
                }
            } + listOfNotNull(if (order.isEmpty()) null else ui.smallButton("恢复原顺序") {
                val before = order
                order = emptyList()
                save(s, "") { ok -> if (!ok) order = before; render() }
            })
            box.addView(ui.flow(6, chips))
        }
        render()
        return block(s.title, summaryOf(s, where), ui.vertical(6, preview, box))
    }

    /** 色板：点一个颜色直接保存（默认值是八位的存 FF + 六位）。 */
    private fun palette(s: StringSetting, input: EditText): View {
        val eight = s.default.length == 8 || s.default.isEmpty()
        val current = (store.value(s) as String).removePrefix("#")
        val swatches = ArrayList<View>()
        fun mark(sel: String) = swatches.forEachIndexed { i, v ->
            val hex = SettingChoices.PALETTE[i].second
            val on = sel.equals(if (eight) "FF$hex" else hex, true) || (sel.length >= 6 && sel.takeLast(6).equals(hex, true))
            v.background = ui.rounded(0xFF000000.toInt() or hex.toInt(16), 18f, if (on) t.text else t.chipBorder, if (on) 3f else 1f)
            v.isSelected = on
        }
        SettingChoices.PALETTE.forEach { (name, hex) ->
            swatches += View(activity).apply {
                layoutParams = ViewGroup.LayoutParams(t.dp(36), t.dp(36))
                contentDescription = name
                isClickable = true
                setOnClickListener {
                    val v = if (eight) "FF$hex" else hex
                    input.setText(v)
                    mark(v)
                    save(s, v)
                }
            }
        }
        mark(current)
        return ui.flow(10, swatches)
    }

    /** 关键词、UID 这类自由列表：一行一项。 */
    private fun listControl(s: StringSetSetting, where: String?): View {
        @Suppress("UNCHECKED_CAST")
        var items = (store.value(s) as Set<String>).sorted()
        val count = ui.text("", TextSize.LABEL, t.textSecondary)
        fun showCount() { count.text = if (items.isEmpty()) "还没有内容" else "共 ${items.size} 项：" + items.take(6).joinToString("、") + if (items.size > 6) "…" else "" }
        showCount()
        val editor = editText(items.joinToString("\n"), multiLine = true).apply { visibility = View.GONE }
        val actions = LinearLayout(activity).apply { visibility = View.GONE }
        val edit = ui.smallButton("编辑") { editor.visibility = View.VISIBLE; actions.visibility = View.VISIBLE }
        actions.apply {
            orientation = LinearLayout.HORIZONTAL
            addView(ui.smallButton("保存") {
                val next = editor.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                save(s, next) { ok -> if (ok) { items = next.sorted(); showCount(); editor.visibility = View.GONE; actions.visibility = View.GONE } }
            })
            addView(ui.smallButton("取消") { editor.setText(items.joinToString("\n")); editor.visibility = View.GONE; actions.visibility = View.GONE },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = t.dp(8) })
        }
        return block(s.title, summaryOf(s, where).let { listOfNotNull(it, "一行一项").joinToString(" · ") }, ui.vertical(6, count, edit, editor, actions))
    }

    private fun textControl(s: StringSetting, where: String?): View {
        val current = store.value(s) as String
        if (s.format == TextFormat.SECRET) return block(s.title, summaryOf(s, where), appLink(s, if (current.isEmpty()) "未设置" else "已设置（不在这里显示）"))
        val input = editText(current, multiLine = false)
        val saveButton = ui.smallButton("保存") {
            val text = input.text.toString().trim()
            when (val r = SettingWrite.checkText(s.format, text)) {
                is SettingWrite.Result.Bad -> HostToast.show(activity, "没改成：${s.title}", r.reason)
                is SettingWrite.Result.Ok -> save(s, text)
            }
        }
        // 常用值点一下直接保存；输入框留给想要别的值的人
        val presets = if (s.presets.isEmpty()) null else ui.choiceChips(s.presets.map { it.label }, s.presets.indexOfFirst { it.id == current }) { i ->
            input.setText(s.presets[i].id)
            save(s, s.presets[i].id)
        }
        val colors = if (s.format == TextFormat.COLOR) palette(s, input) else null
        input.hint = when {
            colors != null -> "别的颜色（色值）"
            s.format == TextFormat.TIME_WINDOW && presets != null -> "别的时段（如 22:00-07:00）"
            presets != null -> "自己写"
            else -> input.hint
        }
        return block(s.title, summaryOf(s, where), ui.vertical(6, *listOfNotNull(colors, presets, input, saveButton).toTypedArray()))
    }

    private fun appLink(s: Setting<*>, state: String): View =
        ui.horizontal(8, ui.text(state, TextSize.LABEL, t.textSecondary), ui.smallButton("在【模块】里改") { store.openApp(activity, SettingPages.routeOf(s.key)); dialog.dismiss() })

    // ---- 小部件 ----

    private fun editText(value: String, multiLine: Boolean) = EditText(activity).apply {
        setText(value)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TextSize.BODY)
        setTextColor(t.text)
        background = ui.rounded(t.surface, 14f, t.chipBorder)
        setPadding(t.dp(14), t.dp(10), t.dp(14), t.dp(10))
        if (multiLine) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3; maxLines = 10
            gravity = Gravity.TOP or Gravity.START
        } else setSingleLine()
    }

    private fun block(title: String, summary: String?, content: View?): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(t.dp(16), t.dp(8), t.dp(16), t.dp(8))
        addView(ui.text(title, TextSize.ITEM, t.text, t.medium))
        if (!summary.isNullOrEmpty()) addView(ui.text(summary, TextSize.CAPTION, t.textSecondary), ui.lp(top = 1))
        if (content != null) addView(content, ui.lp(top = 6))
    }

    /** 单选的一行：标题、当前值和 ›，点开弹出选项。 */
    private fun choiceRow(s: ChoiceSetting, where: String?, current: Int): View {
        var at = current
        val value = ui.text(s.options.getOrNull(at)?.label.orEmpty(), TextSize.LABEL, t.textSecondary).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = t.dp(150) }
        val texts = ui.vertical(1, ui.text(s.title, TextSize.ITEM, t.text, t.medium),
            ui.text(summaryOf(s, where).orEmpty(), TextSize.CAPTION, t.textSecondary).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; visibility = if (summaryOf(s, where).isNullOrEmpty()) View.GONE else View.VISIBLE })
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = t.dp(48)
            setPadding(t.dp(16), t.dp(4), t.dp(12), t.dp(4))
            background = ui.pressable(ColorDrawable(Color.TRANSPARENT), 0f)
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = t.dp(8) })
            addView(ui.text("›", 18f, t.textSecondary), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = t.dp(4) })
            contentDescription = "${s.title}，现在是${s.options.getOrNull(at)?.label.orEmpty()}"
            isClickable = true
            setOnClickListener { anchor ->
                val menu = android.widget.PopupMenu(activity, anchor, Gravity.END)
                s.options.forEachIndexed { i, o -> menu.menu.add(0, i, i, if (i == at) "✓ ${o.label}" else o.label) }
                menu.setOnMenuItemClickListener { item ->
                    val before = at
                    at = item.itemId
                    value.text = s.options[at].label
                    save(s, s.options[at].id) { ok -> if (!ok) { at = before; value.text = s.options[before].label } }
                    true
                }
                menu.show()
            }
        }
    }

    private fun navRow(title: String, summary: String, onClick: () -> Unit): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = t.dp(56)
        setPadding(t.dp(16), t.dp(8), t.dp(12), t.dp(8))
        background = ui.pressable(ColorDrawable(t.surface), 0f)
        addView(ui.vertical(2, ui.text(title, TextSize.ITEM, t.text, t.medium), ui.text(summary, TextSize.CAPTION, t.textSecondary)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ui.text("›", 20f, t.textSecondary))
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** 一组行放进白色圆角卡片，行间细线。 */
    private fun card(parent: LinearLayout, rows: List<View>) {
        if (rows.isEmpty()) return
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rounded(t.surface, 20f)
            clipToOutline = true
        }
        rows.forEachIndexed { i, v ->
            if (i > 0) box.addView(ui.divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { marginStart = t.dp(16) })
            box.addView(v)
        }
        parent.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(t.dp(16), t.dp(4), t.dp(16), t.dp(12))
        })
    }

    private fun sectionLabel(text: String) = ui.text(text, TextSize.LABEL, t.textSecondary, t.medium).apply {
        setPadding(t.dp(20), t.dp(12), t.dp(20), t.dp(4))
    }

    private fun note(text: String) = ui.text(text, TextSize.CAPTION, t.textSecondary).apply {
        setPadding(t.dp(20), 0, t.dp(20), t.dp(16))
    }

    private fun updateRestartBar() {
        val n = store.pendingRestart.size
        restartBar.visibility = if (n > 0) View.VISIBLE else View.GONE
        restartText.text = "$n 项改动重开【宿主】后生效"
    }

    companion object {
        /** 只在 App 里管的分类：App 自己的外观、配置作用域。 */
        private val APP_ONLY = setOf("appearance", "scope")
        private const val MAX_RESULTS = 40

        /** 上次看的场景和分段（这次运行里记住）。 */
        private var lastScene: String? = null
    }
}

/** 设置窗顶部的线条图标（搜索、在 App 中打开、关闭），按 24 格画，2dp 线宽。只在代码里创建（不从布局文件加载）。 */
@android.annotation.SuppressLint("ViewConstructor")
class IconView(context: android.content.Context, private val t: HostTheme, private val kind: Kind) : View(context) {
    enum class Kind { SEARCH, OPEN, CLOSE }

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }

    private val path = android.graphics.Path()

    override fun onDraw(canvas: android.graphics.Canvas) {
        val u = t.dp(22).toFloat() / 24f
        canvas.save()
        canvas.translate((width - 24 * u) / 2, (height - 24 * u) / 2)
        canvas.scale(u, u)
        paint.color = t.text
        paint.strokeWidth = 2f
        when (kind) {
            Kind.SEARCH -> { canvas.drawCircle(11f, 11f, 7f, paint); canvas.drawLine(16.5f, 16.5f, 20f, 20f, paint) }
            Kind.CLOSE -> { canvas.drawLine(6f, 6f, 18f, 18f, paint); canvas.drawLine(18f, 6f, 6f, 18f, paint) }
            Kind.OPEN -> {
                val p = path
                p.reset()
                p.moveTo(14f, 4f); p.lineTo(20f, 4f); p.lineTo(20f, 10f)
                p.moveTo(20f, 4f); p.lineTo(11f, 13f)
                p.moveTo(18f, 14f); p.lineTo(18f, 19f); p.quadTo(18f, 20f, 17f, 20f); p.lineTo(5f, 20f); p.quadTo(4f, 20f, 4f, 19f); p.lineTo(4f, 7f); p.quadTo(4f, 6f, 5f, 6f); p.lineTo(10f, 6f)
                canvas.drawPath(p, paint)
            }
        }
        canvas.restore()
    }
}
