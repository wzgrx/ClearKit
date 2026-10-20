package io.github.clearkit.starter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.ui.pages.boolSetting
import io.github.clearkit.starter.model.settings.BoolSetting

/** 这周某个开关起作用的次数（【宿主】按周记的 `ev.<设置键>`）。 */
@Composable
fun evidenceCount(key: String): Int {
    val stats by ModuleApp.instance.stats.stats.collectAsState()
    return stats?.totals?.get("w${io.github.clearkit.starter.app.ui.pages.weekId()}.ev.$key")?.toInt() ?: 0
}

/** 一个开关设置：标题、说明（要重启时注明）、当前值，改动立即保存并同步到【宿主】。 */
@Composable
fun SettingToggle(setting: BoolSetting, divider: Boolean = true) {
    if (inDiagram(setting.key)) return
    val on = boolSetting(setting)
    // 证据（6.0）：开着、这周真的起过作用时，说明前面写上次数
    val n = if (on) evidenceCount(setting.key) else 0
    val summary = if (n > 0) listOfNotNull("这周起作用 $n 次", setting.summary).joinToString(" · ") else setting.summary
    SwitchRow(
        setting.title, summary, on, divider, enabled = !setting.comingSoon,
        badge = when { setting.comingSoon -> "即将支持"; setting.needsRestart -> "重开生效"; else -> null },
    ) { ModuleApp.instance.settings.set(setting, it) }
}

/**
 * 多选设置（V2.1 的 chips）：标题和说明在上，下面是全部选项，点一下切换，改动立即保存。
 * 旧版迁移来的、当前目录里没有的选项不丢（只改点过的那一项）。
 */
@Composable
fun SettingChips(setting: io.github.clearkit.starter.model.settings.StringSetSetting, divider: Boolean = true) {
    if (inDiagram(setting.key)) return
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    @Suppress("UNCHECKED_CAST")
    val selected = (values[setting.key] as? Set<String>) ?: setting.default
    val options = setting.choices.map { it.id to it.label }
    val badge = when { setting.comingSoon -> "即将支持"; setting.needsRestart -> "重开生效"; else -> null }
    // 示意图里已经能点的多选（视频页模块、动态类型……）：列表里只留一行摘要，点开再改，不重复一墙小格
    val inPreview = io.github.clearkit.starter.model.preview.PagePreviews.PAGES.any { pg -> pg.binds.any { it.setting.key == setting.key } }
    if (inPreview) {
        var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val n = options.count { it.first in selected }
        PickerRow(setting.title, "在上面的示意图里点也能改", if (n == 0) "都显示" else "隐藏 $n 项", divider, badge) { open = true }
        if (open) BcSheet(setting.title, setting.summary, onDismiss = { open = false }) {
            HideTiles("点一下隐藏", null, options, selected, false, null,
                onToggle = { id -> app.settings.update(setting) { if (id in it) it - id else it + id } },
                onRestoreAll = { app.settings.update(setting) { cur -> cur - options.map { it.first }.toSet() } })
        }
        return
    }
    if (io.github.clearkit.starter.model.settings.SettingChoices.isHideSet(setting)) {
        HideTiles(setting.title, setting.summary, options, selected, divider, badge,
            onToggle = { id -> app.settings.update(setting) { if (id in it) it - id else it + id } },
            onRestoreAll = { app.settings.update(setting) { cur -> cur - options.map { it.first }.toSet() } })
        return
    }
    MultiPickerRow(setting.title, setting.summary, options, selected, divider, badge) { id ->
        app.settings.update(setting) { if (id in it) it - id else it + id }
    }
}

