package io.github.clearkit.starter.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.BuildConfig
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.data.ChangeJournal
import io.github.clearkit.starter.app.data.HostControl
import io.github.clearkit.starter.app.data.ModuleService
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.BcIcons
import io.github.clearkit.starter.app.ui.components.CardPadding
import io.github.clearkit.starter.app.ui.components.CardRow
import io.github.clearkit.starter.app.ui.components.CloseCard
import io.github.clearkit.starter.app.ui.components.HueTile
import io.github.clearkit.starter.app.ui.components.IconButton
import io.github.clearkit.starter.app.ui.components.Notice
import io.github.clearkit.starter.app.ui.components.PageGutter
import io.github.clearkit.starter.app.ui.components.PillButton
import io.github.clearkit.starter.app.ui.components.PillStyle
import io.github.clearkit.starter.app.ui.components.Tone
import io.github.clearkit.starter.app.ui.theme.Bc
import io.github.clearkit.starter.app.ui.theme.Hue
import io.github.clearkit.starter.model.Impact
import io.github.clearkit.starter.model.bridge.FeatureStatus
import io.github.clearkit.starter.model.config.ConfigDocument
import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.Settings
import kotlinx.coroutines.launch

/** 首页：模块状态和这周的效果、待重开、常用开关、最近改动。 */
@Composable
fun HomePage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val pending by collect(app.settings.pendingRestart)
    val values by collect(app.settings.values)
    val changes by collect(app.journal.changes)
    val module by collect(ModuleService.state)
    val reports by collect(app.status.all)
    val subtitle = BuildConfig.VERSION_NAME + when {
        module is ModuleService.State.Active -> " · 模块已启用"
        module == ModuleService.State.Connecting -> " · 正在连接"
        patchedRecently(reports.values) -> " · 免根权限模式"
        else -> " · 模块未启用"
    }
    PageScaffold("【模块】", subtitle, back = false, actions = { IconButton(BcIcons.Search, "搜索设置", { nav.open("search") }, filled = true) }) {
        StatusHero()
        if (pending.isNotEmpty()) RestartNotice()
        Heading("常用开关", "编辑") { nav.open("quick") }
        QuickToggles(values)
        val recent = changes.take(3)
        if (recent.isNotEmpty()) {
            Heading("最近改动", "全部") { nav.open("changes") }
            androidx.compose.runtime.key(recent) { recent.forEachIndexed { i, c -> ChangeRow(c, divider = i < recent.lastIndex) } }
        }
    }
}

/**
 * 免 Root 加载（LSPatch / NPatch 修补的【宿主】）：没有 LSPosed 服务，但【宿主】最近 3 天报过运行状态、
 * 而且报告说读不到远程设置。这时设置经桥接从 App 拉取，模块照常工作。
 */
fun patchedRecently(reports: Collection<io.github.clearkit.starter.model.bridge.RuntimeStatus>): Boolean =
    reports.any { !it.remoteSettings && System.currentTimeMillis() - it.reportedAt < 3 * 86_400_000L }

/** 卡片外的区块标题，右边一个文字动作。 */
@Composable
fun Heading(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    CloseCard()
    Row(Modifier.fillMaxWidth().padding(start = PageGutter + 4.dp, end = PageGutter - 6.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Bc.type.blockTitle, color = Bc.colors.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) PillButton(action, onAction, style = PillStyle.Text, height = 40.dp)
    }
}

/** 这周的效果计数（【宿主】报来的 `w<年周>.<种类>`）。 */
private data class Week(val hidden: Long)

/** 年 × 100 + ISO 周数（和【宿主】里的 HostCounters.weekId 一样）。 */
internal fun weekId(millis: Long = System.currentTimeMillis()): Int {
    val d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val wf = java.time.temporal.WeekFields.ISO
    return d.get(wf.weekBasedYear()) * 100 + d.get(wf.weekOfWeekBasedYear())
}

private fun week(totals: Map<String, Long>): Week {
    val p = "w${weekId(System.currentTimeMillis())}."
    fun n(k: String) = totals["$p$k"] ?: 0
    return Week(n(Impact.CARD))
}

