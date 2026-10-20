package io.github.clearkit.starter.model.preview

import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.ChoiceSetting
import io.github.clearkit.starter.model.settings.IntSetting
import io.github.clearkit.starter.model.settings.StringSetting
import io.github.clearkit.starter.model.settings.Setting
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.model.settings.StringSetSetting

/**
 * 实物预览：【宿主】各页面的示意图。每一块绑定一项设置（开关，或多选里的一个选项），
 * App（Compose）和宿主里的设置窗（View）按同一份结构画，点块就改设置，改设置块跟着变。
 * 示意图只画“有哪些块、大致顺序”，名字用【宿主】界面上的叫法，不画像素级的样子。
 *
 * **加一个设置后**：在下面 [PAGES] 里它所在那一页加一块（hide / mark / add / value，见 ClearKit docs/05、docs/11）。
 * 覆盖测试（PreviewCoverageTest）会检查每项设置都画进了示意图。
 */
object PagePreviews {
    /**
     * 块和设置的绑定：开关为真 / 多选里选上了 [option] / 单选选的是 [option] = 生效。
     * [requires] 是这项要靠的总开关（例如“净化消息中心”）：总开关关着时不算生效，点块会把总开关一起打开。
     */
    data class Bind(val setting: Setting<*>, val option: String? = null, val requires: BoolSetting? = null, val minKeep: Int = 0, val maxKeep: Int = Int.MAX_VALUE)

    /**
     * 生效时的画法：藏 = 划掉；标记 = 块上加一个标签（例如“自动展开”）；新增 = 平时虚线，生效时显示成新加的东西；
     * 数值（6.2）= 块里直接写当前值（标签里的 `{v}`），改过就用强调色，点一下换到下一个常用值。
     */
    enum class Effect { HIDE, MARK, ADD, VALUE }

    /** 数值怎么写：自动（按单位）、倍速（百分数写成“1.25 倍”）、逗号分隔的列表（写“N 项”）。 */
    enum class Fmt { AUTO, SPEED, LIST }

    /** 块的样子：普通、视频画面（深色）、画面上的浮层、一行并排、带标题的一组小格、页签条。 */
    enum class Style { PLAIN, MEDIA, OVERLAY, ROW, GROUP, TABS }

    data class Block(
        val label: String,
        val bind: Bind? = null,
        val effect: Effect = Effect.HIDE,
        val mark: String? = null,
        val style: Style = Style.PLAIN,
        val children: List<Block> = emptyList(),
        /** 数值块：值为 0 / 空时 `{v}` 写成什么（默认“未设置”）。 */
        val zero: String? = null,
        val fmt: Fmt = Fmt.AUTO,
    ) {
        /** 这一块和它里面所有块绑定的设置。 */
        fun allBinds(): List<Bind> = listOfNotNull(bind) + children.flatMap { it.allBinds() }

        fun flatten(): List<Block> = listOf(this) + children.flatMap { it.flatten() }
    }

    data class Page(val route: String, val title: String, val blocks: List<Block>, val note: String? = null) {
        val binds: List<Bind> get() = blocks.flatMap { it.allBinds() }

        val allBlocks: List<Block> get() = blocks.flatMap { it.flatten() }

        /** 画这一页要读的全部设置（含总开关）。 */
        val settings: List<Setting<*>> get() = binds.flatMap { listOfNotNull(it.setting, it.requires) }.distinct()
    }

    private fun bool(s: BoolSetting, values: Map<String, Any?>) = (values[s.key] as? Boolean) ?: s.default

    /** 不看总开关时，这一项本身是不是选上了。 */
    private fun own(bind: Bind, values: Map<String, Any?>): Boolean {
        val v = values[bind.setting.key]
        return when (val s = bind.setting) {
            is BoolSetting -> (v as? Boolean) ?: s.default
            is StringSetSetting -> bind.option != null && bind.option in ((v as? Set<*>) ?: s.default)
            is ChoiceSetting -> bind.option != null && ((v as? String) ?: s.default) == bind.option
            else -> false
        }
    }

    /** 绑定现在是不是生效（总开关关着就不算）。数值块：和默认不一样就算。 */
    fun active(bind: Bind, values: Map<String, Any?>): Boolean = (bind.requires?.let { bool(it, values) } ?: true) && own(bind, values)

    private fun raw(s: Setting<*>, values: Map<String, Any?>): Any? = values[s.key] ?: s.default

