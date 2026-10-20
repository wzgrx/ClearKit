package io.github.clearkit.starter.app.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.BuildConfig
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.BottomActions
import io.github.clearkit.starter.app.ui.components.ChipsPicker
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.Notice
import io.github.clearkit.starter.app.ui.components.PageGutter
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.SwitchRow
import io.github.clearkit.starter.app.ui.components.Tone
import io.github.clearkit.starter.app.ui.components.ValueRow
import io.github.clearkit.starter.app.ui.theme.Bc
import io.github.clearkit.starter.model.HostBuild
import io.github.clearkit.starter.model.bridge.FeatureStatus
import io.github.clearkit.starter.model.diag.Diagnostics
import io.github.clearkit.starter.model.settings.ConfigScope
import io.github.clearkit.starter.model.settings.MapSettingsReader
import io.github.clearkit.starter.model.settings.Settings
import java.io.File
import java.security.MessageDigest

/** 会和本模块冲突的其它模块（作用于同一个【宿主】、改同样的东西）：装着时在冲突检查里提醒。没有就留空。 */
private val CONFLICTING_MODULES: Set<String> = emptySet()

private fun installedConflicts(context: Context): Set<String> = CONFLICTING_MODULES.filter { pkg ->
    runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
}.toSet()

private fun stateColorTone(s: FeatureStatus.State) = when (s) {
    FeatureStatus.State.ACTIVE -> Tone.OK
    FeatureStatus.State.OFF -> Tone.INFO
    FeatureStatus.State.OBSERVING, FeatureStatus.State.SAFE_MODE_OFF -> Tone.WARN
    else -> Tone.DANGER
}

/** 生成脱敏报告（设计 033、086、135）：功能状态、检查结果、改过的设置（凭据、地址隐藏，名单只写数量）。 */
private fun buildReport(context: Context): String {
    val app = ModuleApp.instance
    val stored = app.settings.stored()
    val reader = MapSettingsReader { app.settings.values.value[it] }
    val status = app.status.all.value.values
    val issues = Diagnostics.issues(reader, stored.keys, status, installedConflicts(context))
    return Diagnostics.report(
        Diagnostics.Device(Build.VERSION.RELEASE, Build.VERSION.SDK_INT, "${Build.MANUFACTURER} ${Build.MODEL}", "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）", io.github.clearkit.starter.app.data.Root.state.value.label),
        stored, status, issues, System.currentTimeMillis(),
    )
}

/**
 * 诊断中心（设计 033、135）：按名字搜、按状态筛全部功能记录；冲突检查、兼容性、全部配置详情的入口；
 * 脱敏的诊断报告先全文预览，再复制或分享。默认只读，不触发任何检测。
 */
@Composable
fun DiagnosticsPage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val all by collect(app.status.all)
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Diagnostics.Filter.ALL) }
    var report by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    val r = report
    PageScaffold(
        if (r == null) "诊断中心" else "诊断报告", if (r == null) "功能记录与排查" else "确认内容后再复制或分享",
        bottom = if (r == null) null else ({
            BottomActions("复制", {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("【模块】诊断报告", r))
                message = "已复制诊断报告（${r.length} 字）" to Tone.OK
            }, "分享") {
                runCatching {
                    val dir = File(context.cacheDir, "share").apply { mkdirs() }
                    val f = File(dir, "clearstarter-report.txt").apply { writeText(r) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "分享诊断报告"))
                }.onFailure { message = "分享失败：${it.message}" to Tone.DANGER }
            }
        }),
    ) {
        message?.let { (t, tone) -> Notice(t, tone) }
        if (r != null) {
            Notice("报告里没有账号、登录信息、访问密钥和服务器地址；名单类设置只写数量。")
            SelectionContainer { Text(r, style = Bc.type.caption, color = Bc.colors.text, modifier = Modifier.padding(horizontal = PageGutter, vertical = 8.dp)) }
            NavRow("返回诊断中心", null, divider = false) { report = null }
            return@PageScaffold
        }
        SectionLabel("排查")
        NavRow("冲突与前置条件检查", "开着但不会生效的设置、冲突的模块、出错的功能", "检查") { nav.open("conflict") }
        NavRow("客户端兼容性", "【宿主】的版本、签名和适配情况", "查看") { nav.open("compat") }
        NavRow("全部配置详情", "每项设置的值和来源，凭据隐藏", "查看") { nav.open("diagnostics/settings") }
        NavRow("维护模式", "临时停用指定功能，找出引起问题的那个", "设置") { nav.open("maintenance") }
        NavRow("日志与复现信息", "生成脱敏的诊断报告，先预览再复制或分享", divider = false) { report = buildReport(context) }
        SectionLabel("功能记录")
        io.github.clearkit.starter.app.ui.components.BcTextField(query, { query = it }, label = { Text("搜索功能名、标识或原因") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 6.dp))
        ChipsPicker("状态", Diagnostics.Filter.entries.map { it.name to it.label }, setOf(filter.name)) { filter = Diagnostics.Filter.valueOf(it) }
        if (all.isEmpty()) Notice("还没收到【宿主】的报告。启用模块后打开一次【宿主】，这里就会显示各功能的状态。")
        for ((pkg, s) in all.toSortedMap()) {
            val shown = Diagnostics.filter(s.features, query, filter)
            SectionLabel("【宿主】${HostBuild.channelOf(pkg)?.label.orEmpty()} ${s.hostVersionName}（${shown.size} / ${s.features.size}）")
            if (shown.isEmpty()) ValueRow("没有符合条件的功能", null, "")
            shown.forEachIndexed { i, f ->
                val tone = stateColorTone(f.state)
                val color = when (tone) { Tone.OK -> Bc.colors.ok; Tone.WARN -> Bc.colors.warn; Tone.DANGER -> Bc.colors.danger; Tone.INFO -> Bc.colors.textSecondary }
                ValueRow(f.title, listOfNotNull(f.id, f.detail).joinToString(" · "), Diagnostics.stateLabel(f.state), divider = i < shown.lastIndex, valueColor = color)
            }
        }
    }
}

