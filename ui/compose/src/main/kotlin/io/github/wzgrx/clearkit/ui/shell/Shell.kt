package io.github.wzgrx.clearkit.ui.shell

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
import androidx.compose.ui.unit.IntOffset
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
import io.github.wzgrx.clearkit.ui.motion.Glass
import io.github.wzgrx.clearkit.ui.motion.LocalGlassBackdrop
import io.github.wzgrx.clearkit.ui.theme.Clear

/** 页签页底部要让出的高度（悬浮底栏 + 系统导航条）；二级页是 0。 */
val LocalTabBarInset = staticCompositionLocalOf { 0.dp }

/**
 * App 外壳（规范 v1 “框架”）：底部四个页签（首页 · 功能 · 特色页 · 我的），二级页压在上面。
 * 页签页上浮一条液态玻璃底栏，内容从它下面滚过去；二级页向前从右边推进来，返回往右退。
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
    val backdrop = rememberLayerBackdrop()
    val capture = Clear.glass && Glass.supported
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalNavigator provides nav) {
        Box(Modifier.fillMaxSize().background(c.ground)) {
            Box(Modifier.fillMaxSize().then(if (capture) Modifier.layerBackdrop(backdrop) else Modifier)) {
                val top = stack.lastOrNull()
                val reduceMotion = Clear.reduceMotion
                AnimatedContent(
                    targetState = top to stack.size,
                    transitionSpec = {
                        if (reduceMotion) return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None
                        val forward = targetState.second >= initialState.second
                        val motion = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 500f)
                        (slideInHorizontally(motion) { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally(motion) { if (forward) -it / 6 else it / 6 } + fadeOut())
                    },
                    label = "page",
                ) { (route, depth) ->
                    if (route == null) {
                        val t = tabs[current]
                        CompositionLocalProvider(LocalTabBarInset provides TabBarSize.space + navBar) {
                            saved.SaveableStateProvider("tab:${t.key}") { tab(t) }
                        }
                    } else {
                        saved.SaveableStateProvider(pageKey(depth, route)) { page(route) }
                    }
                }
            }
            if (stack.isEmpty()) {
                CompositionLocalProvider(LocalGlassBackdrop provides if (capture) backdrop else null) {
                    GlassTabBar(tabs, current, bottom = navBar, modifier = Modifier.align(Alignment.BottomCenter)) { current = it }
                }
            }
        }
    }
}

/**
 * 页头（规范 v1）：二级页是返回 + 左对齐标题 + 副标题（“35 项 · 按位置分组”）；页签页（[onBack] 为空）是大标题，
 * 右边的操作是浅灰圆底按钮。
 */
@Composable
fun PageHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Clear.colors
    val big = onBack == null
    Row(
        Modifier.fillMaxWidth().background(c.ground).statusBarsPadding()
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
    content: @Composable () -> Unit,
) {
    val nav = LocalNavigator.current
    val tabInset: Dp = if (back) 0.dp else LocalTabBarInset.current
    Column(Modifier.fillMaxSize().background(Clear.colors.ground)) {
        PageHeader(title, subtitle, onBack = if (back) nav::back else null, actions = actions)
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
            if (back) Box(Modifier.navigationBarsPadding()) { bottom() } else bottom()
        } else if (back) {
            Box(Modifier.navigationBarsPadding())
        }
    }
}