    /** 数值块改过没有（和默认比；总开关关着不算）。 */
    fun changed(b: Block, values: Map<String, Any?>): Boolean {
        val bind = b.bind ?: return false
        if (b.effect != Effect.VALUE) return active(bind, values)
        return (bind.requires?.let { bool(it, values) } ?: true) && raw(bind.setting, values) != bind.setting.default
    }

    /** 块上要写的字：数值块把 `{v}` 换成当前值。凭据只说有没有设置，网址只写主机，不把内容画出来。 */
    fun label(b: Block, values: Map<String, Any?>): String {
        if (b.effect != Effect.VALUE) return b.label
        val bind = b.bind ?: return b.label
        return b.label.replace("{v}", valueText(bind.setting, raw(bind.setting, values), b.zero, b.fmt))
    }

    fun valueText(s: Setting<*>, v: Any?, zero: String?, fmt: Fmt): String {
        val none = zero ?: "未设置"
        return when (s) {
            is IntSetting -> {
                val n = (v as? Int) ?: s.default
                when {
                    n == 0 && zero != null -> zero
                    fmt == Fmt.SPEED -> trim(n / 100.0) + " 倍"
                    s.unit == "秒" && n >= 60 && n % 60 == 0 -> "${n / 60} 分钟"
                    s.unit == "分钟" && n >= 60 && n % 60 == 0 -> "${n / 60} 小时"
                    s.unit == "毫秒" && n >= 1000 -> trim(n / 1000.0) + " 秒"
                    s.unit == "次" && n >= 10000 -> trim(n / 10000.0) + " 万次"
                    s.unit.isEmpty() -> "$n"
                    s.unit.first().code < 128 -> "$n${s.unit}"
                    else -> "$n ${s.unit}"
                }
            }
            is StringSetting -> {
                val t = (v as? String).orEmpty().trim()
                when {
                    t.isEmpty() -> none
                    s.format == io.github.clearkit.starter.model.settings.TextFormat.SECRET -> "已设置"
                    s.format == io.github.clearkit.starter.model.settings.TextFormat.URL || t.contains("://") -> t.substringAfter("://").substringBefore('/').substringAfterLast('@')
                    s.format == io.github.clearkit.starter.model.settings.TextFormat.COLOR -> "#" + t.removePrefix("#").uppercase()
                    fmt == Fmt.LIST -> "${t.split(',').count { it.isNotBlank() }} 项"
                    t.contains('\n') -> "${t.lines().count { it.isNotBlank() }} 行"
                    t.length > 14 -> t.take(13) + "…"
                    else -> t
                }
            }
            is StringSetSetting -> {
                val set = (v as? Set<*>)?.map { it.toString() } ?: s.default.toList()
                when {
                    set.isEmpty() -> none
                    s.choices.isNotEmpty() -> set.mapNotNull { id -> s.choices.firstOrNull { it.id == id }?.label }.let { l ->
                        if (l.size <= 2) l.joinToString("、") else l.take(2).joinToString("、") + " 等 ${l.size} 项"
                    }
                    else -> "${set.size} 条"
                }
            }
            is ChoiceSetting -> s.label((v as? String) ?: s.default)
            is BoolSetting -> if ((v as? Boolean) ?: s.default) "开" else "关"
            else -> v.toString()
        }
    }