/** 首页状态卡：一切正常时是强调色大卡（这周挡下多少），出问题时换成对应颜色和下一步。 */
@Composable
private fun StatusHero() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val c = Bc.colors
    val module by collect(ModuleService.state)
    val hosts by collect(app.hostAdapter.statuses)
    val reports by collect(app.status.all)
    val stats by collect(app.stats.stats)
    val values by collect(app.settings.values)
    val installed = hosts.values.filter { it.installed }
    val safe = (values[Settings.Module.safeMode.key] as? Boolean) == true || reports.values.any { it.safeMode }
    val failed = reports.values.flatMap { r -> r.features.filter { it.state == FeatureStatus.State.FAILED || it.state == FeatureStatus.State.CRASH_DISABLED } }
    CloseCard()
    val patched = module is ModuleService.State.NotActive && patchedRecently(reports.values)
    when {
        module is ModuleService.State.NotActive && !patched -> ProblemCard(
            Tone.INFO, "模块没有启用", "在 LSPosed 里启用后才会生效", "勾选【宿主】，然后重开【宿主】。没有根权限时可以用 LSPatch / NPatch 修补【宿主】并加载本模块，打开一次后这里会显示免根权限模式。", "怎么启用",
        ) { nav.open("status") }
        module is ModuleService.State.Connecting -> ProblemCard(Tone.INFO, "正在连接", "正在等 LSPosed 送来模块服务", "一般一两秒；一直这样就重开一次【模块】。", null) {}
        installed.isEmpty() -> ProblemCard(Tone.INFO, "没有找到【宿主】", "装好【宿主】后回到这里", "装好【宿主】后回到这里。", null) {}
        safe -> ProblemCard(Tone.DANGER, "安全模式", "只保留净化类功能，其它已暂停", "排查完问题后在“模块设置”里关掉安全模式。", "模块设置") { nav.open("module") }
        installed.any { !it.ready } -> ProblemCard(
            Tone.WARN, "需要适配", installed.filter { !it.ready }.joinToString("、") { "${it.channel?.label ?: it.packageName} ${it.versionName}" } + " 还没适配好",
            "打开一次【宿主】会自动适配；也可以在这里手动开始。", "去适配",
        ) { nav.open("adapt") }
        reports.values.any { it.report?.restart?.isNotEmpty() == true } -> {
            val r = reports.values.first { it.report?.restart?.isNotEmpty() == true }
            val names = r.report!!.restart
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            ProblemCard(
                Tone.WARN, "待重开", "重开【宿主】后 ${names.size} 项生效", names.take(4).joinToString("、") + if (names.size > 4) " 等" else "", "立即重开",
            ) { scope.launch { io.github.clearkit.starter.app.data.HostControl.restart(context, r.hostPackage) } }
        }
        reports.values.any { it.report?.unavailable?.isNotEmpty() == true } -> {
            val issues = reports.values.flatMap { it.report?.unavailable.orEmpty() }
            ProblemCard(
                Tone.WARN, "这一版有功能用不了", "${issues.size} 项用不了，其余正常", issues.take(3).joinToString("、") { it.title } + if (issues.size > 3) " 等" else "", "看是哪些",
            ) { nav.open("adapt") }
        }
        failed.isNotEmpty() -> ProblemCard(
            Tone.WARN, "部分功能没生效", "${failed.size} 项没生效，其余正常", failed.take(3).joinToString("、") { it.title } + if (failed.size > 3) " 等" else "", "看是哪些",
        ) { nav.open("status") }
        else -> {
            val w = week(stats?.totals.orEmpty())
            Column(
                Modifier.padding(horizontal = PageGutter).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.hero)
                    .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (c.isDark) c.accent else Color(0xFF9FF0D9)))
                    Spacer(Modifier.width(8.dp))
                    Text("一切正常", style = Bc.type.body, fontWeight = FontWeight.Medium, color = c.onHero, modifier = Modifier.weight(1f))
                    PillButton("诊断", { nav.open("status") }, style = PillStyle.Custom(Color.Transparent, c.onHeroSecondary), icon = null, height = 40.dp)
                }
                if (w.hidden > 0) {
                    Text("这周帮你挡下", style = Bc.type.body, color = c.onHeroSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%,d".format(w.hidden), style = Bc.type.heroNumber, color = c.onHero)
                        Spacer(Modifier.width(6.dp))
                        Text("条广告和不想看的内容", style = Bc.type.body, color = c.onHeroSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                } else {
                    val active = reports.values.sumOf { r -> r.features.count { it.state == FeatureStatus.State.ACTIVE } }
                    if (reports.isEmpty()) {
                        Text("模块已就绪", style = Bc.type.cardTitle, color = c.onHero, modifier = Modifier.padding(top = 4.dp))
                        Text("打开一次【宿主】，这里会显示生效的功能，用一阵后还会显示这周帮你挡下了多少", style = Bc.type.caption, color = c.onHeroSecondary)
                    } else Row(verticalAlignment = Alignment.Bottom) {
                        Text("$active", style = Bc.type.heroNumber, color = c.onHero)
                        Spacer(Modifier.width(6.dp))
                        Text("项功能正在生效", style = Bc.type.body, color = c.onHeroSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (reports.isNotEmpty()) Text("用一阵【宿主】后，这里会显示这周帮你挡下了多少", style = Bc.type.caption, color = c.onHeroSecondary)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    installed.forEach { h ->
                        val r = reports[h.packageName]
                        val n = r?.features?.count { it.state == FeatureStatus.State.ACTIVE }
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = if (c.isDark) 0.07f else 0.13f))
                                .clickable(role = Role.Button) { nav.open("adapt") }.padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text("${h.channel?.label ?: h.packageName} ${h.versionName}", style = Bc.type.caption, color = c.onHeroSecondary, maxLines = 1)
                            Text(if (n != null) "$n 项生效" else "已适配", style = Bc.type.body, fontWeight = FontWeight.Medium, color = c.onHero)
                        }
                    }
                }
            }
        }
    }
}

