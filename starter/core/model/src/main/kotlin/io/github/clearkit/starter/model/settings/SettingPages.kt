package io.github.clearkit.starter.model.settings

/**
 * 设置项在哪一页（App 的搜索、宿主里的设置窗共用）。页面 = 分组：键名前缀决定它在哪一页，
 * 所以**加设置不用改这里**；加了新分组时在 [FeatureCatalog.pages] 里登记一页。
 */
object SettingPages {
    /** 不在搜索和设置窗里出现的内部设置（令牌、记录这类由程序自己管的）。 */
    val INTERNAL: Set<String> by lazy {
        Settings.transient.map { it.key }.toSet() + setOf(
            "module.crashResetToken", "module.disabledFeatures", "module.quickToggles", "module.onboarded", "scope.accounts", "scope.edit",
        )
    }

    fun routeOf(key: String): String? = FeatureCatalog.pageOf(key)?.route ?: when (key.substringBefore('.')) {
        "module" -> "module"
        "appearance" -> "appearance"
        "scope" -> "config/scope"
        else -> null
    }

    val TITLES: Map<String, String> by lazy {
        FeatureCatalog.pages.associate { it.route to it.title } + mapOf("module" to "模块设置", "appearance" to "外观", "config/scope" to "配置作用域")
    }

    /** 分组的名字。 */
    fun groupLabel(key: String): String = TITLES[routeOf(key)] ?: "其他"

    /** 页面标题。 */
    fun pageTitle(key: String): String = routeOf(key)?.let { TITLES[it] } ?: groupLabel(key)
}
