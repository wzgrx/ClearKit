package io.github.clearkit.starter.app.ui.pages

import androidx.compose.foundation.background
import androidx.core.content.edit
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import io.github.clearkit.starter.app.ui.components.CloseCard
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.Notice
import io.github.clearkit.starter.app.ui.components.PageGutter
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.SwitchRow
import io.github.clearkit.starter.app.ui.components.Tone
import io.github.clearkit.starter.app.ui.components.ValueRow
import io.github.clearkit.starter.model.config.ConfigDocument
import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.ConfigScope
import io.github.clearkit.starter.model.settings.Setting
import io.github.clearkit.starter.model.settings.SettingPages
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.model.settings.StringSetSetting

private val INTERNAL get() = SettingPages.INTERNAL

internal fun routeOf(key: String): String? = SettingPages.routeOf(key)

private val ROUTE_TITLES get() = SettingPages.TITLES

private fun groupLabel(key: String) = SettingPages.groupLabel(key)

/** [q] 是不是在 [text] 里（拼音见 [io.github.clearkit.starter.hook.ui.Pinyin]）。 */
internal fun matches(text: String, q: String): Boolean = io.github.clearkit.starter.hook.ui.Pinyin.matches(text, q)

/**
 * 搜索设置：按名字、说明、设置键、拼音首字母搜；结果按页面分组，开关直接在结果里改，其余点进去。
 * 筛选：全部 / 开着的 / 需重开的。搜过的词留最近 8 个。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSearchPage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val values by collect(app.settings.values)
    val prefs = remember { app.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE) }
    var history by remember { mutableStateOf(prefs.getString("q", "").orEmpty().split('\n').filter { it.isNotBlank() }) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var limit by remember { mutableIntStateOf(60) }
    fun keep(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        history = (listOf(t) + history.filter { it != t }).take(8)
        prefs.edit { putString("q", history.joinToString("\n")) }
    }
    val candidates = Settings.all.filter { it.key !in INTERNAL && !it.comingSoon }
    val q = query.trim()
    val base = candidates.filter { s -> q.isEmpty() || matches(s.title, q) || s.summary.orEmpty().contains(q, true) || s.key.contains(q, true) }
    val onCount = base.count { isOn(it, values[it.key]) }
    val restartCount = base.count { it.needsRestart }
    val results = when (filter) {
        1 -> base.filter { isOn(it, values[it.key]) }
        2 -> base.filter { it.needsRestart }
        else -> base
    }
    val c = io.github.clearkit.starter.app.ui.theme.Bc.colors
    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().background(c.ground)) {
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = PageGutter, top = 4.dp, bottom = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            io.github.clearkit.starter.app.ui.components.IconButton(io.github.clearkit.starter.app.ui.components.BcIcons.Back, "返回", { keep(query); nav.back() })
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.foundation.text.BasicTextField(
                query, { query = it; limit = 60 },
                singleLine = true,
                textStyle = io.github.clearkit.starter.app.ui.theme.Bc.type.itemTitle.copy(color = c.text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keep(query) }),
                modifier = androidx.compose.ui.Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    androidx.compose.foundation.layout.Row(
                        androidx.compose.ui.Modifier.fillMaxWidth().height(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(c.surface)
                            .border(2.dp, c.accent, androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("搜索设置，支持拼音首字母", style = io.github.clearkit.starter.app.ui.theme.Bc.type.itemTitle, color = c.textSecondary)
                            inner()
                        }
                        if (query.isNotEmpty()) io.github.clearkit.starter.app.ui.components.IconButton(io.github.clearkit.starter.app.ui.components.BcIcons.Close, "清空", { query = "" }, tint = c.textSecondary)
                    }
                },
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
        io.github.clearkit.starter.app.ui.components.FilterPills(listOf("全部 ${base.size}", "开着的 $onCount", "需重开的 $restartCount"), filter) { filter = it }
        androidx.compose.runtime.CompositionLocalProvider(io.github.clearkit.starter.app.ui.components.LocalCardFlow provides remember { io.github.clearkit.starter.app.ui.components.CardFlow() }) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 8.dp).navigationBarsPadding()) {
                if (q.isEmpty() && history.isNotEmpty() && filter == 0) {
                    Heading("最近搜索", "清空") { history = emptyList(); prefs.edit { remove("q") } }
                    androidx.compose.foundation.layout.FlowRow(
                        androidx.compose.ui.Modifier.padding(horizontal = PageGutter).padding(bottom = 12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) { history.forEach { h -> io.github.clearkit.starter.app.ui.components.FilterPill(h, false) { query = h } } }
                }
                if (results.isEmpty()) {
                    io.github.clearkit.starter.app.ui.components.EmptyState(
                        if (q.isEmpty()) "没有符合的设置" else "没找到“$q”", "换个说法试试，比如“广告”“提示”；也可以用拼音首字母", "去功能页逛逛",
                    ) { nav.back() }
                }
                val shown = results.take(limit)
                // 结果变了整块重新组合：卡片的首行、末行按新顺序重新算
                androidx.compose.runtime.key(q, filter, limit) {
                val order = io.github.clearkit.starter.model.settings.FeatureCatalog.pages.map { it.title }
                shown.groupBy { groupLabel(it.key) }.entries
                    .sortedBy { e -> order.indexOf(e.key).let { if (it < 0) order.size else it } }
                    .forEach { (title, list) ->
                    CloseCard()
                    androidx.compose.foundation.layout.Row(
                        androidx.compose.ui.Modifier.padding(start = PageGutter + 4.dp, top = 6.dp, bottom = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(title, style = io.github.clearkit.starter.app.ui.theme.Bc.type.body, color = c.text)
                        Text("  ${list.size} 项", style = io.github.clearkit.starter.app.ui.theme.Bc.type.caption, color = c.textSecondary)
                    }
                    list.forEachIndexed { i, s ->
                        val where = routeOf(s.key)?.let { ROUTE_TITLES[it] } ?: groupLabel(s.key)
                        val last = i == list.lastIndex
                        if (s is BoolSetting) {
                            io.github.clearkit.starter.app.ui.components.SwitchRow(
                                s.title, where, (values[s.key] as? Boolean) ?: s.default, divider = !last,
                                badge = if (s.needsRestart) "重开生效" else null,
                            ) { v -> keep(query); app.settings.set(s, v) }
                        } else {
                            NavRow(s.title, where, if (values[s.key] == null) null else ConfigDocument.describe(s, values[s.key]).take(12), divider = !last) {
                                keep(query)
                                nav.open(routeOf(s.key) ?: "setting?key=${s.key}")
                            }
                        }
                    }
                }
                }
                if (results.size > limit) {
                    CloseCard()
                    NavRow("再显示 ${minOf(60, results.size - limit)} 项", null, divider = false) { limit += 60 }
                }
                CloseCard()
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(24.dp))
            }
        }
    }
}

/** 功能说明（设计 004）：用途、当前值、默认值、生效时机、旧版对应、分层覆盖、所在页面。 */
@Composable
fun SettingDetailPage(route: String) {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val values by collect(app.settings.values)
    val key = routeParam(route, "key").orEmpty()
    val s = Settings.byKey(key)
    PageScaffold("功能说明", s?.let { groupLabel(it.key) } ?: "设置") {
        if (s == null) { Notice("没有这项设置：$key", Tone.WARN); return@PageScaffold }
        SectionLabel("这项功能")
        ValueRow(s.title, s.summary ?: "没有更多说明", "")
        ValueRow("当前值", if (values[s.key] == null) "没改过" else "改过", ConfigDocument.describe(s, values[s.key]))
        ValueRow("默认值", null, ConfigDocument.describe(s, s.default))
        if (s.comingSoon) Notice("这项还没接入（即将支持）：设置先保留，现在改了不会生效。", Tone.WARN)
        SectionLabel("生效与来源")
        ValueRow("生效时机", null, if (s.needsRestart) "重开【宿主】" else "立即")
        ValueRow("数据影响", "只改这项设置，不清数据，不自动联网", "")
        ValueRow("设置键", s.legacyKey?.let { "旧版：${it.substringAfterLast('&')}" }, s.key)
        val stored = app.settings.stored()
        ConfigScope.LAYERS.drop(1).forEach { (l, label) ->
            stored[ConfigScope.key(l, s.key)]?.let { ValueRow("$label 覆盖", null, ConfigDocument.describe(s, it)) }
        }
        routeOf(s.key)?.let { r -> NavRow("去设置页修改", ROUTE_TITLES[r] ?: r, "前往") { nav.open(r) } }
        NavRow("运行证据", "在诊断中心看对应功能是否生效", "查看", divider = false) { nav.open("diagnostics") }
    }
}
