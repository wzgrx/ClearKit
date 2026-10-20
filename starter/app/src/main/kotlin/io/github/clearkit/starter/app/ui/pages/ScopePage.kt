package io.github.clearkit.starter.app.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.BottomActions
import io.github.clearkit.starter.app.ui.components.ListRow
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.Notice
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.SettingChoice
import io.github.clearkit.starter.app.ui.components.SettingToggle
import io.github.clearkit.starter.app.ui.components.Tone
import io.github.clearkit.starter.app.ui.components.ValueRow
import io.github.clearkit.starter.model.config.ConfigDocument
import io.github.clearkit.starter.model.settings.ConfigScope
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.material3.TextButton

/**
 * 配置作用域（设计 026、084）：各个渠道各自覆盖一部分设置。打开并选一层后，所有设置页改的都是这一层；
 * 这里列出每层覆盖了什么，可以逐项清除（回到全局值）或清空整层。模块、作用域、外观只有全局值。
 */
@Composable
fun ScopePage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val values by collect(app.settings.values)
    val stored = remember(values) { app.settings.stored() }
    val enabled = stored[Settings.Scope.enabled.key] == true
    val editing = app.settings.editingLayer
    var clearing by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    PageScaffold(
        "配置作用域", "全局、客户端和账号各自的设置",
        bottom = clearing?.let { layer ->
            {
                BottomActions("取消", { clearing = null }, "清空${ConfigScope.label(layer)}的覆盖") {
                    app.settings.clearLayer(layer); clearing = null; message = "已清空${ConfigScope.label(layer)}的覆盖，都用全局值" to Tone.OK
                }
            }
        },
    ) {
        io.github.clearkit.starter.app.ui.components.PagePreviewCard("config/scope")
        io.github.clearkit.starter.app.ui.components.CloseCard()
        message?.let { (t, tone) -> Notice(t, tone) }
        SectionLabel("作用域")
        SettingToggle(Settings.Scope.enabled)
        @Suppress("UNCHECKED_CAST")
        val accountLayers = (((stored[Settings.Scope.accounts.key] as? Set<String>).orEmpty().mapNotNull { it.toLongOrNull() }.map(ConfigScope::accountLayer)) +
            ConfigScope.accountLayers(stored)).distinct()
        val layers = ConfigScope.LAYERS + accountLayers.map { it to ConfigScope.label(it) }
        if (enabled) {
            io.github.clearkit.starter.app.ui.components.SettingChipsLayout(
                "现在改的是", "选一层后，各设置页改的只是这一层的值；账号层在【宿主】里按当前登录的账号生效",
                layers, setOf(editing ?: ConfigScope.GLOBAL), divider = false,
            ) { id -> app.settings.set(Settings.Scope.edit, id) }
        }
        Notice(
            if (!enabled) "关闭时两个客户端都用全局值；已有的覆盖保留，再打开就恢复。"
            else if (editing == null) "现在改的是全局值：两个客户端都会跟着变（除非那一层覆盖了这一项）。"
            else "现在各设置页改的只是${ConfigScope.label(editing)}的值；恢复默认 = 回到全局值。改完记得切回全局。",
            if (editing != null) Tone.WARN else Tone.INFO,
        )
        for ((layer, label) in layers.drop(1)) {
            val ov = ConfigScope.overrides(stored, layer)
            SectionLabel("$label 覆盖（${ov.size} 项）")
            if (ov.isEmpty()) { ValueRow("没有覆盖", "全部用全局值", ""); continue }
            ov.entries.sortedBy { it.key }.forEachIndexed { i, (key, v) ->
                val s = Settings.byKey(key)
                ListRow(
                    s?.title ?: key,
                    s?.let { "覆盖为 ${ConfigDocument.describe(it, v)}；全局是 ${ConfigDocument.describe(it, stored[key])}" } ?: "不认识的设置",
                    divider = i < ov.size - 1, onClick = null,
                ) {
                    TextButton(onClick = {
                        app.settings.atomically {
                            val prevEnabled = stored[Settings.Scope.enabled.key] == true
                            val prevEdit = stored[Settings.Scope.edit.key] as? String ?: ConfigScope.GLOBAL
                            // 临时切到这一层再恢复默认（= 删掉覆盖键），然后切回原来的层
                            setAll(Settings.Scope.enabled to true, Settings.Scope.edit to layer)
                            s?.let { reset(it) }
                            setAll(Settings.Scope.enabled to prevEnabled, Settings.Scope.edit to prevEdit)
                        }
                    }) { Text("清除") }
                }
            }
            NavRow("清空$label 的覆盖", "全部回到全局值，先确认", divider = false) { clearing = layer }
        }
        SectionLabel("说明")
        ValueRow("运行时用哪一层", "先看当前登录账号的覆盖，再看客户端的覆盖，都没有就用全局值", "")
        ValueRow("账号从哪来", "打开【宿主】登录后会记下账号的用户编号（只记用户编号）；没登录时不用账号层", "")
        ValueRow("只有全局值的", "模块设置、作用域本身、外观与主题、一次性请求", "")
        NavRow("导出、导入完整配置", "在“配置与备份”里；导出的是当前编辑层实际生效的值", divider = false) { nav.open("config") }
    }
}
