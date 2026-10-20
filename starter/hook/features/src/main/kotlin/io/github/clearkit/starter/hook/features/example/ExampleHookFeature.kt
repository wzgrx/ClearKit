package io.github.clearkit.starter.hook.features.example

import android.app.Activity
import io.github.clearkit.starter.hook.runtime.Feature
import io.github.clearkit.starter.hook.runtime.FeatureScope
import io.github.clearkit.starter.hook.ui.HostToast
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.symbols.example.ExampleSymbols

/**
 * 示例：用 DexAdapt 找到的方法挂钩子（见 ClearKit docs/11-cookbook.md「挂一个方法」）。
 *
 * 【宿主】启动页 onCreate 之后弹一条提示，显示设置里的数值。真实功能的套路一样：
 * 1. 在 core/symbols 里写查询（[ExampleSymbols]），在 SymbolCatalog.groups 登记；
 * 2. [requires] 写上必需的符号（缺了这个功能就不装，App 里显示“这个版本没适配”）；
 * 3. install 里 `hook(symbols.method(...)) { call -> ... call.proceed() ... }`。
 */
object ExampleHookFeature : Feature("misc.exampleToast", "示例：打开首页时提示") {
    override val switches = listOf(Settings.Misc.exampleToast)
    override val requires = listOf(ExampleSymbols.launchActivity, ExampleSymbols.launchOnCreate)

    override fun FeatureScope.install() {
        hook(symbols.method(ExampleSymbols.launchOnCreate)) { call ->
            val result = call.proceed()
            // 设置在钩子里实时读：用户在 App 里改了不用重开
            val n = settings[Settings.Misc.exampleCount]
            (call.thisObject as? Activity)?.let { HostToast.show(it, "示例功能生效了", "示例数值现在是 $n") }
            result
        }
    }
}