/** 全部配置详情（设计 033）：按分组列出每项设置的当前值、来源（默认 / 已改 / 分层覆盖），凭据和地址隐藏。 */
@Composable
fun SettingsDetailPage() {
    val app = ModuleApp.instance
    val values by collect(app.settings.values)
    val stored = remember(values) { app.settings.stored() }
    var query by remember { mutableStateOf("") }
    var changedOnly by remember { mutableStateOf(true) }
    var limit by remember { mutableIntStateOf(80) }
    val items = Settings.all.filter { s ->
        (!changedOnly || stored.containsKey(s.key) || stored.keys.any { k -> ConfigScope.split(k)?.second == s.key }) &&
            (query.isBlank() || s.title.contains(query, true) || s.key.contains(query, true))
    }
    PageScaffold("全部配置详情", "诊断中心") {
        io.github.clearkit.starter.app.ui.components.BcTextField(query, { query = it }, label = { Text("搜索设置名或键") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 6.dp))
        SwitchRow("只看改过的", "共 ${Settings.all.size} 项设置", changedOnly) { changedOnly = it }
        SectionLabel("${items.size} 项")
        items.take(limit).forEachIndexed { i, s ->
            val layers = ConfigScope.LAYERS.drop(1).mapNotNull { (l, label) -> stored[ConfigScope.key(l, s.key)]?.let { "$label：${Diagnostics.reportValue(s, it)}" } }
            val source = when {
                stored.containsKey(s.key) -> "已改"
                else -> "默认"
            }
            ValueRow(s.title, (listOf(s.key, "全局$source：${Diagnostics.reportValue(s, stored[s.key])}") + layers).joinToString(" · "), "", divider = i < minOf(limit, items.size) - 1)
        }
        if (items.size > limit) NavRow("再显示 ${minOf(80, items.size - limit)} 项", null, divider = false) { limit += 80 }
    }
}

/** 客户端兼容性（设计 032）：每个客户端的包名、版本名和版本码、签名指纹（展开才显示）、适配和功能证据分层。 */
@Composable
fun CompatPage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val all by collect(app.status.all)
    var showSig by remember { mutableStateOf(false) }
    PageScaffold("客户端兼容性", "诊断中心") {
        for (pkg in HostBuild.Channel.entries.map { it.packageName }) {
            val label = HostBuild.channelOf(pkg)?.label.orEmpty()
            val info = runCatching { context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
            SectionLabel("【宿主】$label")
            if (info == null) { ValueRow("没有安装", pkg, ""); continue }
            ValueRow("包名", "从系统读取", pkg)
            ValueRow("版本名称", null, info.versionName.orEmpty())
            ValueRow("版本码", null, androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toString())
            if (showSig) {
                val sig = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                val digest = sig?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString(":") { b -> "%02X".format(b) } } ?: "读不到"
                ValueRow("签名校验值", digest, "")
            }
            val s = all[pkg]
            if (s == null) { ValueRow("运行证据", "还没收到这个客户端的报告", "无"); continue }
            val counts = s.features.groupingBy { it.state }.eachCount()
            ValueRow("适配表", if (s.hostVersionName == info.versionName) "和已安装的版本一致" else "报告来自 ${s.hostVersionName}，已安装的是 ${info.versionName}，打开一次【宿主】会更新", if (s.adapted) "有" else "没有")
            ValueRow("功能证据", "生效 ${counts[FeatureStatus.State.ACTIVE] ?: 0}、观察中 ${counts[FeatureStatus.State.OBSERVING] ?: 0}、不可用 ${counts[FeatureStatus.State.UNAVAILABLE] ?: 0}、出错 ${(counts[FeatureStatus.State.FAILED] ?: 0) + (counts[FeatureStatus.State.CRASH_DISABLED] ?: 0)}、关闭 ${counts[FeatureStatus.State.OFF] ?: 0}", "", divider = false)
        }
        SectionLabel("说明")
        Notice("“生效”只表示钩子装上了、开关开着；实际效果（比如广告真的没了）要在【宿主】里看。")
        NavRow(if (showSig) "收起签名指纹" else "显示签名指纹", null) { showSig = !showSig }
        NavRow("适配与运行状态", "适配表来源、手机上重新适配", "查看", divider = false) { nav.open("adapt") }
    }
}

