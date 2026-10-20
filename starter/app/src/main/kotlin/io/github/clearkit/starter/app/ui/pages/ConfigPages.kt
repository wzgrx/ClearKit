package io.github.clearkit.starter.app.ui.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.data.ConfigHistory
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
import io.github.clearkit.starter.model.config.ConfigDocument
import io.github.clearkit.starter.model.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 待恢复的文档（从文件、版本历史、远程或方案来）：在页面之间传，进恢复预览页时读。 */
internal object ConfigDraft {
    var document: ConfigDocument.Document? = null
    /** 方案只合并，不允许整份替换。 */
    var mergeOnly: Boolean = false
    /** 恢复前自动保存的版本叫什么。 */
    var backupSource: String = "恢复前自动保存"
}

private fun time(ms: Long): String = if (ms <= 0) "时间未知" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))

private fun groupOf(key: String): String = io.github.clearkit.starter.model.settings.SettingPages.groupLabel(key)

/**
 * 配置与备份：保存版本、导出导入文件、版本历史。
 * 凭据（访问密钥、语义过滤密钥）和一次性令牌不进任何备份；恢复都先出逐项对比，确认后才写入。
 */
@Composable
fun ConfigPage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val values by collect(app.settings.values)
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    val count = ConfigDocument.collect(values).size

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ConfigDocument.export(values, ConfigDocument.Meta(System.currentTimeMillis(), "导出文件", app.configHistory.appVersion()))
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                    val back = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    if (back == text) "已导出 ${ConfigDocument.collect(values).size} 项设置并读回核对（不含凭据）" to Tone.OK else "导出后读回的内容不一致，请重试" to Tone.WARN
                }.getOrElse { "导出失败：${it.message}" to Tone.DANGER }
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().take(ConfigDocument.MAX_SIZE + 1).toByteArray().decodeToString() } }
            }
            r.onSuccess { text ->
                when (val p = ConfigDocument.parse(text)) {
                    is ConfigDocument.Parsed.Ok -> {
                        ConfigDraft.document = p.document; ConfigDraft.mergeOnly = false; ConfigDraft.backupSource = "导入前自动保存"
                        nav.open("config/restore")
                    }
                    is ConfigDocument.Parsed.Bad -> message = p.reason to Tone.DANGER
                }
            }.onFailure { message = "读不了文件：${it.message}" to Tone.DANGER }
        }
    }

    PageScaffold("配置与备份", "方案、版本与远程备份") {
        message?.let { (t, tone) -> Notice(t, tone) }
        SectionLabel("一键方案")
        NavRow(
            "配置作用域", app.settings.editingLayer?.let { "正在编辑：${io.github.clearkit.starter.model.settings.ConfigScope.label(it)}" } ?: "不同渠道、账号各自覆盖一部分设置",
            "设置", divider = false,
        ) { nav.open("config/scope") }
        SectionLabel("配置版本")
        NavRow("保存当前配置版本", "现在有 $count 项改过的设置；不含访问密钥等凭据") {
            scope.launch {
                message = withContext(Dispatchers.IO) {
                    runCatching { app.configHistory.save(values, "手动保存"); "已保存当前配置（$count 项）" to Tone.OK }.getOrElse { "保存失败：${it.message}" to Tone.DANGER }
                }
            }
        }
        NavRow("版本历史", "查看、对比、恢复以前保存的版本", "查看") { nav.open("config/history") }
        SectionLabel("文件")
        NavRow("导出配置文件", "保存成文件，换机或重装后导入") { exporter.launch("clearstarter-config-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())}.json") }
        NavRow("导入配置文件", "先对比再确认", divider = false) { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
        Notice("备份里不含凭据（密钥、密码）和一次性请求。恢复前会自动保存一份当前配置。")
    }
}

