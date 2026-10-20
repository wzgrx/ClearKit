package io.github.clearkit.starter.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.BottomActions
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.Notice
import io.github.clearkit.starter.app.ui.components.PageGutter
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.SettingChoice
import io.github.clearkit.starter.app.ui.components.SettingToggle
import io.github.clearkit.starter.app.ui.components.Tone
import io.github.clearkit.starter.app.ui.components.ValueRow
import io.github.clearkit.starter.app.ui.theme.Bc
import io.github.clearkit.starter.app.ui.theme.LightColors
import io.github.clearkit.starter.app.ui.theme.accentColor
import io.github.clearkit.starter.model.config.ConfigDocument
import io.github.clearkit.starter.model.settings.Settings

private val ap = Settings.Appearance

/**
 * 外观与主题（设计 022、060）：主题方式、强调色（预设色块 + 自定义色值，实时预览）、界面密度、减少动效、模块语言、
 * 恢复默认外观（先列出会改的项）。
 */
@Composable
fun AppearancePage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val values by collect(app.settings.values)
    val saved = values[ap.accent.key] as? String ?: ""
    var hex by remember { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    var resetting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    val changed = ap.all.filter { values[it.key] != null }

    fun pick(v: String) {
        hex = v
        if (v.isBlank()) { app.settings.reset(ap.accent); error = null; return }
        if (accentColor(v) == null) { error = "写成六位色值，例如 1E88E5"; return }
        error = null
        app.settings.set(ap.accent, v.trim().removePrefix("#").uppercase())
    }

    PageScaffold(
        "外观与主题", "只改【模块】自己的界面",
        bottom = if (!resetting) null else ({
            BottomActions("取消", { resetting = false }, "恢复 ${changed.size} 项") {
                app.settings.atomically { changed.forEach { reset(it) } }
                hex = ""; resetting = false; message = "已恢复默认外观" to Tone.OK
            }
        }),
    ) {
        io.github.clearkit.starter.app.ui.components.PagePreviewCard("appearance")
        io.github.clearkit.starter.app.ui.components.CloseCard()
        message?.let { (t, tone) -> Notice(t, tone) }
        if (resetting) {
            SectionLabel("会恢复的项")
            changed.forEachIndexed { i, s -> ValueRow(s.title, "${ConfigDocument.describe(s, values[s.key])} → ${ConfigDocument.describe(s, null)}", "", divider = i < changed.lastIndex) }
            return@PageScaffold
        }
        SectionLabel("主题")
        SettingChoice(ap.themeMode)
        SectionLabel("强调色")
        io.github.clearkit.starter.app.ui.components.InCard(last = true) {
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ap.accent.presets.forEach { o ->
                val color = accentColor(o.id) ?: LightColors.accent
                val selected = o.id.equals(saved, ignoreCase = true)
                Box(
                    Modifier.size(40.dp).background(color, CircleShape)
                        .border(if (selected) 3.dp else 1.dp, if (selected) Bc.colors.text else Bc.colors.line, CircleShape)
                        .clickable { pick(o.id) }
                        .semantics { contentDescription = o.label; this.selected = selected },
                )
            }
        }
        io.github.clearkit.starter.app.ui.components.BcTextField(
            hex, ::pick, label = { Text("自定义色值（六位色值，留空用默认）") }, singleLine = true, isError = error != null,
            supportingText = error?.let { e -> { Text(e) } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Bc.colors.accentSoft, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("预览：开关、按钮和选中项用这个颜色", style = Bc.type.body, color = Bc.colors.accentInk)
        }
        }
        SectionLabel("可读性")
        SettingChoice(ap.density)
        SettingToggle(ap.reduceMotion)
        ValueRow("文字大小", "跟随系统的字体大小设置；固定底栏不会被大字体挡住", "跟随系统", divider = false)
        SectionLabel("语言与恢复")
        SettingChoice(ap.locale)
        NavRow("恢复默认外观", if (changed.isEmpty()) "现在就是默认外观" else "会恢复 ${changed.size} 项，先列出再确认", enabled = changed.isNotEmpty(), divider = false) { resetting = true }
    }
}
