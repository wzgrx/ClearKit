package io.github.clearkit.starter.app.ui.pages

import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.clearkit.starter.BuildConfig
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.Notice
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.ValueRow

/** 关于与帮助：版本、更新、反馈、许可。 */
@Composable
fun AboutPage() {
    val context = LocalContext.current
    val nav = io.github.clearkit.starter.app.ui.LocalNavigator.current
    PageScaffold("关于与帮助", "【模块】 ${BuildConfig.VERSION_NAME}") {
        SectionLabel("版本")
        ValueRow("模块版本", null, "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
        ValueRow("DexKit", "在手机上适配新版本【宿主】", BuildConfig.DEXKIT_VERSION, divider = false)
        SectionLabel("更新与反馈")
        NavRow("模块更新", "检查新版本、下载并核对后安装", "检查") { nav.open("update") }
        NavRow("诊断中心", "功能记录、冲突检查、生成诊断报告", "打开") { nav.open("diagnostics") }
        NavRow("问题反馈", "在 GitHub 上提交问题（附上诊断报告更好排查）", "打开", divider = false) {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, "https://github.com/${io.github.clearkit.starter.app.data.Updater.REPO}/issues".toUri())) }
        }
        SectionLabel("许可")
        Notice("【模块】按 GPL-3.0 开源（许可写在仓库的 LICENSE 里，换许可时改这里）。")
        NavRow("源代码", "github.com/${io.github.clearkit.starter.app.data.Updater.REPO}", "打开", divider = false) {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, "https://github.com/${io.github.clearkit.starter.app.data.Updater.REPO}".toUri())) }
        }
    }
}
