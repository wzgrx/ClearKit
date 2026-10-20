package io.github.clearkit.starter.hook.features.entries

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import io.github.clearkit.starter.hook.runtime.BridgeClient
import io.github.clearkit.starter.hook.runtime.ModuleLog
import io.github.clearkit.starter.model.bridge.Bridge

/** 从【宿主】打开模块 App 的某个页面：放进【宿主】当前的任务，按返回回到原页面。 */
object ModuleLauncher {
    fun open(activity: Activity, route: String) {
        val intent = Intent().setComponent(ComponentName(BridgeClient.MODULE_PACKAGE, Bridge.ROUTE_ACTIVITY))
            .putExtra(Bridge.EXTRA_ROUTE, route)
        runCatching { activity.startActivity(intent) }.onFailure { ModuleLog.w("module page $route not opened", it) }
    }
}
