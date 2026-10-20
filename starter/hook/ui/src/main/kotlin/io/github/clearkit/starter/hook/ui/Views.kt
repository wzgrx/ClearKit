package io.github.clearkit.starter.hook.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** 【宿主】里各个浮层共用的小控件。 */
class Ui(val t: HostTheme) {
    val context get() = t.context

    fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null, strokeDp: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = t.dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(t.dp(strokeDp), strokeColor)
        }

    fun pressable(background: Drawable, radiusDp: Float): Drawable =
        RippleDrawable(ColorStateList.valueOf(0x22000000), background, rounded(0xFF000000.toInt(), radiusDp))

    fun text(
        value: CharSequence,
        sizeSp: Float = TextSize.BODY,
        color: Int = t.text,
        typeface: Typeface? = null,
        maxLines: Int = Int.MAX_VALUE,
    ) = TextView(context).apply {
        text = ZhText.convert(value)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (typeface != null) this.typeface = typeface
        if (maxLines != Int.MAX_VALUE) {
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
        }
        includeFontPadding = false
        setLineSpacing(0f, 1.25f)
    }

    fun vertical(gapDp: Int = 0, vararg children: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        children.forEachIndexed { i, c -> addView(c, lp(top = if (i > 0) gapDp else 0)) }
    }

    fun horizontal(gapDp: Int = 0, vararg children: View) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        children.forEachIndexed { i, c ->
            addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) marginStart = t.dp(gapDp)
            })
        }
    }

    fun lp(width: Int = ViewGroup.LayoutParams.MATCH_PARENT, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(width, height, weight).apply { topMargin = t.dp(top) }

    /** 48dp 胶囊：主要 = 强调色实心，次要 = 白底描边。 */
    fun button(label: String, primary: Boolean = true, onClick: () -> Unit) = text(
        label, sizeSp = 16f, color = if (primary) t.onAccent else t.text, typeface = t.medium,
    ).apply {
        gravity = Gravity.CENTER
        minHeight = t.dp(48)
        setPadding(t.dp(20), 0, t.dp(20), 0)
        background = pressable(if (primary) rounded(t.accent, 24f) else rounded(t.surface, 24f, t.chipBorder), 24f)
        isClickable = true
        setOnClickListener { onClick() }
    }

    /**
     * 看起来 [visualDp] 高、可点范围至少 48dp 的背景（审查：小按钮、芯片只有 32dp，难点中）：
     * 背景上下各缩进一截，视图本身按 48dp 排版。
     */
    fun compactTarget(view: View, fill: Int, visualDp: Int = 32, stroke: Int? = null) {
        view.minimumHeight = t.dp(TOUCH_DP)
        if (view is TextView) view.minHeight = t.dp(TOUCH_DP)
        // setBackground 会用 InsetDrawable 的 padding（左右 0）覆盖已设的 padding，按钮文字就贴着胶囊边；先存后还。
        val l = view.paddingLeft; val top = view.paddingTop; val r = view.paddingRight; val b = view.paddingBottom
        view.background = insetPill(fill, visualDp, stroke)
        view.setPadding(l, top, r, b)
    }

    /** [visualDp] 高的圆角背景，上下缩进到 48dp 的触控高度里（见 [compactTarget]）。 */
    fun insetPill(fill: Int, visualDp: Int, stroke: Int? = null, pressed: Boolean = true): android.graphics.drawable.Drawable {
        val inset = t.dp(((TOUCH_DP - visualDp) / 2).coerceAtLeast(0))
        val base = rounded(fill, visualDp / 2f, stroke)
        return android.graphics.drawable.InsetDrawable(if (pressed) pressable(base, visualDp / 2f) else base, 0, inset, 0, inset)
    }

    /** Small rounded action, e.g. “全选”。看起来 32dp 高，可点范围 48dp。 */
    fun smallButton(label: String, onClick: () -> Unit) = text(label, TextSize.LABEL, t.text, t.medium).apply {
        gravity = Gravity.CENTER
        setPadding(t.dp(14), 0, t.dp(14), 0)
        compactTarget(this, t.surface, 34, t.chipBorder)
        setOnClickListener { onClick() }
    }

    /** Toggle chip; [onToggle] receives the new state and the chip restyles itself. */
    fun chip(label: String, initial: Boolean, onToggle: (Boolean) -> Unit): TextView {
        var on = initial
        return text(label, TextSize.LABEL).apply {
            gravity = Gravity.CENTER
            setPadding(t.dp(12), 0, t.dp(12), 0)
            fun style() {
                setTextColor(if (on) t.onAccentSoft else t.text)
                typeface = if (on) t.medium else Typeface.DEFAULT
                compactTarget(this, if (on) t.accentSoft else t.surface, 34, if (on) null else t.chipBorder)
                text = if (on) "✓ $label" else label
                isSelected = on
            }
            style()
            // 读屏念“已选中 / 未选中”。
            accessibilityDelegate = checkableDelegate(android.widget.CheckBox::class.java.name) { on }
            setOnClickListener { on = !on; style(); onToggle(on) }
        }
    }

    /**
     * 选上 = 隐藏的小格（5.4）：显示的照常；隐藏的变灰、划掉、前面加 ✕，不用打勾（打勾容易看成“要”）。
     * [locked] 的项不能藏，显示成灰色、点了没反应。
     */
    fun hideChip(label: String, initial: Boolean, locked: Boolean = false, onToggle: (Boolean) -> Unit): TextView {
        var hidden = initial && !locked
        return text(label, TextSize.LABEL).apply {
            gravity = Gravity.CENTER
            setPadding(t.dp(12), 0, t.dp(12), 0)
            fun style() {
                setTextColor(if (hidden || locked) t.textDisabled else t.text)
                paintFlags = if (hidden) paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                compactTarget(this, if (hidden) t.seg else t.surface, 34, t.chipBorder)
                text = if (hidden) "✕ $label" else label
                contentDescription = label + if (locked) "，不能隐藏" else if (hidden) "，已隐藏" else "，显示"
            }
            style()
            if (!locked) setOnClickListener { hidden = !hidden; style(); onToggle(hidden) }
        }
    }

    /** Segmented control (相册/日历, 全部/最近 20 篇…). */
    fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        var current = selected
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(t.seg, 14f)
            setPadding(t.dp(4), t.dp(4), t.dp(4), t.dp(4))
        }
        val items = options.mapIndexed { i, label ->
            text(label, TextSize.BODY).apply {
                gravity = Gravity.CENTER
                minHeight = t.dp(34)
                // 放大字体时可以换行（不在外面写死高度，见调用方）。
                setPadding(t.dp(4), t.dp(4), t.dp(4), t.dp(4))
                accessibilityDelegate = checkableDelegate(android.widget.RadioButton::class.java.name) { current == i }
                setOnClickListener { current = i; restyle(row, current); onSelect(i) }
            }
        }
        items.forEach { row.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        restyle(row, current)
        return row
    }

    private fun restyle(row: LinearLayout, selected: Int) {
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i) as TextView
            val on = i == selected
            v.setTextColor(if (on) t.text else t.textSecondary)
            v.typeface = if (on) t.medium else Typeface.DEFAULT
            v.background = if (on) rounded(t.segOn, 11f) else null
            v.isSelected = on
        }
    }

    /** 单选的一排标签，选项多时自动换行（筛选条件）。 */
    fun choiceChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        var current = selected
        val views = ArrayList<TextView>()
        fun style() = views.forEachIndexed { j, v ->
            val on = current == j
            v.setTextColor(if (on) t.onAccentSoft else t.text)
            v.typeface = if (on) t.medium else Typeface.DEFAULT
            compactTarget(v, if (on) t.accentSoft else t.surface, 34, if (on) null else t.chipBorder)
            v.text = if (on) "✓ ${labels[j]}" else labels[j]
            v.isSelected = on
        }
        labels.forEachIndexed { i, label ->
            views += text(label, TextSize.LABEL, t.text).apply {
                gravity = Gravity.CENTER
                setPadding(t.dp(12), 0, t.dp(12), 0)
                accessibilityDelegate = checkableDelegate(android.widget.RadioButton::class.java.name) { current == i }
                setOnClickListener { current = i; style(); onSelect(i) }
            }
        }
        style()
        return flow(6, views)
    }

    /** Wraps chips into rows (simple flow layout). */
    fun flow(gapDp: Int, children: List<View>): View = FlowLayout(context, t.dp(gapDp)).apply { children.forEach(::addView) }

    fun divider() = View(context).apply { setBackgroundColor(t.divider) }

    /**
     * 设置行：标题 + 说明 + 右侧 52×32 的 M3 开关，整行可点。[summary] 可以之后改
     * （返回的 [ToggleRow.summary]）。
     */
    fun toggleRow(title: String, summary: String?, initial: Boolean, onToggle: (Boolean) -> Unit): ToggleRow {
        var on = initial
        val sub = text(summary.orEmpty(), TextSize.CAPTION, t.textSecondary).apply { visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE }
        val texts = vertical(2, text(title, TextSize.ITEM, t.text, t.medium), sub)
        val track = SwitchView(context, t).apply { checked = on }
        fun style() { track.checked = on }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = t.dp(48)
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(track, LinearLayout.LayoutParams(t.dp(52), t.dp(32)).apply { marginStart = t.dp(12) })
            contentDescription = title
            isClickable = true
            // 读屏按开关念：“标题，开关，已开启”（审查：以前只念标题，不知道开没开）。
            accessibilityDelegate = checkableDelegate(android.widget.Switch::class.java.name) { on }
            setOnClickListener { on = !on; style(); isSelected = on; onToggle(on); sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED) }
        }
        style()
        return ToggleRow(row, sub, { on }) { value -> on = value; row.isSelected = value; style() }
    }

    fun hScroll(child: View) = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        addView(child)
    }

    fun frame() = FrameLayout(context)

    /** 读屏把视图当成 [role]（开关、复选框、单选）念，并报告 [checked] 的状态。 */
    fun checkableDelegate(role: String, checked: () -> Boolean) = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = role
            info.isCheckable = true
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                info.setChecked(if (checked()) android.view.accessibility.AccessibilityNodeInfo.CHECKED_STATE_TRUE else android.view.accessibility.AccessibilityNodeInfo.CHECKED_STATE_FALSE)
            } else {
                @Suppress("DEPRECATION")
                info.isChecked = checked()
            }
        }

        override fun onInitializeAccessibilityEvent(host: View, event: android.view.accessibility.AccessibilityEvent) {
            super.onInitializeAccessibilityEvent(host, event)
            event.className = role
            event.isChecked = checked()
        }
    }

    companion object {
        /** 最小触控尺寸。 */
        const val TOUCH_DP = 48
    }
}