@Composable
fun ConfigRestorePage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val doc = ConfigDraft.document
    val values by collect(app.settings.values)
    var mode by remember { mutableStateOf(ConfigDocument.Mode.MERGE) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    var done by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(PAGE) }
    if (doc == null) {
        PageScaffold("恢复前对比", "配置与备份") { Notice("没有待恢复的配置，请从导入、版本历史或方案进来。") }
        return
    }
    val plan = remember(values, doc, mode) { ConfigDocument.plan(values, doc, mode) }
    PageScaffold(
        "恢复前对比", doc.meta.source.ifEmpty { "配置与备份" },
        bottom = if (done) ({ BottomActions(primary = "完成") { nav.back() } }) else if (plan.changes.isEmpty()) null else ({
            BottomActions("取消", { nav.back() }, if (busy) "正在写入…" else "确认写入 ${plan.changes.size} 项", primaryEnabled = !busy) {
                busy = true
                scope.launch {
                    message = withContext(Dispatchers.IO) {
                        runCatching {
                            app.configHistory.save(values, ConfigDraft.backupSource)
                            val bad = ConfigHistory.apply(app.settings, plan)
                            if (bad.isEmpty()) "已写入 ${plan.changes.size} 项并读回核对；写入前的配置已存进版本历史" + (if (plan.changes.any { it.setting.needsRestart }) "。部分设置要重启【宿主】才生效" else "") to Tone.OK
                            else "有 ${bad.size} 项没写对：${bad.take(5).joinToString("、") { it.title }}" to Tone.WARN
                        }.getOrElse { "写入失败：${it.message}（没有改动或只改了一部分，可以从版本历史恢复）" to Tone.DANGER }
                    }
                    busy = false; done = true
                }
            }
        }),
    ) {
        message?.let { (t, tone) -> Notice(t, tone) }
        SectionLabel("来源与校验")
        ValueRow("来源", doc.origin.label, doc.meta.source.ifEmpty { "未注明" })
        ValueRow("时间", if (doc.meta.appVersion.isNotEmpty()) "【模块】 ${doc.meta.appVersion}" else null, time(doc.meta.createdAt))
        if (doc.meta.label.isNotEmpty()) ValueRow("名字", null, doc.meta.label)
        ValueRow("文件里的设置", null, "${doc.values.size} 项", divider = false)
        if (!ConfigDraft.mergeOnly && !done) {
            ChipsPicker("恢复范围", ConfigDocument.Mode.entries.map { it.name to it.label }, setOf(mode.name)) { mode = ConfigDocument.Mode.valueOf(it) }
            Notice(mode.summary)
        }
        SectionLabel("变化（${plan.changes.size} 项，${plan.same} 项相同）")
        if (plan.changes.isEmpty()) ValueRow("没有要改的", "现在的设置和文件里的一样", "")
        plan.changes.take(shown).forEachIndexed { i, ch ->
            ValueRow(
                ch.setting.title,
                "${groupOf(ch.setting.key)} · ${ch.kind.label}：${ConfigDocument.describe(ch.setting, ch.before)} → ${ConfigDocument.describe(ch.setting, ch.after)}",
                "", divider = i < minOf(shown, plan.changes.size) - 1,
            )
        }
        if (plan.changes.size > shown) NavRow("再显示 ${minOf(PAGE, plan.changes.size - shown)} 项", "共 ${plan.changes.size} 项", divider = false) { shown += PAGE }
        if (plan.rejected.isNotEmpty()) {
            SectionLabel("不写入（${plan.rejected.size} 项）")
            plan.rejected.entries.forEachIndexed { i, (k, why) -> ValueRow(Settings.byKey(k)?.title ?: k, why, "", divider = i < plan.rejected.size - 1) }
        }
        if (plan.unknown.isNotEmpty()) {
            SectionLabel("不认识的设置（${plan.unknown.size} 项）")
            Notice("可能来自更新版本的【模块】，这次不写入：${plan.unknown.take(20).joinToString("、")}" + if (plan.unknown.size > 20) " 等" else "")
        }
        if (doc.skipped.isNotEmpty()) {
            SectionLabel("旧版文件里跳过的（${doc.skipped.size} 项）")
            doc.skipped.entries.take(30).forEachIndexed { i, (k, why) -> ValueRow(k, why, "", divider = i < minOf(30, doc.skipped.size) - 1) }
        }
    }
}

private const val PAGE = 60

