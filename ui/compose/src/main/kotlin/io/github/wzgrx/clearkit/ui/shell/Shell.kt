package io.github.wzgrx.clearkit.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.wzgrx.clearkit.ui.components.CardFlow
import io.github.wzgrx.clearkit.ui.components.CloseCard
import io.github.wzgrx.clearkit.ui.components.IconButton
import io.github.wzgrx.clearkit.ui.components.LocalCardFlow
import io.github.wzgrx.clearkit.ui.components.LocalHideSectionLabels
import io.github.wzgrx.clearkit.ui.components.Text
import io.github.wzgrx.clearkit.ui.icons.ClearIcons
import io.github.wzgrx.clearkit.ui.motion.ClearMotion
import io.github.wzgrx.clearkit.ui.motion.Glass
import io.github.wzgrx.clearkit.ui.motion.LocalGlassBackdrop
import io.github.wzgrx.clearkit.ui.theme.Clear

/** 页签页底部要让出的高度（悬浮底栏 + 系统导航条）；二级页是 0。 */
val LocalTabBarInset = staticCompositionLocalOf { 0.dp }

/** 外壳里正在显示的那一屏：页签页（[route] 为空，[tab] 是第几个页签）或压在上面的二级页。 */
private data class Screen(val route: String?, val depth: Int, val tab: Int)

/**
 * App 外壳（规范 v1 “框架”）：底部四个页签（首页 · 功能 · 特色页 · 我的），二级页压在上面。
 * 页签页上浮一条液态玻璃底栏，内容从它下面滚过去；进二级页时底栏往下滑走，回来时滑上来。
 * 转场见 [ClearMotion]：二级页从右边推进来（旧页先淡出、新页再淡入，不重影），返回反向；页签之间 fade-through。
 * [entry] 是从宿主、通知打开的页：是页签就停在那个页签，不是就只显示那一页，返回即 [onExit]。
 * 每页的滚动位置、展开的分组用状态容器记住，返回时原样恢复。
 */
@Composable
fun ClearShell(
    tabs: List<ShellTab>,
    tabOf: (String) -> Int?,
    entry: String = "",
    onExit: (() -> Unit)? = null,
    onStart: (Navigator) -> Unit = {},
    tab: @Composable (ShellTab) -> Unit,
    page: @Composable (String) -> Unit,
) {
    val single = entry.isNotEmpty() && tabOf(entry) == null
    val stack = remember { mutableStateListOf<String>().apply { if (single) add(entry) } }
    val saved = rememberSaveableStateHolder()
    val nav = remember { Navigator(stack, if (single) onExit else null, saved::removeState) }
    var current by rememberSaveable { mutableIntStateOf(tabOf(entry) ?: 0) }
    BackHandler(enabled = stack.isNotEmpty() || current != 0) {
        if (stack.isNotEmpty()) nav.back() else current = 0
    }
    LaunchedEffect(Unit) { if (!single) onStart(nav) }
    val c = Clear.colors
    val reduceMotion = Clear.reduceMotion
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 底栏的显隐：弹簧，只在绘制层里读（页面不用重新测量）
    val barVisible = stack.isEmpty() && !single
    val barProgress = remember { Animatable(if (barVisible) 1f else 0f) }
    LaunchedEffect(barVisible, reduceMotion) {
        val target = if (barVisible) 1f else 0f
        if (reduceMotion) barProgress.snapTo(target) else barProgress.animateTo(target, if (barVisible) ClearMotion.BarShow else ClearMotion.BarHide)
    }
    val barShown by remember { derivedStateOf { barProgress.value > 0f } }
    // 液态玻璃要取页面内容：只在底栏露出来时取（二级页上不多画一遍）
    val backdrop = rememberLayerBackdrop()
    val capture = Clear.glass && Glass.supported && (barVisible || barShown)
    CompositionLocalProvider(LocalNavigator provides nav) {
        Box(Modifier.fillMaxSize().background(c.ground)) {
            Box(Modifier.fillMaxSize().then(if (capture) Modifier.layerBackdrop(backdrop) else Modifier)) {
                AnimatedContent(
                    targetState = Screen(stack.lastOrNull(), stack.size, current),
                    transitionSpec = {
                        when {
                            reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                            initialState.route == null && targetState.route == null -> ClearMotion.tabIn() togetherWith ClearMotion.tabOut()
                            targetState.depth >= initialState.depth -> ClearMotion.pushEnter() togetherWith ClearMotion.pushExit()
                            else -> ClearMotion.popEnter() togetherWith ClearMotion.popExit()
                        }
                    },
                    contentKey = { if (it.route == null) "tab:${it.tab}" else pageKey(it.depth, it.route) },
                    label = "page",
                ) { screen ->
                    // 每一页画自己的底色：转场时两页不会透明叠在一起
                    Box(Modifier.fillMaxSize().background(c.ground)) {
                        if (screen.route == null) {
                            val t = tabs[screen.tab.coerceIn(0, tabs.lastIndex)]
                            CompositionLocalProvider(LocalTabBarInset provides TabBarSize.space + navBar) {
                                saved.SaveableStateProvider("tab:${t.key}") { tab(t) }
                            }
                        } else {
                            saved.SaveableStateProvider(pageKey(screen.depth, screen.route)) { page(screen.route) }
                        }
                    }
                }
            }
            if (barVisible || barShown) {
                CompositionLocalProvider(LocalGlassBackdrop provides if (capture) backdrop else null) {
                    GlassTabBar(tabs, current, bottom = navBar, modifier = Modifier.align(Alignment.BottomCenter), progress = { barProgress.value }) { current = it }
                }
            }
        }
    }
}