/** 出问题时的状态卡：同一个位置，按语气换颜色，带下一步。 */
@Composable
private fun ProblemCard(tone: Tone, label: String, title: String, body: String, action: String?, onAction: () -> Unit) {
    val c = Bc.colors
    val (bg, fg, dot) = when (tone) {
        Tone.WARN -> Triple(c.warnSoft, c.onWarnSoft, c.warn)
        Tone.DANGER -> Triple(c.dangerSoft, c.onDangerSoft, c.danger)
        else -> Triple(c.surface, c.text, c.outline)
    }
    Column(
        Modifier.padding(horizontal = PageGutter).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg)
            .then(if (tone == Tone.INFO) Modifier.border(1.dp, c.line, RoundedCornerShape(24.dp)) else Modifier).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(label, style = Bc.type.body, fontWeight = FontWeight.Medium, color = fg)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = Bc.type.cardTitle, color = fg)
        Spacer(Modifier.height(4.dp))
        Text(body, style = Bc.type.body, color = if (tone == Tone.INFO) c.textSecondary else fg)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            PillButton(action, onAction, style = if (tone == Tone.INFO) PillStyle.Primary else PillStyle.Custom(dot, if (c.isDark) Color(0xFF1B1B1F) else Color.White), height = 40.dp)
        }
    }
}

