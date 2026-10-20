package io.github.clearkit.starter.model.settings

/**
 * App“功能”页的目录：每页对应一个设置分组，按用途排（去广告、精简、过滤、增强……）。
 * **加一个新分组时**：在 [Settings] 里写分组，在这里加一行 [Page]，在 PagePreviews 里画这一页的示意图。
 * 页面内容（示意图 + 设置行）由 App 按分组自动生成。
 */
object FeatureCatalog {
    data class Page(
        /** 路由，也是示意图的键（PagePreviews 里同名）。 */
        val route: String,
        val title: String,
        /** 一句话说明，显示在功能页的列表里。 */
        val summary: String,
        val group: SettingGroup,
    )

    val pages: List<Page> = listOf(
        Page("cleanup", "去广告与精简", "【宿主】里的广告、推广和用不上的入口", Settings.Cleanup),
        Page("misc", "增强", "【宿主】没有的小功能", Settings.Misc),
    )

    fun byRoute(route: String): Page? = pages.firstOrNull { it.route == route }

    fun pageOf(key: String): Page? = pages.firstOrNull { key.startsWith(it.group.prefix + ".") }
}
