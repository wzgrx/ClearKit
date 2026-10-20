package io.github.clearkit.starter.app.ui.pages

import androidx.compose.runtime.Composable
import io.github.clearkit.starter.model.settings.FeatureCatalog

/**
 * 路由表：三个页签和二级页。从【宿主】打开时用的路由（`adapt`、`status`、`home`）也在这里。
 * 功能页（[FeatureCatalog.pages]）按设置分组自动生成，**加功能不用改这里**；只有自己写了特殊页面时才在 [Page] 里加一行。
 */
object Pages {
    val tabs = listOf("home", "features", "mine")

    /** 路由是不是页签；不是返回 null。 */
    fun tabOf(route: String): Int? = when (route) {
        "home" -> 0
        "features" -> 1
        "mine" -> 2
        else -> null
    }

    @Composable
    fun Tab(route: String) = when (route) {
        "features" -> FeaturesPage()
        "mine" -> MinePage()
        else -> HomePage()
    }

    @Composable
    fun Page(route: String) = when (route) {
        "adapt" -> AdaptPage()
        "quick" -> QuickEditPage()
        "changes" -> ChangesPage()
        "onboarding" -> OnboardingPage()
        "status" -> StatusPage()
        "module" -> ModuleSettingsPage()
        "about" -> AboutPage()
        "appearance" -> AppearancePage()
        "diagnostics" -> DiagnosticsPage()
        "diagnostics/settings" -> SettingsDetailPage()
        "compat" -> CompatPage()
        "conflict" -> ConflictPage()
        "maintenance" -> MaintenancePage()
        "update" -> UpdatePage()
        "search" -> SettingsSearchPage()
        "config" -> ConfigPage()
        "config/restore" -> ConfigRestorePage()
        "config/history" -> ConfigHistoryPage()
        "config/scope" -> ScopePage()
        else -> when {
            FeatureCatalog.byRoute(route) != null -> FeaturePage(route)
            route.startsWith("config/history/item") -> ConfigHistoryItemPage(route)
            route.startsWith("setting?key=") -> SettingDetailPage(route)
            else -> HomePage()
        }
    }
}

/** 路由里的参数（`page?key=xxx` 里的 xxx），按 URL 编码解。 */
fun routeParam(route: String, name: String): String? =
    route.substringAfter('?', "").split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
