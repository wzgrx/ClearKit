package io.github.clearkit.starter.hook.features.entries

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.clearkit.starter.hook.runtime.FeatureRuntime
import io.github.clearkit.starter.hook.runtime.ModuleLog
import io.github.clearkit.starter.hook.runtime.SettingsWriter
import io.github.clearkit.starter.hook.ui.HostToast
import io.github.clearkit.starter.hook.ui.SettingsSheet
import io.github.clearkit.starter.hook.ui.SettingsStore
import io.github.clearkit.starter.model.settings.Setting

/**
 * 打开【宿主】里的设置窗（[SettingsSheet]）：不用跳到 App 就能改设置。
 * 模板里从“模块已加载”提示上的“设置”按钮打开；想在宿主自己的设置页里加入口，参考 BiliClear 的 HostSettingsEntryFeature。
 */
object HostSettingsSheet {
    fun open(activity: Activity, scene: String? = null) {
        val runtime = FeatureRuntime.current ?: return
        runCatching { SettingsSheet(activity, Store(runtime), scene).show() }
            .onFailure { ModuleLog.w("settings sheet failed", it); ModuleLauncher.open(activity, "home") }
    }

    /** 设置窗的读写：读本进程的设置，写经桥接交给 App 校验保存。 */
    private class Store(private val runtime: FeatureRuntime) : SettingsStore {
        override fun value(setting: Setting<*>): Any = runtime.settings.value(setting)

        override fun write(setting: Setting<*>, value: Any?, done: (String?) -> Unit) {
            val app = runtime.application ?: return done("【宿主】还没准备好，稍后再试")
            val values = if (value == null) emptyMap() else mapOf(setting to value)
            val reset = if (value == null) listOf(setting) else emptyList()
            SettingsWriter.write(app, runtime.settings, values, reset) { r ->
                done(
                    when {
                        !r.reachable -> "没连上【模块】App：先打开一次 App（系统的自启动 / 关联启动要允许）"
                        r.rejected.isNotEmpty() -> r.rejected.values.first()
                        else -> null
                    },
                )
            }
        }

        override val pendingRestart: Set<String> get() = SettingsWriter.pendingRestart

        override fun restart(activity: Activity) {
            SettingsWriter.requestRootRestart(activity) { rooted ->
                if (rooted) return@requestRootRestart
                // 没有根权限：定一个马上打开【宿主】的闹钟，再结束进程
                val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                if (launch == null) { HostToast.show(activity, "请手动重开【宿主】"); return@requestRootRestart }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                val pending = PendingIntent.getActivity(activity, 0x6263, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
                runCatching { (activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 400, pending) }
                activity.finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }

        override fun openApp(activity: Activity, route: String?) = ModuleLauncher.open(activity, route ?: "home")
    }
}
