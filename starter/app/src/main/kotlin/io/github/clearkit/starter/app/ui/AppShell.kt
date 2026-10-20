package io.github.clearkit.starter.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.github.clearkit.starter.app.ui.components.BcIcons
import io.github.clearkit.starter.app.ui.components.CardFlow
import io.github.clearkit.starter.app.ui.components.CloseCard
import io.github.clearkit.starter.app.ui.components.LocalCardFlow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ui.components.BottomTabs
import io.github.clearkit.starter.app.ui.components.PageHeader
import io.github.clearkit.starter.app.ui.pages.Pages
import io.github.clearkit.starter.app.ui.theme.Bc

/** 页面间跳转：[open] 压一页，[back] 退一页（栈底时退出单页入口）。 */
class Navigator(private val stack: MutableList<String>, private val onExit: (() -> Unit)?) {
    val top: String? get() = stack.lastOrNull()
    fun open(route: String) { guard = null; stack += route }
    /** 换掉当前页（不留在返回栈里）。 */
    fun replace(route: String) { if (stack.isEmpty()) stack += route else stack[stack.lastIndex] = route }
    /** 当前页的离开确认（设计 054）：返回 false 表示先不走（页面自己弹确认）。 */
    var guard: (() -> Boolean)? = null

    fun back() {
        if (guard?.invoke() == false) return
        force()
    }

    /** 不经过离开确认直接返回（确认“放弃修改”之后用）。 */
    fun force() {
        guard = null
        // 单页入口：退到入口页再返回就结束，回到【宿主】
        if (onExit != null && stack.size <= 1) onExit.invoke() else if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("no navigator") }

/**
 * App 外壳：底部三个页签（首页 / 功能 / 我的），二级页压在上面。
 * [entry] 不为空时是从【宿主】、通知打开的单页：只显示那一页，返回即 [onExit]。
 */
@Composable
fun AppShell(entry: String = "", onExit: (() -> Unit)? = null) {
    val single = entry.isNotEmpty() && Pages.tabOf(entry) == null
    val stack = remember { mutableStateListOf<String>().apply { if (single) add(entry) } }
    val nav = remember { Navigator(stack, if (single) onExit else null) }
    var tab by rememberSaveable { mutableIntStateOf(Pages.tabOf(entry) ?: 0) }
    BackHandler(enabled = stack.isNotEmpty() || tab != 0) {
        when {
            stack.isNotEmpty() -> nav.back()
            else -> tab = 0
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!single && io.github.clearkit.starter.app.ui.pages.needsOnboarding()) nav.open("onboarding")
    }
    CompositionLocalProvider(LocalNavigator provides nav) {
        Box(Modifier.fillMaxSize().background(Bc.colors.ground)) {
            val current = stack.lastOrNull()
            val reduceMotion = Bc.reduceMotion
            AnimatedContent(
                targetState = current to stack.size,
                transitionSpec = {
                    if (reduceMotion) return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None
                    val forward = targetState.second >= initialState.second
                    (slideInHorizontally(spring(stiffness = 500f)) { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally(spring(stiffness = 500f)) { if (forward) -it / 6 else it / 6 } + fadeOut())
                },
                label = "page",
            ) { (route, _) ->
                if (route == null) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { Pages.Tab(Pages.tabs[tab]) }
                        BottomTabs(
                            listOf("首页" to BcIcons.Home, "功能" to BcIcons.Grid, "我的" to BcIcons.Person),
                            selected = tab,
                        ) { tab = it }
                    }
                } else {
                    Pages.Page(route)
                }
            }
        }
    }
}

/** 嵌在场景页里时，页面只出内容（不出页头、不自己滚动）。 */
val LocalEmbedded = staticCompositionLocalOf { false }

/**
 * 一页的骨架：页头、可滚动的内容、可选的底部按钮。页签页没有返回键；二级页有。
 * 内容里的行按顺序拼成卡片（见 [CardFlow]）；嵌在场景页里（[LocalEmbedded]）时只出内容。
 */
@Composable
fun PageScaffold(
    title: String,
    subtitle: String,
    back: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (LocalEmbedded.current) {
        content()
        CloseCard()
        return
    }
    val nav = LocalNavigator.current
    // 有示意图的页（6.2）：默认“示意图”视图，示意图里已有的设置不再在下面重复列一遍；可切回完整列表
    val route = nav.top.orEmpty()
    val page = if (route == "ads") null else io.github.clearkit.starter.model.preview.PagePreviews.byRoute(route)
    val listMode = io.github.clearkit.starter.app.ui.components.PreviewMode.list
    // 只收有自己块的设置；只当总开关出现的（“净化消息中心”这类）列表里留着，关掉它要用
    val hidden = if (page == null || listMode) emptySet() else page.binds.map { it.setting.key }.toSet()
    Column(Modifier.fillMaxSize().background(Bc.colors.ground)) {
        PageHeader(title, subtitle, onBack = if (back) nav::back else null, actions = actions)
        if (page != null) io.github.clearkit.starter.app.ui.components.PreviewModeSwitch()
        CompositionLocalProvider(
            LocalCardFlow provides remember(listMode) { CardFlow() },
            io.github.clearkit.starter.app.ui.components.LocalDiagramKeys provides hidden,
        ) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
                content()
                CloseCard()
                Spacer(Modifier.height(16.dp))
            }
        }
        if (bottom != null) {
            if (back) Box(Modifier.navigationBarsPadding()) { bottom() } else bottom()
        } else if (back) {
            Box(Modifier.navigationBarsPadding())
        }
    }
}