/** 普通多选（不是“隐藏”的）：一行显示选了几项，点开是带方框的选单。 */
@Composable
fun MultiPickerRow(
    title: String, summary: String?, options: List<Pair<String, String>>, selected: Set<String>, divider: Boolean, badge: String? = null,
    onToggle: (String) -> Unit,
) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val picked = options.filter { it.first in selected }
    val value = when {
        picked.isEmpty() -> "未选"
        picked.size <= 2 -> picked.joinToString("、") { it.second }
        else -> "已选 ${picked.size} 项"
    }
    PickerRow(title, summary, value, divider, badge) { open = true }
    if (open) {
        BcSheet(title, summary, onDismiss = { open = false }) {
            options.forEach { (id, label) -> SheetOption(label, id in selected, multi = true) { onToggle(id) } }
        }
    }
}

/**
 * 按真实目录选择要隐藏的项目（底栏、顶部标签等）：[entries] 是【宿主】报来的目录（id → 名称）。
 * 已经选了、但目录里没有的 id（旧版迁移来的，或客户端改版后不见了）单独列出，可以取消，不会悄悄丢掉。
 */
@Composable
fun SettingIdChips(
    setting: io.github.clearkit.starter.model.settings.StringSetSetting,
    entries: List<Pair<String, String>>?,
    divider: Boolean = true,
    locked: Set<String> = emptySet(),
) {
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    @Suppress("UNCHECKED_CAST")
    val selected = (values[setting.key] as? Set<String>) ?: setting.default
    val missing = selected.filter { id -> entries?.none { it.first == id } ?: false }
    val options = entries.orEmpty() + missing.mapIndexed { i, id -> id to "已不在目录里的旧选项 ${i + 1}" }
    HideTiles(
        setting.title,
        if (entries == null) "还没有目录：打开一次【宿主】，这里就会列出它的真实项目" else setting.summary,
        options, selected, divider, if (setting.needsRestart) "重开生效" else null, locked,
        onToggle = { id -> app.settings.update(setting) { if (id in it) it - id else it + id } },
        onRestoreAll = { app.settings.update(setting) { cur -> cur - options.map { it.first }.toSet() } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SettingChipsLayout(
    title: String,
    summary: String?,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    divider: Boolean,
    onToggle: (String) -> Unit,
) {
    val c = io.github.clearkit.starter.app.ui.theme.Bc.colors
    CardRow(divider) { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(horizontal = CardPadding, vertical = 12.dp)) {
        io.github.clearkit.starter.app.ui.components.Text(title, style = io.github.clearkit.starter.app.ui.theme.Bc.type.itemTitle, color = c.text)
        summary?.let {
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(4.dp))
            io.github.clearkit.starter.app.ui.components.Text(it, style = io.github.clearkit.starter.app.ui.theme.Bc.type.caption, color = c.textSecondary)
        }
        if (options.isNotEmpty()) {
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { (id, label) ->
                    BcChip(label, id in selected) { onToggle(id) }
                }
            }
        }
    }
    }
}