/** 常用开关：两列方块，点一下开关；开着的描强调色边。 */
@Composable
private fun QuickToggles(values: Map<String, Any?>) {
    val app = ModuleApp.instance
    val c = Bc.colors
    @Suppress("UNCHECKED_CAST")
    val keys = (values[Settings.Module.quickToggles.key] as? Set<String>) ?: Settings.Module.quickToggles.default
    val items = Settings.all.filter { it.key in keys }.filterIsInstance<BoolSetting>()
    if (items.isEmpty()) {
        Notice("还没有常用开关，点“编辑”挑几个放在这里", Tone.INFO)
        return
    }
    Column(Modifier.padding(horizontal = PageGutter).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { s ->
                    val on = (values[s.key] as? Boolean) ?: s.default
                    val shape = RoundedCornerShape(18.dp)
                    Row(
                        Modifier.weight(1f).heightIn(min = 68.dp).clip(shape).background(c.surface).border(1.dp, if (on) c.accent else c.line, shape)
                            .clickable(role = Role.Switch, onClickLabel = s.title) { app.settings.set(s, !on) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HueTile(BcIcons.Gear, if (on) Hue.HOME else Hue.GRAY, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = Bc.type.body, fontWeight = FontWeight.Medium, color = c.text, maxLines = 2)
                            Text(if (on) "开" else "关", style = Bc.type.caption, color = if (on) c.accent else c.textSecondary)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 一条改动的说明：“打开 · 去掉示例广告”“隐藏的入口 +1”。 */
fun describeChange(ch: ChangeJournal.Change): String {
    val s = Settings.byKey(ch.key) ?: return ch.key
    return when {
        s is BoolSetting -> (if (ch.after == true) "打开 · " else "关闭 · ") + s.title
        ch.after is Set<*> || ch.before is Set<*> -> {
            val a = (ch.after as? Set<*>)?.size ?: 0
            val b = (ch.before as? Set<*>)?.size ?: 0
            s.title + if (a >= b) " +${a - b}" else " −${b - a}"
        }
        ch.after == null -> "恢复默认 · ${s.title}"
        else -> "${s.title} → ${ConfigDocument.describe(s, ch.after).take(16)}"
    }
}

/** 相对时间：刚刚、N 分钟前、今天 21:40、10月18日。 */
fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
    val d = now - at
    if (d < 60_000) return "刚刚"
    if (d < 3_600_000) return "${d / 60_000} 分钟前"
    val z = java.time.ZoneId.systemDefault()
    val t = java.time.Instant.ofEpochMilli(at).atZone(z)
    val today = java.time.Instant.ofEpochMilli(now).atZone(z).toLocalDate()
    return when (t.toLocalDate()) {
        today -> "今天 %02d:%02d".format(t.hour, t.minute)
        today.minusDays(1) -> "昨天 %02d:%02d".format(t.hour, t.minute)
        else -> "${t.monthValue}月${t.dayOfMonth}日"
    }
}

/** 最近改动的一行：说明、时间、撤销。 */
@Composable
fun ChangeRow(ch: ChangeJournal.Change, divider: Boolean) {
    val app = ModuleApp.instance
    val c = Bc.colors
    CardRow(divider) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = CardPadding, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(describeChange(ch), style = Bc.type.itemTitle, color = c.text, maxLines = 2)
                Text(relativeTime(ch.at), style = Bc.type.caption, color = c.textSecondary)
            }
            PillButton("撤销", { app.journal.undo(ch, app.settings) }, style = PillStyle.Text, icon = BcIcons.Undo, height = 40.dp)
        }
    }
}

/** 全部最近改动。 */
@Composable
fun ChangesPage() {
    val app = ModuleApp.instance
    val changes by collect(app.journal.changes)
    PageScaffold("最近改动", "最多保留 ${ChangeJournal.KEEP} 条；撤销会把那一项改回去") {
        if (changes.isEmpty()) io.github.clearkit.starter.app.ui.components.EmptyState("还没有改动", "在【模块】或【宿主】里改过的设置会记在这里")
        androidx.compose.runtime.key(changes) { changes.forEachIndexed { i, c -> ChangeRow(c, divider = i < changes.lastIndex) } }
    }
}

/** 编辑常用开关：从全部开关里挑，最多 8 个。 */
@Composable
fun QuickEditPage() {
    val app = ModuleApp.instance
    val values by collect(app.settings.values)
    @Suppress("UNCHECKED_CAST")
    val keys = (values[Settings.Module.quickToggles.key] as? Set<String>) ?: Settings.Module.quickToggles.default
    PageScaffold("编辑常用开关", "已选 ${keys.size} / 8，按设置顺序排在首页") {
        io.github.clearkit.starter.model.settings.FeatureCatalog.pages.forEach { page ->
            val bools = page.group.all.filterIsInstance<BoolSetting>()
            if (bools.isEmpty()) return@forEach
            io.github.clearkit.starter.app.ui.components.SectionLabel(page.title)
            bools.forEachIndexed { i, s ->
                val chosen = s.key in keys
                io.github.clearkit.starter.app.ui.components.ListRow(
                    s.title, s.summary, divider = i < bools.lastIndex, enabled = chosen || keys.size < 8,
                    onClick = { app.settings.update(Settings.Module.quickToggles) { if (s.key in it) it - s.key else it + s.key } }, role = Role.Checkbox,
                ) {
                    if (chosen) androidx.compose.material3.Icon(BcIcons.Check, contentDescription = "已选", tint = c().accent, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun c() = Bc.colors

/** 改了要重启【宿主】才生效的设置时的提示条。 */
@Composable
fun RestartNotice() {
    val app = ModuleApp.instance
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pending by collect(app.settings.pendingRestart)
    Notice("${pending.size} 项改动重开【宿主】后生效", Tone.WARN, "重开") {
        val pkg = app.status.status.value?.hostPackage ?: HostControl.installed(context).firstOrNull()?.packageName ?: return@Notice
        scope.launch {
            if (HostControl.restart(context, pkg)) app.settings.clearPendingRestart()
            else android.widget.Toast.makeText(context, "在应用信息里点“强行停止”，再打开【宿主】，改动就会生效", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
