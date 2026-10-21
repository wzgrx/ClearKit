package io.github.clearkit.starter.app.ui.pages

import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.adapt.HostAdapter
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.BottomActions
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.Notice
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.Tone
import io.github.clearkit.starter.app.ui.components.ValueRow
import io.github.clearkit.starter.app.ui.theme.Bc
import io.github.clearkit.starter.model.HostBuild
import io.github.clearkit.starter.symbols.SymbolCatalog

/** 适配与运行状态（设计“适配与运行状态详情”）：每个客户端用哪张表、上次扫描、手动重扫和自检。 */
@Composable
fun AdaptPage() {
    val adapter = ModuleApp.instance.hostAdapter
    val context = LocalContext.current
    val hosts by collect(adapter.statuses)
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { adapter.refresh() } }
    PageScaffold(
        "适配与运行状态", "每个渠道分别适配",
        bottom = {
            BottomActions("复制报告", {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("【模块】适配报告", adapter.report()))
                Toast.makeText(context, "已复制适配报告", Toast.LENGTH_SHORT).show()
            }, "重新检查") { adapter.check("手动检查") }
        },
    ) {
        Notice("内置 ${HostBuild.entries.joinToString("、") { it.label }} 的适配；其它版本打开【模块】后自动在手机上适配，一般几十秒。共 ${SymbolCatalog.all.size} 处查找。")
        for (st in hosts.values.sortedBy { it.channel?.ordinal }) HostSection(st)
    }
}

@Composable
private fun HostSection(st: HostAdapter.Status) {
    val adapter = ModuleApp.instance.hostAdapter
    SectionLabel("【宿主】${st.channel?.label.orEmpty()}")
    if (!st.installed) {
        ValueRow("版本", null, "没有安装", divider = false)
        return
    }
    ValueRow("版本", st.packageName, "${st.versionName}（${st.versionCode}）")
    ValueRow(
        "适配来源", if (st.bundled) "这个版本有内置适配" else "这个版本没有内置适配", st.source,
        valueColor = if (st.ready) Bc.colors.ok else Bc.colors.warn,
    )
    // 【宿主】这次运行报来的适配报告（和【宿主】里的卡片说的一样）
    val reports by collect(ModuleApp.instance.status.all)
    reports[st.packageName]?.takeIf { it.hostVersionCode == st.versionCode }?.report?.let { ReportRows(it, st.packageName) }
    st.lastScan?.let { r ->
        val at = java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(r.at))
        ValueRow("上次扫描", "$at · ${r.reason}", if (r.ok) "找到 ${r.found} / ${r.total}" else "失败")
    }
    when (st.phase) {
        HostAdapter.Phase.RUNNING -> Notice(if (st.total > 0) "正在解析 ${st.progress} / ${st.total}" else st.message.ifEmpty { "正在准备…" })
        HostAdapter.Phase.DONE -> if (st.message.isNotEmpty()) Notice(st.message, Tone.OK)
        HostAdapter.Phase.FAILED -> Notice(st.message, Tone.DANGER)
        HostAdapter.Phase.IDLE -> Unit
    }
    NavRow("全量扫描", "不管判断结果，在手机上重新解析一遍", "扫描", enabled = st.phase != HostAdapter.Phase.RUNNING) { adapter.start(st.packageName) }
    NavRow("自检", "内置版本：重新解析并和内置表逐条比对", "自检", divider = false, enabled = st.bundled && st.phase != HostAdapter.Phase.RUNNING) {
        adapter.start(st.packageName, selfCheck = true)
    }
}

/** 一份适配报告：找到几处、几项生效、要重开的（带“立即重开”）、用不了的和原因、先观察的。 */
@Composable
private fun ReportRows(r: io.github.clearkit.starter.model.adapt.AdaptReport, pkg: String) {
    val nav = io.github.clearkit.starter.app.ui.LocalNavigator.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ValueRow("这次运行", io.github.clearkit.starter.model.adapt.AdaptCardText.foundLine(r), "${r.active} 项生效", valueColor = if (r.tier == io.github.clearkit.starter.model.adapt.AdaptReport.Tier.OK) Bc.colors.ok else Bc.colors.warn)
    if (r.restart.isNotEmpty()) {
        Notice("重开【宿主】后 ${r.restart.size} 项生效：${r.restart.joinToString("、")}", Tone.WARN, "立即重开") {
            scope.launch { io.github.clearkit.starter.app.data.HostControl.restart(context, pkg) }
        }
    }
    if (r.unavailable.isNotEmpty()) {
        SectionLabel("这一版用不了 · ${r.unavailable.size}")
        r.unavailable.forEachIndexed { i, issue -> ValueRow(issue.title, issue.reason, "", divider = i < r.unavailable.lastIndex) }
    }
    if (r.observing > 0) ValueRow("先观察", "推测出来的位置，用到时自动确认，确认前不改【宿主】的行为", "${r.observing} 项")
    NavRow("每个功能的状态", "生效、待重开、用不了、没开，各带原因", "查看") { nav.open("status") }
}