/**
 * 页头（规范 v1）：二级页是返回 + 左对齐标题 + 副标题（“35 项 · 按位置分组”）；页签页（[onBack] 为空）是大标题，
 * 右边的操作是浅灰圆底按钮。
 * [statusBar] 为 false 时不再让出状态栏（外层已经让过，例如整个导航图统一让）。
 */
@Composable
fun PageHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null, statusBar: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Clear.colors
    val big = onBack == null
    Row(
        Modifier.fillMaxWidth().background(c.ground).then(if (statusBar) Modifier.statusBarsPadding() else Modifier)
            .padding(start = if (big) 20.dp else 4.dp, end = if (big) 16.dp else 8.dp, top = if (big) 10.dp else 4.dp, bottom = if (big) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(ClearIcons.Back, "返回", onBack)
            Spacer(Modifier.width(2.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = if (big) Clear.type.pageTitle else Clear.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = if (big) Clear.type.body.copy(fontSize = Clear.type.label.fontSize, lineHeight = Clear.type.label.lineHeight) else Clear.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(if (big) 8.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/**
 * 一页的骨架：页头、可滚动的内容、可选的底部按钮。页签页（[back] 为 false）没有返回键，底部让出悬浮底栏。
 * 内容里的行按顺序拼成卡片（见 [CardFlow]）；[flowKey] 变了重新开始拼。[top] 放在页头下、滚动区外（例如“示意图 / 列表”切换）。
 * 不用 [ClearShell] 的模块（自己有导航）传 [onBack]、[bottomInset]（列表底部要让出的高度），[statusBar] 为 false 时页头不再让状态栏。
 */
@Composable
fun PageFrame(
    title: String,
    subtitle: String,
    back: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    top: (@Composable () -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
    flowKey: Any? = null,
    hideSectionLabels: Boolean = false,
    onBack: (() -> Unit)? = null,
    statusBar: Boolean = true,
    bottomInset: Dp? = null,
    content: @Composable () -> Unit,
) {
    val backAction: (() -> Unit)? = if (!back) null else onBack ?: LocalNavigator.current.let { nav -> { nav.back() } }
    val tabInset: Dp = bottomInset ?: if (back) 0.dp else LocalTabBarInset.current
    Column(Modifier.fillMaxSize().background(Clear.colors.ground)) {
        PageHeader(title, subtitle, onBack = backAction, statusBar = statusBar, actions = actions)
        top?.invoke()
        CompositionLocalProvider(
            LocalCardFlow provides remember(flowKey) { CardFlow() },
            LocalHideSectionLabels provides hideSectionLabels,
        ) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
                content()
                CloseCard()
                Spacer(Modifier.height(16.dp + tabInset))
            }
        }
        if (bottom != null) {
            if (back && bottomInset == null) Box(Modifier.navigationBarsPadding()) { bottom() } else bottom()
        } else if (back && bottomInset == null) {
            Box(Modifier.navigationBarsPadding())
        }
    }
}
