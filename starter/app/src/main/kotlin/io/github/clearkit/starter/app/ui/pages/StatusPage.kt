package io.github.clearkit.starter.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.BcIcons
import io.github.clearkit.starter.app.ui.components.CardPadding
import io.github.clearkit.starter.app.ui.components.CardRow
import io.github.clearkit.starter.app.ui.components.CloseCard
import io.github.clearkit.starter.app.ui.components.FilterPills
import io.github.clearkit.starter.app.ui.components.IconButton
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.Notice
import io.github.clearkit.starter.app.ui.components.PageGutter
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.Segmented
import io.github.clearkit.starter.app.ui.components.SwitchRow
import io.github.clearkit.starter.app.ui.theme.Bc
import io.github.clearkit.starter.model.HostBuild
import io.github.clearkit.starter.model.bridge.FeatureStatus
import io.github.clearkit.starter.model.bridge.FeatureStatus.State
import io.github.clearkit.starter.model.settings.Settings

/** 功能状态的文字和颜色。 */
@Composable
private fun stateLabel(s: State): Pair<String, Color> {
    val c = Bc.colors
    return when (s) {
        State.ACTIVE -> "生效" to c.ok
        State.OFF -> "未开" to c.textSecondary
        State.OBSERVING -> "观察中" to c.warn
        State.SAFE_MODE_OFF -> "安全模式暂停" to c.warn
        State.UNAVAILABLE -> "跳过" to c.textSecondary
        State.FAILED -> "出错" to c.danger
        State.CRASH_DISABLED -> "崩溃后停用" to c.danger
    }
}

/**
 * 功能运行情况：按客户端看【宿主】最近一次报来的每个功能的状态。
 * 顶部三个数（生效、失败、跳过），按状态筛选；底下是崩溃保护。
 */
@Composable
fun StatusPage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val all by collect(app.status.all)
    val hosts = all.toSortedMap().entries.toList()
    var host by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val current = hosts.getOrNull(host.coerceIn(0, (hosts.size - 1).coerceAtLeast(0)))?.value
    val at = current?.let { java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(it.reportedAt)) }
    PageScaffold(
        "功能运行情况", at?.let { "上次报告 $it · 模块 ${current.moduleVersion}" } ?: "【宿主】里各功能是否生效",
        actions = { IconButton(BcIcons.Doc, "诊断中心", { nav.open("diagnostics") }) },
    ) {
        if (hosts.isEmpty()) {
            Notice("还没收到【宿主】的报告。启用模块后打开一次【宿主】，这里就会显示各功能的状态。")
        } else {
            if (hosts.size > 1) {
                Segmented(
                    hosts.map { (pkg, s) -> "${HostBuild.channelOf(pkg)?.label ?: pkg} ${s.hostVersionName}" }, host.coerceIn(0, hosts.lastIndex),
                    Modifier.padding(bottom = 12.dp),
                ) { host = it; filter = 0 }
            }
            val features = current?.features.orEmpty()
            val ok = features.count { it.state == State.ACTIVE }
            val bad = features.count { it.state == State.FAILED || it.state == State.CRASH_DISABLED }
            val skipped = features.count { it.state == State.UNAVAILABLE || it.state == State.SAFE_MODE_OFF || it.state == State.OBSERVING }
            CloseCard()
            Row(Modifier.padding(horizontal = PageGutter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("$ok", "生效", Bc.colors.ok), Triple("$bad", "失败", if (bad > 0) Bc.colors.danger else Bc.colors.text), Triple("$skipped", "跳过", Bc.colors.text)).forEach { (n, l, color) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Bc.colors.surface).padding(12.dp)) {
                        Text(n, style = Bc.type.cardTitle.copy(fontSize = Bc.type.cardTitle.fontSize * 1.3f), color = color)
                        Text(l, style = Bc.type.caption, color = Bc.colors.textSecondary)
                    }
                }
            }
            val off = features.count { it.state == State.OFF }
            FilterPills(listOf("全部 ${features.size}", "生效 $ok", "跳过 $skipped", "失败 $bad", "未开 $off"), filter) { filter = it }
            Spacer(Modifier.height(8.dp))
            val shown = features.filter { f ->
                when (filter) {
                    1 -> f.state == State.ACTIVE
                    2 -> f.state == State.UNAVAILABLE || f.state == State.SAFE_MODE_OFF || f.state == State.OBSERVING
                    3 -> f.state == State.FAILED || f.state == State.CRASH_DISABLED
                    4 -> f.state == State.OFF
                    else -> true
                }
            }.sortedBy { f -> listOf(State.FAILED, State.CRASH_DISABLED, State.ACTIVE, State.OBSERVING, State.SAFE_MODE_OFF, State.UNAVAILABLE, State.OFF).indexOf(f.state) }
            androidx.compose.runtime.key(host, filter, shown.size) {
                if (shown.isEmpty()) Notice("这一类没有功能")
                shown.forEachIndexed { i, f -> FeatureRow(f, divider = i < shown.lastIndex) }
            }
        }
        SectionLabel("崩溃保护")
        SwitchRow(Settings.Module.autoDisableCrashing.title, Settings.Module.autoDisableCrashing.summary, boolSetting(Settings.Module.autoDisableCrashing)) {
            app.settings.set(Settings.Module.autoDisableCrashing, it)
        }
        NavRow("重新启用崩溃停用的功能", "清掉崩溃记录，下次打开【宿主】再试", "重新启用", divider = false) {
            app.settings.update(Settings.Module.crashResetToken) { it + 1 }
        }
    }
}

/** 一个功能：状态点、名称、原因、状态字。 */
@Composable
private fun FeatureRow(f: FeatureStatus, divider: Boolean) {
    val (label, color) = stateLabel(f.state)
    CardRow(divider) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = CardPadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.title, style = Bc.type.itemTitle, color = Bc.colors.text)
                if (!f.detail.isNullOrBlank()) Text(f.detail.orEmpty(), style = Bc.type.caption, color = Bc.colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = Bc.type.caption, fontWeight = FontWeight.Medium, color = color)
        }
    }
}
