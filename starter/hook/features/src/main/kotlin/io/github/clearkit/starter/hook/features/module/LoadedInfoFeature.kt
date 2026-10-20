package io.github.clearkit.starter.hook.features.module

import io.github.clearkit.starter.hook.features.entries.HostSettingsSheet
import io.github.clearkit.starter.hook.runtime.Feature
import io.github.clearkit.starter.hook.runtime.FeatureRuntime
import io.github.clearkit.starter.hook.runtime.FeatureScope
import io.github.clearkit.starter.hook.ui.HostToast
import io.github.clearkit.starter.model.settings.Settings

/**
 * “提示模块运行信息”：【宿主】第一个页面打开后提示一次模块已加载、适配的版本和生效的功能数；
 * 提示上的“设置”打开宿主里的设置窗。用通用的页面回调（不依赖宿主的类），所以没适配的版本也会提示。
 */
object LoadedInfoFeature : Feature("module.loadedInfo", "提示模块运行信息") {
    override val switches = listOf(Settings.Module.showInfo)
    override val keepInSafeMode = true

    @Volatile
    private var shown = false

    override fun FeatureScope.install() {
        onActivityResumed { activity ->
            if (shown) return@onActivityResumed
            shown = true
            activity.window.decorView.postDelayed({ show(activity) }, 1500)
        }
    }

    private fun show(activity: android.app.Activity) {
        if (activity.isFinishing) return
        val runtime = FeatureRuntime.current ?: return
        val status = runtime.status()
        val active = status.features.count { it.state == io.github.clearkit.starter.model.bridge.FeatureStatus.State.ACTIVE }
        val detail = if (status.adapted) "已适配【宿主】${runtime.versionName}，$active 个功能生效" else "这个版本还没适配，打开【模块】会自动适配"
        // 放在顶部，不挡宿主的底栏；适配卡片正在顶部时放底部，免得叠在一起
        HostToast.show(
            activity, "【模块】${runtime.moduleVersionName} 已加载", detail, "设置",
            top = !io.github.clearkit.starter.hook.ui.AdaptCard.showing,
        ) { HostSettingsSheet.open(activity) }
    }
}
