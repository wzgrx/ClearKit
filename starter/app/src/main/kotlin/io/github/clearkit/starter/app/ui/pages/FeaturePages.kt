package io.github.clearkit.starter.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.data.HostControl
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.BcIcons
import io.github.clearkit.starter.app.ui.components.CardPadding
import io.github.clearkit.starter.app.ui.components.CardRow
import io.github.clearkit.starter.app.ui.components.CloseCard
import io.github.clearkit.starter.app.ui.components.HueTile
import io.github.clearkit.starter.app.ui.components.IconButton
import io.github.clearkit.starter.app.ui.components.LocalCardFlow
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.PageGutter
import io.github.clearkit.starter.app.ui.components.PillButton
import io.github.clearkit.starter.app.ui.components.PillStyle
import io.github.clearkit.starter.app.ui.components.Segmented
import io.github.clearkit.starter.app.ui.components.SettingChips
import io.github.clearkit.starter.app.ui.components.SettingChoice
import io.github.clearkit.starter.app.ui.components.SettingInt
import io.github.clearkit.starter.app.ui.components.SettingSpeed
import io.github.clearkit.starter.app.ui.components.SettingText
import io.github.clearkit.starter.app.ui.components.SettingToggle
import io.github.clearkit.starter.app.ui.components.SwitchRow
import io.github.clearkit.starter.app.ui.components.ValueRow
import io.github.clearkit.starter.app.ui.theme.Bc
import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.ChoiceSetting
import io.github.clearkit.starter.model.settings.IntSetting
import io.github.clearkit.starter.model.settings.Setting
import io.github.clearkit.starter.model.settings.FeatureCatalog
import io.github.clearkit.starter.model.settings.SettingPages
import io.github.clearkit.starter.model.settings.StringSetSetting
import io.github.clearkit.starter.model.settings.StringSetting
import kotlinx.coroutines.launch

/** 按类型给一项设置配控件（开关、单选、数字、多选、文字）。新类型的设置在这里加一行。 */
@Composable
fun SettingControl(s: Setting<*>, divider: Boolean = true) = when (s) {
    is BoolSetting -> SettingToggle(s, divider)
    is ChoiceSetting -> SettingChoice(s, divider)
    is IntSetting -> SettingInt(s, divider)
    is StringSetSetting -> SettingChips(s, divider)
    is StringSetting -> SettingText(s, divider)
    else -> ValueRow(s.title, s.summary, "", divider)
}

/** 示意图里点不能直接切换的块、或长按任何块时弹出的编辑：这一项的完整控件（不受“示意图”视图收起影响）。 */
@Composable
fun SettingEditor(s: Setting<*>) {
    CompositionLocalProvider(
        io.github.clearkit.starter.app.ui.components.LocalDiagramKeys provides emptySet(),
        io.github.clearkit.starter.app.ui.components.LocalCardFlow provides androidx.compose.runtime.remember { io.github.clearkit.starter.app.ui.components.CardFlow() },
    ) {
        SettingControl(s, divider = false)
        CloseCard()
    }
}

/** 一项设置算不算“开着”：开关看真假，多选看有没有选，其它看是不是改过默认值。 */
fun isOn(s: Setting<*>, value: Any?): Boolean = when (s) {
    is BoolSetting -> (value as? Boolean ?: s.default) == true
    is StringSetSetting -> (value as? Set<*>)?.isNotEmpty() == true
    else -> value != null && value != s.default
}

/** 一页（[FeatureCatalog.Page]）里开着几项、共几项。 */
fun countOn(page: FeatureCatalog.Page, values: Map<String, Any?>): Pair<Int, Int> {
    val all = page.group.all.filter { it.key !in SettingPages.INTERNAL }
    return all.count { isOn(it, values[it.key]) } to all.size
}

/** 每页的图标：按路由配，新分组在这里加一行（图标见 BcIcons）。 */
private fun iconOf(route: String) = when (route) {
    "cleanup" -> BcIcons.AdOff
    "misc" -> BcIcons.Wrench
    else -> BcIcons.Gear
}

/** “功能”页签：搜索框 + 功能目录（[FeatureCatalog.pages]）一页一行，再加模块自己的设置。 */
@Composable
fun FeaturesPage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val values by collect(app.settings.values)
    val pages = FeatureCatalog.pages
    val on = pages.sumOf { countOn(it, values).first }
    val total = pages.sumOf { countOn(it, values).second }
    PageScaffold("功能", "共 $total 项 · 已开 $on 项", back = false) {
        SearchField { nav.open("search") }
        GroupHeading("功能")
        pages.forEachIndexed { i, p ->
            val (n, t) = countOn(p, values)
            PageRow(p.title, p.summary, iconOf(p.route), n, t, divider = i < pages.lastIndex) { nav.open(p.route) }
        }
        GroupHeading("模块")
        NavRow("模块设置", "主开关、安全模式、提示、日志", divider = true) { nav.open("module") }
        NavRow("外观", "深浅色、动画", divider = false) { nav.open("appearance") }
    }
}