/** 配置版本历史（设计 030）：按来源、名字搜索；点开看详情、对比恢复、导出、删除。 */
@Composable
fun ConfigHistoryPage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ConfigHistory.Entry>?>(null) }
    var query by remember { mutableStateOf("") }
    var unlimited by remember { mutableStateOf(app.configHistory.keepUnlimited) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { entries = withContext(Dispatchers.IO) { app.configHistory.list() } }
    PageScaffold("版本历史", "配置与备份") {
        io.github.clearkit.starter.app.ui.components.BcTextField(query, { query = it }, label = { Text("按来源或名字搜索") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 6.dp))
        val list = entries
        if (list == null) { io.github.clearkit.starter.app.ui.components.LoadingState(); return@PageScaffold }
        val shown = list.filter { query.isBlank() || it.meta.source.contains(query, true) || it.meta.label.contains(query, true) || time(it.meta.createdAt).contains(query) }
        SectionLabel("已保存的版本（${list.size} 个）")
        if (list.isEmpty()) io.github.clearkit.starter.app.ui.components.EmptyState("还没有保存过版本", "在“配置与备份”里保存，或恢复、应用方案时自动保存")
        shown.forEachIndexed { i, e ->
            NavRow(time(e.meta.createdAt), listOf(e.meta.source, e.meta.label).filter { it.isNotEmpty() }.joinToString(" · "), "${e.count} 项", divider = i < shown.lastIndex) {
                nav.open("config/history/item?id=${e.id}")
            }
        }
        SectionLabel("保留")
        SwitchRow("不限制保留数量", "默认只留最近 ${ConfigHistory.DEFAULT_KEEP} 个；只管本模块的版本文件，手机空间照样有限", unlimited, divider = false) {
            unlimited = it; scope.launch(Dispatchers.IO) { app.configHistory.keepUnlimited = it; refresh++ }
        }
    }
}

@Composable
fun ConfigHistoryItemPage(route: String) {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = routeParam(route, "id").orEmpty()
    var doc by remember { mutableStateOf<ConfigDocument.Document?>(null) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(id) { doc = withContext(Dispatchers.IO) { app.configHistory.read(id) } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.configHistory.text(id) ?: error("版本已不存在")
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                    "已导出这个版本" to Tone.OK
                }.getOrElse { "导出失败：${it.message}" to Tone.DANGER }
            }
        }
    }
    val d = doc
    PageScaffold(
        "版本详情", "版本历史",
        bottom = if (d == null) null else ({
            if (deleting) BottomActions("取消", { deleting = false }, "确认删除") {
                scope.launch { withContext(Dispatchers.IO) { app.configHistory.delete(id) }; nav.back() }
            } else BottomActions("删除", { deleting = true }, "对比当前配置并恢复") {
                ConfigDraft.document = d; ConfigDraft.mergeOnly = false; ConfigDraft.backupSource = "恢复前自动保存"
                nav.open("config/restore")
            }
        }),
    ) {
        message?.let { (t, tone) -> Notice(t, tone) }
        if (d == null) { Notice("正在读取，或这个版本已经不存在。"); return@PageScaffold }
        if (deleting) Notice("删除后不能找回。", Tone.WARN)
        ValueRow("时间", null, time(d.meta.createdAt))
        ValueRow("来源", null, d.meta.source.ifEmpty { "未注明" })
        if (d.meta.appVersion.isNotEmpty()) ValueRow("【模块】版本", null, d.meta.appVersion)
        ValueRow("设置项", null, "${d.values.size} 项", divider = false)
        NavRow("导出这个版本", "保存成文件", divider = false) { exporter.launch("clearstarter-config-$id.json") }
        SectionLabel("完整内容")
        val items = d.values.entries.sortedBy { it.key }
        (if (showAll) items else items.take(PAGE)).forEachIndexed { i, (k, v) ->
            val s = Settings.byKey(k)
            ValueRow(s?.title ?: k, "${groupOf(k)} · ${s?.let { ConfigDocument.describe(it, v) } ?: v.toString()}", "", divider = i < items.size - 1)
        }
        if (!showAll && items.size > PAGE) NavRow("显示全部 ${items.size} 项", null, divider = false) { showAll = true }
    }
}