/** A row built by [Ui.toggleRow]. */
class ToggleRow(val view: View, val summary: TextView, private val state: () -> Boolean, private val update: (Boolean) -> Unit) {
    /** Reading is live; setting restyles without calling the toggle callback. */
    var checked: Boolean
        get() = state()
        set(value) = update(value)

    fun setSummary(value: String?) {
        summary.text = value.orEmpty()
        summary.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}

/** Minimal wrapping layout for chips. */
@android.annotation.SuppressLint("ViewConstructor") // 宿主进程里纯代码构造，从不从 XML 加载，不需要 (Context, AttributeSet) 构造
class FlowLayout(context: android.content.Context, private val gap: Int) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            if (x > 0 && x + c.measuredWidth > maxWidth) {
                x = 0; y += rowHeight + gap; rowHeight = 0
            }
            x += c.measuredWidth + gap
            rowHeight = maxOf(rowHeight, c.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), y + rowHeight + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (x > 0 && x + c.measuredWidth > maxWidth) {
                x = 0; y += rowHeight + gap; rowHeight = 0
            }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + c.measuredWidth, paddingTop + y + c.measuredHeight)
            x += c.measuredWidth + gap
            rowHeight = maxOf(rowHeight, c.measuredHeight)
        }
    }
}

/**
 * Material 3 样式的开关（只画，不处理点击）：开 = 强调色轨道 + 带对勾的 24dp 白滑块；
 * 关 = 浅槽 + 2dp 描边 + 16dp 描边色滑块。和 App 的 BcSwitch 一样。只在代码里创建。
 */
