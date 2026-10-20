package io.github.clearkit.starter.hook.features

import io.github.clearkit.starter.hook.features.example.ExampleHookFeature
import io.github.clearkit.starter.hook.features.example.ExampleJsonRuleFeature
import io.github.clearkit.starter.hook.features.module.LoadedInfoFeature
import io.github.clearkit.starter.hook.features.net.NetPipeline
import io.github.clearkit.starter.hook.features.net.NetRuleFeature
import io.github.clearkit.starter.hook.runtime.Feature

/**
 * 【宿主】里的全部功能，按安装顺序。**写了新功能一定要登记在这里**，否则不会运行：
 * - 改接口数据的（继承 [NetRuleFeature]）放进 [netRules]；
 * - 其它（继承 [Feature]）放进 [list] 末尾的 listOf。
 */
object AllFeatures {
    /** 按接口数据净化的功能（规则登记到 [NetPipeline]，按这里的顺序执行）。 */
    val netRules: List<NetRuleFeature> = listOf(
        ExampleJsonRuleFeature,
    )

    val list: List<Feature> = run {
        NetPipeline.consumers = netRules
        listOf(
            LoadedInfoFeature,
            NetPipeline,
        ) + netRules + listOf(
            ExampleHookFeature,
        )
    }
}