/** 冲突与前置条件检查（设计 034）：只看本模块的设置和报告，加上是否装了冲突的模块；点问题去对应设置页。 */
@Composable
fun ConflictPage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val values by collect(app.settings.values)
    val all by collect(app.status.all)
    var query by remember { mutableStateOf("") }
    var level by remember { mutableStateOf<Diagnostics.Level?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val issues = remember(values, all, refresh) {
        Diagnostics.issues(MapSettingsReader { values[it] }, app.settings.stored().keys, all.values, installedConflicts(context))
    }
    val shown = issues.filter { (level == null || it.level == level) && (query.isBlank() || it.title.contains(query, true) || it.detail.contains(query, true)) }
    PageScaffold("冲突检查", "诊断中心") {
        Notice("只检查本模块的设置和【宿主】报来的状态，不扫描别的模块，也不改根权限、框架或其他模块。")
        io.github.clearkit.starter.app.ui.components.BcTextField(query, { query = it }, label = { Text("搜索") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 6.dp))
        ChipsPicker("等级", listOf("" to "全部") + Diagnostics.Level.entries.map { it.name to it.label }, setOf(level?.name ?: "")) { id -> level = Diagnostics.Level.entries.firstOrNull { it.name == id } }
        SectionLabel("结果（${shown.size} 项）")
        if (shown.isEmpty()) ValueRow("没有发现问题", if (issues.isEmpty()) "所有检查都通过了" else "当前筛选下没有", "")
        shown.forEachIndexed { i, it ->
            val route = it.route
            if (route != null) NavRow("[${it.level.label}] ${it.title}", it.detail, "去设置", divider = i < shown.lastIndex) { nav.open(route) }
            else ValueRow("[${it.level.label}] ${it.title}", it.detail, "", divider = i < shown.lastIndex)
        }
        NavRow("重新检查", null, divider = false) { refresh++ }
    }
}

/**
 * 维护模式（设计 086）：临时停用指定功能（下次打开【宿主】生效），找出引起问题的那个；复现材料用诊断报告。
 * 温度保护、性能采样暂不提供，照实说明。
 */
@Composable
fun MaintenancePage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val values by collect(app.settings.values)
    val all by collect(app.status.all)
    @Suppress("UNCHECKED_CAST")
    val disabled = values[Settings.Module.disabledFeatures.key] as? Set<String> ?: emptySet()
    var query by remember { mutableStateOf("") }
    // 功能目录：【宿主】报来的全部功能（两个客户端合并），加上已停用但这次没报到的
    val features = (all.values.flatMap { s -> s.features.map { it.id to it.title } } + disabled.map { it to it }).distinctBy { it.first }.sortedBy { it.second }
    val shown = features.filter { query.isBlank() || it.second.contains(query, true) || it.first.contains(query, true) }
    PageScaffold("维护模式", "诊断中心") {
        Notice(if (disabled.isEmpty()) "正常模式：所有开着的功能都生效。" else "已停用 ${disabled.size} 个功能（下次打开【宿主】生效）。排查完记得恢复。", if (disabled.isEmpty()) Tone.INFO else Tone.WARN)
        if (disabled.isNotEmpty()) NavRow("全部恢复", "清空停用名单", divider = false) { app.settings.reset(Settings.Module.disabledFeatures) }
        SectionLabel("停用的功能")
        io.github.clearkit.starter.app.ui.components.BcTextField(query, { query = it }, label = { Text("搜索功能") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 6.dp))
        if (features.isEmpty()) Notice("还没收到【宿主】的功能列表：启用模块后打开一次【宿主】。")
        shown.forEachIndexed { i, (id, title) ->
            SwitchRow(title, if (id in disabled) "已停用 · $id" else id, id in disabled, divider = i < shown.lastIndex) { on ->
                app.settings.update(Settings.Module.disabledFeatures) { if (on) it + id else it - id }
            }
        }
        SectionLabel("复现材料")
        NavRow("生成诊断报告", "在诊断中心里预览、复制或分享", divider = false) { nav.open("diagnostics") }
        Notice("严重温度保护、低开销性能采样暂不提供。")
    }
}