    private fun trim(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else "%.2f".format(d).trimEnd('0').trimEnd('.')

    /** 这块能不能点：数值块只有带常用值的数字、选项不多的单选能点（点一下换下一个）。 */
    fun tappable(b: Block): Boolean {
        val bind = b.bind ?: return false
        if (b.effect != Effect.VALUE) return true
        return when (val st = bind.setting) {
            is IntSetting -> st.presets.isNotEmpty()
            is ChoiceSetting -> st.options.size <= 5
            is BoolSetting -> true
            else -> false
        }
    }

    /** 点数值块：换到下一个常用值 / 下一个选项（总开关关着时一起打开）。 */
    fun step(b: Block, values: Map<String, Any?>): List<Pair<Setting<*>, Any>> {
        val bind = b.bind ?: return emptyList()
        if (b.effect != Effect.VALUE) return toggle(bind, values)
        if (!tappable(b)) return emptyList()
        val req = bind.requires?.takeIf { !bool(it, values) }?.let { listOf<Pair<Setting<*>, Any>>(it to true) }.orEmpty()
        val next: Pair<Setting<*>, Any>? = when (val st = bind.setting) {
            is IntSetting -> {
                val cur = (values[st.key] as? Int) ?: st.default
                val list = st.presets.distinct()
                (list.firstOrNull { it > cur } ?: list.first()).let { st to it }
            }
            is ChoiceSetting -> {
                val ids = st.options.map { it.id }
                val i = ids.indexOf((values[st.key] as? String) ?: st.default)
                st to ids[(i + 1) % ids.size]
            }
            is BoolSetting -> st to !bool(st, values)
            else -> null
        }
        return req + listOfNotNull(next)
    }

    /**
     * 点一下这块要写的值：开关取反 / 多选加减这个选项 / 单选选上这一项（再点回到默认）。
     * 总开关关着时：打开总开关，这一项没选上的顺便选上（点了就要看到它生效）。
     */
    fun toggle(bind: Bind, values: Map<String, Any?>): List<Pair<Setting<*>, Any>> {
        val req = bind.requires
        if (req != null && !bool(req, values)) {
            return listOf<Pair<Setting<*>, Any>>(req to true) + (if (own(bind, values)) emptyList() else flip(bind, values))
        }
        return flip(bind, values)
    }

    private fun flip(bind: Bind, values: Map<String, Any?>): List<Pair<Setting<*>, Any>> = when (val s = bind.setting) {
        is BoolSetting -> listOf(s to !own(bind, values))
        is StringSetSetting -> {
            val cur = (values[s.key] as? Set<*>)?.map { it.toString() }?.toSet() ?: s.default
            val o = bind.option
            val next = if (o == null) null else if (o in cur) cur - o else cur + o
            // 多选有上下限（“至少留一个”“最多 4 个”）时，越界的点击不改
            if (next == null || next.size < bind.minKeep || next.size > bind.maxKeep) emptyList() else listOf(s to next)
        }
        // 单选：点没选的 = 选上；再点已选的 = 回到默认（示意图里通常不画“跟随”那一项）
        is ChoiceSetting -> when {
            bind.option == null -> emptyList()
            !own(bind, values) -> listOf(s to bind.option)
            bind.option != s.default -> listOf(s to s.default)
            else -> emptyList()
        }
        else -> emptyList()
    }

    /**
     * 示意图里就能改完的设置（6.2 高密度：列表里不再重复）：开关；单选的选项都画了或能点着换；
     * 多选的每个选项都画了。数值、文字、名单要在列表里改具体值，不算。
     */
    fun operable(page: Page): Set<String> {
        val blocks = page.allBlocks.filter { it.bind != null && tappable(it) }
        return blocks.mapNotNull { b ->
            val s = b.bind!!.setting
            val ok = when (s) {
                is BoolSetting -> true
                is ChoiceSetting -> b.effect == Effect.VALUE || s.options.filter { it.id != s.default }.all { o -> page.binds.any { it.setting == s && it.option == o.id } }
                is StringSetSetting -> s.choices.isNotEmpty() && s.choices.all { o -> page.binds.any { it.setting == s && it.option == o.id } }
                else -> false
            }
            s.key.takeIf { ok }
        }.toSet()
    }

    /** 没生效时要不要淡淡写出标签：开关要（只看名字不知道点了会怎样）；单选、多选的块名字就是选项，不用。 */
    fun hintWhenOff(b: Block): Boolean = b.effect == Effect.MARK && b.bind?.setting is BoolSetting

    /** 一页里和默认不一样的块数 / 可点的块数（默认就开着的不算“改了”）。 */
    fun count(page: Page, values: Map<String, Any?>): Pair<Int, Int> =
        page.allBlocks.count { b -> b.bind != null && if (b.effect == Effect.VALUE) changed(b, values) else active(b.bind, values) != active(b.bind, emptyMap()) } to page.binds.size

    private val c = Settings.Cleanup
    private val m = Settings.Misc
    private val md = Settings.Module
    private fun hide(label: String, s: Setting<*>, option: String? = null, requires: BoolSetting? = null) = Block(label, Bind(s, option, requires))
    private fun mark(label: String, s: Setting<*>, mark: String, option: String? = null, requires: BoolSetting? = null) = Block(label, Bind(s, option, requires), Effect.MARK, mark)
    private fun add(label: String, s: Setting<*>) = Block(label, Bind(s), Effect.ADD)
    private fun overlay(label: String, s: Setting<*>, option: String? = null) = Block(label, Bind(s, option), style = Style.OVERLAY)
    private fun overlayMark(label: String, s: Setting<*>, mark: String) = Block(label, Bind(s), Effect.MARK, mark, Style.OVERLAY)
    private fun overlayAdd(label: String, s: Setting<*>) = Block(label, Bind(s), Effect.ADD, style = Style.OVERLAY)
    private fun media(label: String, vararg children: Block) = Block(label, style = Style.MEDIA, children = children.toList())
    private fun row(vararg children: Block) = Block("", style = Style.ROW, children = children.toList())
    private fun tabs(label: String, vararg children: Block) = Block(label, style = Style.TABS, children = children.toList())
    private fun plain(label: String) = Block(label)
    private fun value(label: String, s: Setting<*>, zero: String? = null, requires: BoolSetting? = null, fmt: Fmt = Fmt.AUTO) =
        Block(label, Bind(s, requires = requires), Effect.VALUE, zero = zero, fmt = fmt)
    private fun overlayValue(label: String, s: Setting<*>, zero: String? = null, requires: BoolSetting? = null, fmt: Fmt = Fmt.AUTO) =
        Block(label, Bind(s, requires = requires), Effect.VALUE, style = Style.OVERLAY, zero = zero, fmt = fmt)
    private fun choiceMarks(s: ChoiceSetting, mark: String = "已选", skip: Set<String> = emptySet(), requires: BoolSetting? = null) =
        s.options.filter { it.id !in skip }.map { mark(it.label, s, mark, it.id, requires) }
    private fun setMarks(s: StringSetSetting, mark: String, requires: BoolSetting? = null, minKeep: Int = 0, maxKeep: Int = Int.MAX_VALUE) =
        s.choices.map { Block(it.label, Bind(s, it.id, requires, minKeep, maxKeep), Effect.MARK, mark) }
    private fun group(label: String, children: List<Block>, bind: Bind? = null) = Block(label, bind, style = Style.GROUP, children = children)
    private fun options(s: StringSetSetting, list: List<Pair<String, String>>) = list.map { (id, name) -> hide(name, s, id) }

    /**
     * 每一页的示意图，路由和 FeatureCatalog / App 页面一致。照【宿主】界面的真实顺序画：
     * - hide(名字, 设置[, 选项])：开了会藏掉的东西（划掉）；
     * - mark(名字, 设置, 标签)：开了会改样子或行为（加标签）；
     * - add(名字, 设置)：新加的东西（“+”块）；
     * - value("文字 {v}", 设置, 为 0 时写什么)：数值、文字；
     * - group(标题, 块列表)、row(块...)、media(标题, overlay(...)...)：结构；plain(名字)：只是陪衬。
     */
    val PAGES: List<Page> = listOf(
        Page(
            "cleanup", "去广告与精简",
            listOf(
                group("【宿主】首页", listOf(plain("推荐内容"), hide("广告条目", c.exampleAds), plain("推荐内容"))),
                group("顶部入口", options(c.exampleHide, c.exampleHide.choices.map { it.id to it.label })),
            ),
            "示例页：换成【宿主】真实的界面结构",
        ),
        Page(
            "misc", "增强",
            listOf(
                group("打开首页时", listOf(add("提示一条消息", m.exampleToast), value("每次 {v}", m.exampleCount))),
            ),
        ),
        Page(
            "module", "模块设置",
            listOf(
                group("【宿主】里", listOf(mark("启动时", md.showInfo, "提示已加载"), add("适配进度卡片", md.adaptCard), mark("日志", md.verboseLog, "保存详细的"))),
                group(
                    "出问题时",
                    listOf(mark("出错的功能", md.autoDisableCrashing, "自动停用"), mark("只留净化", md.safeMode, "安全模式"), mark("全部功能", md.enabled, "开着")),
                ),
                group("【模块】", listOf(hide("桌面图标", md.hideLauncherIcon), mark("根权限", md.autoRoot, "自动申请"), mark("离开时", md.autoRestartHost, "自动重开【宿主】"))),
            ),
        ),
        Page(
            "appearance", "外观",
            listOf(
                group("深浅色", choiceMarks(Settings.Appearance.themeMode)),
                row(value("强调色 {v}", Settings.Appearance.accent, "默认"), mark("动画", Settings.Appearance.reduceMotion, "减少")),
                group("界面密度", choiceMarks(Settings.Appearance.density)),
                group("界面文字", choiceMarks(Settings.Appearance.locale)),
            ),
        ),
        Page(
            "config/scope", "设置分几层",
            listOf(group("哪一层的设置生效", listOf(plain("全局"), mark("每个渠道、账号", Settings.Scope.enabled, "可以单独覆盖")))),
        ),
    )

    val ALL: List<Page> get() = PAGES

    fun byRoute(route: String): Page? = PAGES.firstOrNull { it.route == route }
}
