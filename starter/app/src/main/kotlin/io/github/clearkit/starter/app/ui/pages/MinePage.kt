package io.github.clearkit.starter.app.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import io.github.clearkit.starter.BuildConfig
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.NavRow
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.SettingToggle
import io.github.clearkit.starter.model.bridge.FeatureStatus
import io.github.clearkit.starter.model.settings.Settings

/** 我的：配置与备份、适配与诊断、模块自身、关于。 */
@Composable
fun MinePage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val hosts by collect(app.hostAdapter.statuses)
    val reports by collect(app.status.all)
    val values by collect(app.settings.values)
    val installed = hosts.values.filter { it.installed }
    PageScaffold("我的", "", back = false) {
        SectionLabel("配置与备份")
        NavRow("备份与恢复", "导出、导入配置文件，版本历史", "打开", divider = false) { nav.open("config") }
        SectionLabel("适配与诊断")
        NavRow(
            "适配状态", installed.joinToString(" · ") { "${it.channel?.label ?: it.packageName} ${it.versionName}" }.ifEmpty { "没有安装【宿主】" },
            if (installed.isNotEmpty() && installed.all { it.ready }) "已适配" else "查看",
        ) { nav.open("adapt") }
        val active = reports.values.sumOf { r -> r.features.count { it.state == FeatureStatus.State.ACTIVE } }
        val failed = reports.values.sumOf { r -> r.features.count { it.state == FeatureStatus.State.FAILED || it.state == FeatureStatus.State.CRASH_DISABLED } }
        NavRow("功能运行情况", if (reports.isEmpty()) "打开一次【宿主】后显示" else "$active 项生效 · $failed 项失败", "查看") { nav.open("status") }
        NavRow("诊断中心", "功能记录、冲突检查、维护模式、导出诊断报告", "打开", divider = false) { nav.open("diagnostics") }
        SectionLabel("模块")
        NavRow("分层配置", "不同渠道、账号各自覆盖一部分设置", if (values[Settings.Scope.enabled.key] == true) "开" else "关") { nav.open("config/scope") }
        NavRow("外观", "深色模式、强调色、界面密度与动效", "设置") { nav.open("appearance") }
        NavRow("模块设置", "运行提示、崩溃保护、日志与桌面图标", "设置", divider = false) { nav.open("module") }
        SectionLabel("帮助")
        NavRow("关于与更新", "版本、开源许可、检查更新", BuildConfig.VERSION_NAME, divider = false) { nav.open("about") }
    }
}