@android.annotation.SuppressLint("ViewConstructor")
class SwitchView(context: android.content.Context, private val t: HostTheme) : View(context) {
    var checked: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val check = android.graphics.Path()

    override fun onDraw(canvas: android.graphics.Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = h / 2
        paint.style = android.graphics.Paint.Style.FILL
        if (checked) {
            paint.color = t.accent
            canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            val tr = t.dp(12).toFloat()
            val cx = w - t.dp(4) - tr
            paint.color = if (t.dark) t.onAccent else android.graphics.Color.WHITE
            canvas.drawCircle(cx, r, tr, paint)
            paint.color = t.accent
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = t.dp(2).toFloat()
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            paint.strokeJoin = android.graphics.Paint.Join.ROUND
            val u = t.dp(1).toFloat()
            check.reset()
            check.moveTo(cx - 4.5f * u, r)
            check.lineTo(cx - 1.5f * u, r + 3f * u)
            check.lineTo(cx + 4.5f * u, r - 3f * u)
            canvas.drawPath(check, paint)
        } else {
            paint.color = t.seg
            canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            val sw = t.dp(2).toFloat()
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = sw
            paint.color = t.outline
            canvas.drawRoundRect(sw / 2, sw / 2, w - sw / 2, h - sw / 2, r - sw / 2, r - sw / 2, paint)
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawCircle(t.dp(16).toFloat(), r, t.dp(8).toFloat(), paint)
        }
    }
}
