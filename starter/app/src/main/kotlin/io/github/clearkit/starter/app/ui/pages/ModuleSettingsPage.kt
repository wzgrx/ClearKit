package io.github.clearkit.starter.app.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.data.LauncherIcon
import io.github.clearkit.starter.app.ui.PageScaffold
import io.github.clearkit.starter.app.ui.components.SectionLabel
import io.github.clearkit.starter.app.ui.components.SwitchRow
import io.github.clearkit.starter.app.ui.components.SettingToggle as Toggle
import io.github.clearkit.starter.model.settings.Settings
import kotlinx.coroutines.launch

/** 模块设置：模块自身的开关（运行提示、安全模式、根权限、日志、适配卡片、桌面图标）。 */
@Composable
fun ModuleSettingsPage() {
    val app = ModuleApp.instance
    val context = LocalContext.current
    val nav = io.github.clearkit.starter.app.ui.LocalNavigator.current
    val pending by collect(app.settings.pendingRestart)
    PageScaffold("模块设置", "运行、保护与日志") {
        io.github.clearkit.starter.app.ui.components.PagePreviewCard("module")
        io.github.clearkit.starter.app.ui.components.CloseCard()
        if (pending.isNotEmpty()) RestartNotice()
        val m = Settings.Module
        SectionLabel("运行")
        Toggle(m.enabled)
        Toggle(m.safeMode)
        Toggle(m.showInfo)
        Toggle(m.adaptCard, divider = false)
        RootSection()
        SectionLabel("排查问题")
        Toggle(m.autoDisableCrashing)
        Toggle(m.verboseLog)
        io.github.clearkit.starter.app.ui.components.NavRow("诊断中心", "功能记录、冲突检查、维护模式、分享诊断报告", "打开", divider = false) {
            nav.open("diagnostics")
        }
        SectionLabel("桌面")
        // 图标的显示状态在系统里（组件开关），设置项只是记一份，迁移旧版“隐藏图标”时一起生效
        var visible by remember { mutableStateOf(LauncherIcon.visible(context)) }
        SwitchRow(m.hideLauncherIcon.title, m.hideLauncherIcon.summary, !visible, divider = false) { hide ->
            if (LauncherIcon.set(context, !hide).isSuccess) {
                visible = !hide
                app.settings.set(m.hideLauncherIcon, hide)
            }
        }
    }
}

/** 根权限：现在的状态和“获取 / 重新获取”，自动获取、离开时自动重开，放行后台运行。 */
@Composable
private fun RootSection() {
    val app = ModuleApp.instance
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state by collect(io.github.clearkit.starter.app.data.Root.state)
    val m = Settings.Module
    val busy = state == io.github.clearkit.starter.app.data.Root.State.CHECKING
    val granted = state == io.github.clearkit.starter.app.data.Root.State.GRANTED
    SectionLabel("根权限")
    io.github.clearkit.starter.app.ui.components.NavRow(
        "根权限：${state.label}",
        when (state) {
            io.github.clearkit.starter.app.data.Root.State.GRANTED -> "重开【宿主】、放行后台都直接用根权限完成"
            io.github.clearkit.starter.app.data.Root.State.DENIED -> "在 Magisk / KernelSU / APatch 里允许【模块】后点“重新获取”"
            io.github.clearkit.starter.app.data.Root.State.TIMEOUT -> "授权窗口弹出后没点，点“重新获取”再弹一次"
            io.github.clearkit.starter.app.data.Root.State.MISSING -> "没找到 su：KernelSU、APatch 要先在管理器的超级用户列表里允许【模块】；手机没有根权限的话这几项用不了，其它功能照常"
            else -> "点“获取”会弹出根权限授权窗口"
        },
        if (busy) "申请中" else if (granted) "重新检查" else if (state == io.github.clearkit.starter.app.data.Root.State.UNKNOWN) "获取" else "重新获取",
    ) {
        if (busy) return@NavRow
        scope.launch(kotlinx.coroutines.Dispatchers.IO) { io.github.clearkit.starter.app.data.Root.request() }
    }
    Toggle(m.autoRoot)
    Toggle(m.autoRestartHost)
    io.github.clearkit.starter.app.ui.components.NavRow(
        "现在重开【宿主】",
        if (granted) "强行停止后重新打开；另一个客户端在运行的话一起停掉"
        else "没有根权限时打开应用信息页，点“强行停止”后再打开",
        "重开",
    ) {
        val pkg = app.status.status.value?.hostPackage ?: io.github.clearkit.starter.app.data.HostControl.installed(context).firstOrNull()?.packageName ?: return@NavRow
        scope.launch { if (io.github.clearkit.starter.app.data.HostControl.restart(context, pkg)) app.settings.clearPendingRestart() }
    }
    io.github.clearkit.starter.app.ui.components.NavRow(
        "放行后台运行", "用根权限把【模块】加进省电白名单、允许后台运行和自启动，【宿主】唤醒它更稳（设置同步、适配、下载）", "放行", divider = false,
    ) {
        scope.launch {
            val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (io.github.clearkit.starter.app.data.Root.ensure()) io.github.clearkit.starter.app.data.Root.allowBackground(context.packageName) else -1
            }
            val text = when {
                n < 0 -> "没有根权限，放行不了；可以在系统设置里手动允许自启动、关掉省电限制"
                n == 0 -> "没放行成功：这台手机的系统不认这些命令"
                else -> "已放行 $n / ${io.github.clearkit.starter.app.data.Root.BACKGROUND_STEPS} 项（不支持的系统上个别项会失败，属于正常）"
            }
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