/** 色板：一排色块，点一个就保存（[eightDigits] 时存 FF + 六位，带不透明度）。当前颜色描深色边。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColorPalette(current: String, eightDigits: Boolean, onPick: (String) -> Unit) {
    val c = io.github.clearkit.starter.app.ui.theme.Bc.colors
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        io.github.clearkit.starter.model.settings.SettingChoices.PALETTE.forEach { (name, hex) ->
            val value = if (eightDigits) "FF$hex" else hex
            val on = current.equals(value, true) || current.takeLast(6).equals(hex, true)
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFF000000 or hex.toLong(16)))
                    .border(if (on) 3.dp else 1.dp, if (on) c.text else c.line, androidx.compose.foundation.shape.CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClickLabel = name) { onPick(value) },
            )
        }
    }
}

/**
 * 排序设置（逗号分隔的 id）：点选项按先后排进来，再点一次移出；没选的照原顺序排在后面。
 * [options] 是全部可选项（id → 名称）。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingOrder(setting: io.github.clearkit.starter.model.settings.StringSetting, options: List<Pair<String, String>>, divider: Boolean = true) {
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    val saved = (values[setting.key] as? String) ?: setting.default
    val order = saved.split(',').map { it.trim() }.filter { id -> options.any { it.first == id } }.distinct()
    CardRow(divider) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = CardPadding, vertical = 12.dp)) {
            Text(setting.title, style = io.github.clearkit.starter.app.ui.theme.Bc.type.itemTitle, color = io.github.clearkit.starter.app.ui.theme.Bc.colors.text)
            setting.summary?.let { Text(it, style = io.github.clearkit.starter.app.ui.theme.Bc.type.caption, color = io.github.clearkit.starter.app.ui.theme.Bc.colors.textSecondary) }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
            val result = order + options.map { it.first }.filterNot { it in order }
            Text(
                "现在的顺序：" + result.joinToString(" → ") { id -> options.first { it.first == id }.second },
                style = io.github.clearkit.starter.app.ui.theme.Bc.type.caption, color = io.github.clearkit.starter.app.ui.theme.Bc.colors.textSecondary,
            )
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { (id, name) ->
                    val at = order.indexOf(id)
                    BcChip(if (at >= 0) "${at + 1}. $name" else name, at >= 0) {
                        app.settings.set(setting, (if (at >= 0) order - id else order + id).joinToString(","))
                    }
                }
                if (order.isNotEmpty()) BcChip("恢复原顺序", false) { app.settings.set(setting, "") }
            }
        }
    }
}

/** 文字设置：输入框，改动立即保存；格式不对（网址、正则等）时在下面提示原因，不保存。颜色设置先给色板，有常用值时先给常用值。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingText(setting: io.github.clearkit.starter.model.settings.StringSetting, divider: Boolean = true) {
    if (inDiagram(setting.key)) return
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    val saved = (values[setting.key] as? String) ?: setting.default
    var text by androidx.compose.runtime.remember(setting.key) { androidx.compose.runtime.mutableStateOf(saved) }
    var error by androidx.compose.runtime.remember(setting.key) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val c = io.github.clearkit.starter.app.ui.theme.Bc.colors
    CardRow(divider) { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(horizontal = CardPadding, vertical = 12.dp)) {
        io.github.clearkit.starter.app.ui.components.Text(setting.title, style = io.github.clearkit.starter.app.ui.theme.Bc.type.itemTitle, color = c.text)
        setting.summary?.let {
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(4.dp))
            io.github.clearkit.starter.app.ui.components.Text(it, style = io.github.clearkit.starter.app.ui.theme.Bc.type.caption, color = c.textSecondary)
        }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
        // 颜色：点色板就行，下面的输入框只给想要别的颜色的人
        if (setting.format == io.github.clearkit.starter.model.settings.TextFormat.COLOR) {
            ColorPalette(saved, eightDigits = setting.default.length == 8 || setting.default.isEmpty()) { v -> text = v; error = null; app.settings.set(setting, v) }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
        }
        // 常用值：点一下直接填上
        if (setting.presets.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                setting.presets.forEach { o -> BcChip(o.label, saved == o.id) { text = o.id; error = null; app.settings.set(setting, o.id) } }
            }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
        }
        io.github.clearkit.starter.app.ui.components.BcTextField(
            value = text,
            label = when {
                setting.format == io.github.clearkit.starter.model.settings.TextFormat.COLOR -> ({ io.github.clearkit.starter.app.ui.components.Text("别的颜色（色值）") })
                setting.format == io.github.clearkit.starter.model.settings.TextFormat.TIME_WINDOW && setting.presets.isNotEmpty() -> ({ io.github.clearkit.starter.app.ui.components.Text("别的时段（如 22:00-07:00）") })
                setting.presets.isNotEmpty() -> ({ io.github.clearkit.starter.app.ui.components.Text("自己写") })
                else -> null
            },
            onValueChange = { v ->
                text = v
                when (val r = io.github.clearkit.starter.model.settings.SettingWrite.checkText(setting.format, v.trim())) {
                    is io.github.clearkit.starter.model.settings.SettingWrite.Result.Ok -> { error = null; app.settings.set(setting, r.value as String) }
                    is io.github.clearkit.starter.model.settings.SettingWrite.Result.Bad -> error = r.reason
                }
            },
            singleLine = true,
            visualTransformation = if (setting.format == io.github.clearkit.starter.model.settings.TextFormat.SECRET) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else androidx.compose.ui.text.input.VisualTransformation.None,
            isError = error != null,
            supportingText = error?.let { e -> { io.github.clearkit.starter.app.ui.components.Text(e) } },
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        )
    }
    }
}

/** 从真实目录里单选一项（选中的再点一次取消）；目录外的旧值单独列出。 */
@Composable
fun SettingIdChoice(setting: io.github.clearkit.starter.model.settings.StringSetting, entries: List<Pair<String, String>>?, divider: Boolean = true) {
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    val selected = (values[setting.key] as? String) ?: setting.default
    val options = listOf("" to "不选") + entries.orEmpty() +
        listOfNotNull(selected.takeIf { it.isNotEmpty() && entries?.none { e -> e.first == it } != false }?.let { it to "已不在目录里的旧选项" })
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val summary = if (entries == null) "还没有目录：打开一次【宿主】，这里就会列出它的真实项目" else setting.summary
    PickerRow(setting.title, summary, options.firstOrNull { it.first == selected }?.second ?: "不选", divider, enabled = entries != null || selected.isNotEmpty()) { open = true }
    if (open) {
        BcSheet(setting.title, summary, onDismiss = { open = false }) {
            options.forEach { (id, label) -> SheetOption(label, id == selected, multi = false) { app.settings.set(setting, id); open = false } }
        }
    }
}