/**
 * 一页功能（按设置分组自动生成，不用为每页写界面）：顶上示意图（PagePreviews 里同名的页），
 * 下面按顺序列出这一组的设置；示意图里已经能改的，“示意图”视图下不在列表里重复。
 */
@Composable
fun FeaturePage(route: String) {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val values by collect(app.settings.values)
    val pending by collect(app.settings.pendingRestart)
    val page = FeatureCatalog.byRoute(route) ?: run { FeaturesPage(); return }
    val (on, total) = countOn(page, values)
    PageScaffold(
        page.title, "已开 $on 项 · 共 $total 项",
        actions = { IconButton(BcIcons.Search, "搜索设置", { nav.open("search") }) },
        bottom = if (pending.isNotEmpty()) ({ RestartBar(pending.size) }) else null,
    ) {
        io.github.clearkit.starter.app.ui.components.PagePreviewCard(route, divider = true)
        CloseCard()
        val rows = page.group.all.filter { it.key !in SettingPages.INTERNAL && !io.github.clearkit.starter.app.ui.components.inDiagram(it.key) }
        rows.forEachIndexed { i, s -> SettingControl(s, divider = i < rows.lastIndex) }
        if (rows.isEmpty()) io.github.clearkit.starter.app.ui.components.Notice("这一页的设置都在上面的示意图里：点块改，长按看说明。切到“列表”能看全部。")
    }
}

/** 场景页上方的大分组标题（卡片外）。 */
@Composable
fun GroupHeading(text: String) {
    CloseCard()
    Text(text, style = Bc.type.sectionLabel, color = Bc.colors.textSecondary, modifier = Modifier.padding(start = PageGutter + 4.dp, end = PageGutter, top = 12.dp, bottom = 8.dp))
}

/** 假的搜索框：点一下进搜索页。 */
@Composable
fun SearchField(hint: String = "搜索全部设置，支持拼音首字母", onClick: () -> Unit) {
    val c = Bc.colors
    Row(
        Modifier.padding(horizontal = PageGutter).padding(bottom = 4.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.surface)
            .clickable(role = Role.Button, onClickLabel = "搜索设置", onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BcIcons.Search, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(hint, style = Bc.type.body, color = c.textSecondary)
    }
}

@Composable
private fun PageRow(title: String, summary: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Int, total: Int, divider: Boolean, onClick: () -> Unit) {
    val c = Bc.colors
    CardRow(divider) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = CardPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HueTile(icon, io.github.clearkit.starter.app.ui.theme.Hue.HOME, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Bc.type.itemTitle, color = c.text)
                Text(summary, style = Bc.type.caption, color = c.textSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            // “已开 / 共几项”放一行，开了的用强调色
            Text(
                "$on/$total", style = Bc.type.caption, fontWeight = FontWeight.Medium,
                color = if (on > 0) c.accentInk else c.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on > 0) c.accentSoft else c.muted).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** 底部浮着的“重开生效”条（深色反相），一键重开【宿主】。 */
@Composable
fun RestartBar(count: Int) {
    val app = ModuleApp.instance
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val c = Bc.colors
    Row(
        Modifier.padding(horizontal = PageGutter, vertical = 8.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)).background(c.text).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(BcIcons.Restart, contentDescription = null, tint = c.ground, modifier = Modifier.size(18.dp))
        Text("$count 项改动重开【宿主】后生效", style = Bc.type.body, color = c.ground, modifier = Modifier.weight(1f))
        val inverse = if (c.isDark) io.github.clearkit.starter.app.ui.theme.LightColors else io.github.clearkit.starter.app.ui.theme.DarkColors
        PillButton("立即重开", {
            val pkg = app.status.status.value?.hostPackage ?: HostControl.installed(context).firstOrNull()?.packageName ?: return@PillButton
            scope.launch {
                if (HostControl.restart(context, pkg)) app.settings.clearPendingRestart()
                else android.widget.Toast.makeText(context, "在应用信息里点“强行停止”，再打开【宿主】，改动就会生效", android.widget.Toast.LENGTH_LONG).show()
            }
        }, style = PillStyle.Custom(inverse.accent, inverse.onAccent), height = 40.dp)
    }
}

