package io.github.clearkit.starter.hook.features.example

import io.github.clearkit.starter.hook.features.net.NetRuleFeature
import io.github.clearkit.starter.model.example.ExampleRules
import io.github.clearkit.starter.model.net.JsonRule
import io.github.clearkit.starter.model.settings.Settings

/**
 * 示例：改接口数据去广告（最推荐的做法，见 ClearKit docs/11-cookbook.md「写一条 JSON 规则」）。
 *
 * 规则本身（判断哪条是广告、怎么删）是纯函数，写在 core/model 的 [ExampleRules] 里，有单测；这里只登记。
 * 换成真实宿主时：抓到接口返回的 JSON，把 [ExampleRules.MARKERS] 换成那份 JSON 里独有的字段名，改 [ExampleRules.removeAds]。
 */
object ExampleJsonRuleFeature : NetRuleFeature("cleanup.exampleAds", "示例：去掉接口里的广告") {
    /** 开关：开着才去广告。功能的开关和名单都要写在这里，App 据此显示“生效 / 关闭”，全关时功能不装。 */
    override val switches = listOf(Settings.Cleanup.exampleAds)
    override val listSwitches = listOf(Settings.Cleanup.exampleHide)

    override val rules = listOf(
        // 规则里先看自己的开关：同一个功能的两条规则各管各的
        JsonRule("example.feedAds", markers = ExampleRules.MARKERS) { data ->
            if (settings?.get(Settings.Cleanup.exampleAds) == true) ExampleRules.removeAds(data) else null
        },
        JsonRule("example.entries", markers = ExampleRules.ENTRY_MARKERS) { data ->
            ExampleRules.removeEntries(data, settings?.get(Settings.Cleanup.exampleHide).orEmpty())
        },
    )

    /** 规则在【宿主】的网络线程里跑：设置从运行时实时读（用户在 App 里改了马上生效）。 */
    private val settings get() = io.github.clearkit.starter.hook.runtime.FeatureRuntime.current?.settings
}