/**
 * 倍速设置：按倍数输入（如 1.25），存成百分比（125）。[allowEmpty] 时留空表示跟随【宿主】（存 0）。
 * 超出范围或格式不对时提示，不保存。
 */
@Composable
fun SettingSpeed(setting: io.github.clearkit.starter.model.settings.IntSetting, allowEmpty: Boolean = false, divider: Boolean = true) {
    if (inDiagram(setting.key)) return
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    val saved = (values[setting.key] as? Int) ?: setting.default
    fun show(p: Int) = if (p == 0 && allowEmpty) "" else (p / 100.0).toString().removeSuffix(".0")
    var text by androidx.compose.runtime.remember(setting.key) { androidx.compose.runtime.mutableStateOf(show(saved)) }
    var error by androidx.compose.runtime.remember(setting.key) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val lo = maxOf(setting.range.first, 10)
    val hi = setting.range.last
    val c = io.github.clearkit.starter.app.ui.theme.Bc.colors
    CardRow(divider) { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(horizontal = CardPadding, vertical = 12.dp)) {
        io.github.clearkit.starter.app.ui.components.Text(setting.title, style = io.github.clearkit.starter.app.ui.theme.Bc.type.itemTitle, color = c.text)
        setting.summary?.let {
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(4.dp))
            io.github.clearkit.starter.app.ui.components.Text(it, style = io.github.clearkit.starter.app.ui.theme.Bc.type.caption, color = c.textSecondary)
        }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
        io.github.clearkit.starter.app.ui.components.BcTextField(
            value = text,
            onValueChange = { v ->
                text = v
                val t = v.trim().removeSuffix("x").removeSuffix("倍")
                if (t.isEmpty() && allowEmpty) { error = null; app.settings.set(setting, 0); return@BcTextField }
                val p = t.toDoubleOrNull()?.let { Math.round(it * 100).toInt() }
                error = when {
                    p == null -> "请输入倍数，例如 1.25"
                    p < lo || p > hi -> "范围 ${lo / 100.0}–${hi / 100.0} 倍"
                    else -> null
                }
                if (error == null && p != null) app.settings.set(setting, p)
            },
            singleLine = true,
            isError = error != null,
            suffix = { io.github.clearkit.starter.app.ui.components.Text("倍") },
            placeholder = if (allowEmpty) ({ io.github.clearkit.starter.app.ui.components.Text("跟随【宿主】") }) else null,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            supportingText = error?.let { e -> { io.github.clearkit.starter.app.ui.components.Text(e) } },
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        )
    }
    }
}

