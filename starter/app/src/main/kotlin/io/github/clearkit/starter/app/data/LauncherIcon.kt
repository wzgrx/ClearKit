package io.github.clearkit.starter.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 隐藏桌面图标：桌面入口是清单里的 activity-alias [ALIAS]（指向 MainActivity），隐藏 = 停用这个别名。
 * MainActivity 本身不动，LSPosed 管理器的模块入口、【宿主】里的入口照常能打开。
 */
object LauncherIcon {
    const val ALIAS = "io.github.clearkit.starter.app.Launcher"

    /** 组件状态 → 图标是否显示（默认状态按清单里的 enabled=true）。 */
    fun isVisible(state: Int): Boolean = when (state) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        -> false
        else -> true
    }

    private fun component(context: Context) = ComponentName(context.packageName, ALIAS)

    fun visible(context: Context): Boolean = isVisible(context.packageManager.getComponentEnabledSetting(component(context)))

    /** 显示或隐藏；系统没有生效或抛错时恢复原来的状态，返回失败原因。 */
    fun set(context: Context, visible: Boolean): Result<Unit> {
        val pm = context.packageManager
        val cn = component(context)
        val before = runCatching { pm.getComponentEnabledSetting(cn) }.getOrElse { return Result.failure(it) }
        return runCatching {
            val state = if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
            check(isVisible(pm.getComponentEnabledSetting(cn)) == visible) { "系统没有应用这项更改" }
        }.onFailure {
            android.util.Log.w("BcLauncher", "切换桌面图标失败", it)
            runCatching { pm.setComponentEnabledSetting(cn, before, PackageManager.DONT_KILL_APP) }
        }
    }
}
