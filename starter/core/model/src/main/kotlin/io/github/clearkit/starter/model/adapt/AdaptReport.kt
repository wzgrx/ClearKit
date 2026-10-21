package io.github.clearkit.starter.model.adapt

import kotlinx.serialization.Serializable

/**
 * 一次适配（或这次启动）的结果，【宿主】里的适配卡片、启动提示和 App 的状态页都按它说话。
 * [found] / [total] 不含“这个渠道本来就没有”的符号；[restart] 是重开后才生效的功能名；[unavailable] 是用户开着、
 * 这一版却用不了（或只有部分效果）的功能和原因；[observing] 是推测出来、还在先观察的功能数。
 */
@Serializable
data class AdaptReport(
    val hostVersionName: String,
    val found: Int,
    val total: Int,
    val active: Int,
    val restart: List<String> = emptyList(),
    val unavailable: List<Issue> = emptyList(),
    val observing: Int = 0,
    /** 本机适配出来的（不是内置表）。 */
    val local: Boolean = false,
    /** 改接口数据的功能这次运行里才装好：已经显示出来的内容要下拉刷新才变（新加载的直接生效）。 */
    val refreshNeeded: Boolean = false,
) {
    @Serializable
    data class Issue(val title: String, val reason: String)

    enum class Tier { OK, RESTART, UNAVAILABLE }

    /** 有用不了的功能最要紧；其次是要重开；都没有就是全部正常。 */
    val tier: Tier
        get() = when {
            unavailable.isNotEmpty() -> Tier.UNAVAILABLE
            restart.isNotEmpty() -> Tier.RESTART
            else -> Tier.OK
        }
}