/** 单选设置（chips）：点哪个选哪个。 */
@Composable
fun SettingChoice(setting: io.github.clearkit.starter.model.settings.ChoiceSetting, divider: Boolean = true) {
    if (inDiagram(setting.key)) return
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    val selected = (values[setting.key] as? String) ?: setting.default
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val badge = when { setting.comingSoon -> "即将支持"; setting.needsRestart -> "重开生效"; else -> null }
    PickerRow(setting.title, setting.summary, setting.options.firstOrNull { it.id == selected }?.label ?: selected, divider, badge, enabled = !setting.comingSoon) { open = true }
    if (open) {
        BcSheet(setting.title, setting.summary, onDismiss = { open = false }) {
            setting.options.forEach { o -> SheetOption(o.label, o.id == selected, multi = false) { app.settings.set(setting, o.id); open = false } }
        }
    }
}

/** 整数设置：输入框，超出范围时提示不保存；单位写在后面。 */
@Composable
fun SettingInt(setting: io.github.clearkit.starter.model.settings.IntSetting, divider: Boolean = true) {
    if (inDiagram(setting.key)) return
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    val saved = (values[setting.key] as? Int) ?: setting.default
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val badge = when { setting.comingSoon -> "即将支持"; setting.needsRestart -> "重开生效"; else -> null }
    PickerRow(setting.title, setting.summary, "$saved ${setting.unit}".trim(), divider, badge, enabled = !setting.comingSoon) { open = true }
    if (open) BcSheet(setting.title, listOfNotNull(setting.summary, "范围 ${setting.range.first}–${if (setting.range.last == Int.MAX_VALUE) "不限" else setting.range.last}").joinToString(" · "), onDismiss = { open = false }) { IntEditor(setting) }
}

/** 数值的编辑：输入框（超出范围提示、不保存）和常用值。放在底部选单里。 */
@Composable
private fun IntEditor(setting: io.github.clearkit.starter.model.settings.IntSetting) {
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    val saved = (values[setting.key] as? Int) ?: setting.default
    var text by androidx.compose.runtime.remember(setting.key) { androidx.compose.runtime.mutableStateOf(saved.toString()) }
    var error by androidx.compose.runtime.remember(setting.key) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(horizontal = CardPadding, vertical = 12.dp)) {
        io.github.clearkit.starter.app.ui.components.BcTextField(
            value = text,
            onValueChange = { v ->
                text = v
                val n = v.trim().toIntOrNull()
                error = when {
                    n == null -> "请输入整数"
                    n !in setting.range -> "范围 ${setting.range.first}–${setting.range.last}"
                    else -> null
                }
                if (error == null && n != null) app.settings.set(setting, n)
            },
            singleLine = true,
            isError = error != null,
            suffix = { io.github.clearkit.starter.app.ui.components.Text(setting.unit) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            supportingText = error?.let { e -> { io.github.clearkit.starter.app.ui.components.Text(e) } },
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        )
        // 常用值：点一下直接填上
        if (setting.presets.isNotEmpty()) {
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                setting.presets.forEach { v ->
                    BcChip("$v ${setting.unit}".trim(), error == null && v == saved, onClick = { text = v.toString(); error = null; app.settings.set(setting, v) })
                }
            }
        }
    }
}

/** 不存设置的单选 chips（预览页这类本地输入用）。 */
@Composable
fun ChipsPicker(title: String, options: List<Pair<String, String>>, selected: Set<String>, onPick: (String) -> Unit) =
    SettingChipsLayout(title, null, options, selected, true, onPick)

/** 不存设置的数字输入框。 */
@Composable
fun PlainNumberField(label: String, value: String, onChange: (String) -> Unit) {
    io.github.clearkit.starter.app.ui.components.BcTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { io.github.clearkit.starter.app.ui.components.Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 6.dp),
    )
}
