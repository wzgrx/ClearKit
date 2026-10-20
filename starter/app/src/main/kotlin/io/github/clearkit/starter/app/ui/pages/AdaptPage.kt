package io.github.clearkit.starter.app.ui.pages

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
