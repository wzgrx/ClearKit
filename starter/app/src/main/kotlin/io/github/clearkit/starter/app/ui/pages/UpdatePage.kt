package io.github.clearkit.starter.app.ui.pages

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.data.Updater
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.BottomActions
import io.github.clearkit.starter.app.ui.components.ChipsPicker
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.Notice
import io.github.clearkit.starter.app.ui.components.PageGutter
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.Tone
import io.github.clearkit.starter.app.ui.components.ValueRow
import io.github.clearkit.starter.app.ui.theme.Bc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 模块更新（设计 035）：选渠道（正式版 / 含预发布）、明确点了才检查；看完整更新说明；下载后核对包名、版本和签名证书，
 * 通过才交给系统安装器（覆盖安装不清数据）；回来后点“重新核对”看是否真的装上了。
 */
@Composable
fun UpdatePage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { Updater(context) }
    var installed by remember { mutableStateOf(updater.installedVersion()) }
    var pre by remember { mutableStateOf(false) }
    var releases by remember { mutableStateOf<List<Updater.Release>?>(null) }
    var selected by remember { mutableStateOf<Updater.Release?>(null) }
    var verified by remember { mutableStateOf<Updater.Verified?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; message = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { updater.fromFile(uri) } }
            busy = false
            r.onSuccess { verified = it; message = "已核对：${it.versionName}（${it.versionCode}），签名和已安装的一致" to Tone.OK }
                .onFailure { message = "没通过核对：${it.message}" to Tone.DANGER }
        }
    }
    val sel = selected
    val v = verified
    PageScaffold(
        "模块更新", "当前 ${installed.first}",
        bottom = when {
            v != null -> ({ BottomActions("取消", { verified = null }, "交给系统安装") { if (!updater.install(v)) message = "请允许【模块】安装应用，然后回来再点一次" to Tone.WARN } })
            sel != null && sel.apkUrl != null -> ({
                BottomActions("返回", { selected = null }, if (busy) "下载中 $progress" else "下载并核对", primaryEnabled = !busy) {
                    busy = true; message = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching {
                                val f = updater.download(sel) { done, total -> progress = if (total > 0) "${done * 100 / total}%" else "${done / 1024} KB" }
                                updater.verify(f)
                            }
                        }
                        busy = false
                        r.onSuccess { verified = it; message = "已下载并核对：${it.versionName}（${it.versionCode}），签名和已安装的一致" to Tone.OK }
                            .onFailure { message = "没通过核对：${it.message}" to Tone.DANGER }
                    }
                }
            })
            else -> null
        },
    ) {
        message?.let { (t, tone) -> Notice(t, tone) }
        if (sel != null) {
            SectionLabel(sel.name)
            ValueRow("版本", if (sel.prerelease) "预发布，不是正式版" else "正式版", sel.tag)
            ValueRow("发布日期", null, sel.publishedAt)
            ValueRow("安装包", sel.apkName ?: "这个版本没有安装包", if (sel.apkSize > 0) "${sel.apkSize / 1024 / 1024} MB" else "")
            SectionLabel("完整更新说明")
            SelectionContainer { Text(sel.notes.ifBlank { "（没有写说明）" }, style = Bc.type.body, color = Bc.colors.text, modifier = Modifier.padding(horizontal = PageGutter, vertical = 8.dp)) }
            return@PageScaffold
        }
        SectionLabel("当前安装")
        ValueRow("版本", null, "${installed.first}（${installed.second}）")
        NavRow("重新核对安装结果", "安装器回来后点这里，看版本是否真的变了", divider = false) {
            val before = installed
            installed = updater.installedVersion()
            message = (if (installed.second > before.second) "已更新到 ${installed.first}" else "版本没变：还是 ${installed.first}") to Tone.INFO
        }
        SectionLabel("检查更新")
        ChipsPicker("渠道", listOf("stable" to "正式版", "pre" to "含预发布"), setOf(if (pre) "pre" else "stable")) { pre = it == "pre"; releases = null }
        NavRow(if (busy) "正在检查…" else "检查更新", "访问 GitHub（${Updater.REPO}）的发布页；只在点了才联网", enabled = !busy) {
            busy = true; message = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { updater.releases(pre) } }
                busy = false
                r.onSuccess { releases = it; if (it.isEmpty()) message = "没有找到发布的版本" to Tone.INFO }.onFailure { message = "检查失败：${it.message}" to Tone.DANGER }
            }
        }
        NavRow(
            "从文件安装", "选一个【模块】的安装包（网盘、聊天软件里收到的都行）；核对包名、版本和签名证书，一致才交给系统安装",
            "选择", enabled = !busy, divider = releases == null,
        ) { pick.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }
        releases?.let { list ->
            SectionLabel("可用版本（${list.size}）")
            list.forEachIndexed { i, r ->
                val newer = r.tag.removePrefix("v") != installed.first
                NavRow(r.name, listOfNotNull(r.publishedAt, if (r.prerelease) "预发布" else null, if (r.apkUrl == null) "没有安装包" else null).joinToString(" · "), if (!newer) "当前" else "查看", divider = i < list.lastIndex) {
                    selected = r; verified = null; message = null
                }
            }
        }
    }
}
