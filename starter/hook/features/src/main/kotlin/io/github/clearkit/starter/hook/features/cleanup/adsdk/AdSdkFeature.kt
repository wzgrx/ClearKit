package io.github.clearkit.starter.hook.features.cleanup.adsdk

import io.github.clearkit.starter.hook.features.module.Evidence
import io.github.clearkit.starter.hook.runtime.Feature
import io.github.clearkit.starter.hook.runtime.FeatureScope
import io.github.clearkit.starter.hook.runtime.ModuleLog
import io.github.clearkit.starter.hook.runtime.NotInThisHost
import io.github.clearkit.starter.model.adsdk.AdSdks
import io.github.clearkit.starter.model.settings.Settings

/**
 * 通用去广告：第三方广告联盟 SDK 的“加载 / 展示 / 是否就绪”直接拦掉（[AdSdks]）。不用 DexAdapt 查询，任何宿主都能用；
 * 宿主没接入任何已知 SDK 时状态是“不支持”。拦哪些方法由 [AdSdks.actionFor] 决定（有单测）。
 */
object AdSdkFeature : Feature("cleanup.adSdk", "拦截广告联盟的广告") {
    override val switches = listOf(Settings.Cleanup.adSdk)

    override fun FeatureScope.install() {
        var hooked = 0
        for (name in AdSdks.ENTRY_CLASSES) {
            val type = runCatching { hostClassLoader.loadClass(name) }.getOrNull() ?: continue
            // 控件类整体跳过：拦了绘制相关的方法会出怪问题
            if (android.view.View::class.java.isAssignableFrom(type)) continue
            for (m in type.declaredMethods) {
                if (m.isSynthetic || m.isBridge) continue
                val action = AdSdks.actionFor(m.name, m.returnType.name) ?: continue
                hook(m) { call ->
                    if (!settings[Settings.Cleanup.adSdk]) return@hook call.proceed()
                    Evidence.acted(this@AdSdkFeature, settings)
                    when (action) {
                        AdSdks.Action.RETURN_FALSE -> false
                        AdSdks.Action.SKIP -> null
                    }
                }
                hooked++
            }
        }
        if (hooked == 0) throw NotInThisHost("这一版【宿主】没接入已知的广告联盟 SDK")
        ModuleLog.i("ad sdk: $hooked methods hooked")
    }
}
