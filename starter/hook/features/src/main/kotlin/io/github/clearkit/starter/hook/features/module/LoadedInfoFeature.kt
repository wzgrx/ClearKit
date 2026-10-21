package io.github.clearkit.starter.hook.features.module

import io.github.clearkit.starter.hook.features.entries.HostSettingsSheet
import io.github.clearkit.starter.hook.runtime.Feature
import io.github.clearkit.starter.hook.runtime.FeatureRuntime
import io.github.clearkit.starter.hook.runtime.FeatureScope
import io.github.clearkit.starter.hook.ui.AdaptCard
import io.github.clearkit.starter.model.adapt.AdaptCardText
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.model.settings.SettingsReader

/**
 * 启动提示（6.13）：【宿主】第一个页面出来后，在顶部卡片里说一行——
 * 平时“已加载 · n 项生效”；这个版本第一次冷启动时“已适配 x.y.z · 全部正常”；有功能用不了 / 要重开时用橙色写出来，
 * 点“看看”展开成完整的结果卡片。关了“提示模块运行信息”时平时不说，只有出问题才说。
 * 不依赖【宿主】的类（用页面回调），没适配好的版本也能提示；这次运行里弹过适配结果卡片就不再说。
 */
object LoadedInfoFeature : Feature("module.loadedInfo", "提示模块运行信息") {
    override val switches = listOf(Settings.Module.showInfo)
    override val keepInSafeMode = true

    /** 关了提示也要装：出问题时仍要告诉用户。 */
    override fun isOn(settings: SettingsReader): Boolean = true

    @Volatile
    private var shown = false

    override fun FeatureScope.install() {
        onActivityResumed { activity ->
            if (shown) return@onActivityResumed
            shown = true
            activity.window.decorView.postDelayed({ show(activity, settings[Settings.Module.showInfo]) }, 1500)
        }
    }

    private fun show(activity: android.app.Activity, showInfo: Boolean) {
        // 适配卡片正显示（这次运行在热适配）：结果卡片会说清楚，不再叠一条提示，也不用掉“第一次”的标记
        if (activity.isFinishing || AdaptCard.showing) return
        val runtime = FeatureRuntime.current ?: return
        val report = runtime.adaptReport()
        val first = runtime.consumeFirstForVersion()
        val (line, warn) = AdaptCardText.loadedLine(report, first)
        if (!showInfo && !warn) return
        AdaptCard.loaded(activity, "【模块】 ${runtime.moduleVersionName} 已加载", line, warn, if (warn) "看看" else "设置") {
            if (warn) AdaptCard.result(activity, report) else HostSettingsSheet.open(activity)
        }
    }
}
