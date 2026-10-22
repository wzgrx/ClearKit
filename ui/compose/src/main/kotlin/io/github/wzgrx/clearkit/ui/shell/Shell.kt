package io.github.wzgrx.clearkit.ui.shell

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
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
import io.github.wzgrx.clearkit.ui.motion.Glass
import io.github.wzgrx.clearkit.ui.motion.LocalGlassBackdrop
import io.github.wzgrx.clearkit.ui.theme.Clear
import kotlinx.coroutines.flow.drop

/** 页签页底部要让出的高度（悬浮底栏 + 系统导航条）；二级页是 0。 */
val LocalTabBarInset = staticCompositionLocalOf { 0.dp }

/** 回到页签的次数，见 [rememberTabReturns]。 */
private val LocalTabReturns = staticCompositionLocalOf<State<Int>> { mutableIntStateOf(0) }

/**
 * 从二级页回到这个页签的次数（每回来一次加一）。页签页一直留在组合里，返回时不重新组合；以前靠“打开页面时读一次”的数据
 * （不是数据流的，比如读一次配置文件、扫一遍媒体库）拿它当 remember / produceState 的键，回来时就会重新读。
 */
@Composable
fun rememberTabReturns(): Int = LocalTabReturns.current.value

/**
 * App 外壳（规范 v1 “框架”）：底部四个页签（首页 · 功能 · 特色页 · 我的），二级页压在上面。
 * 页签页上浮一条液态玻璃底栏，内容从它下面滚过去。转场照 HyperOS 系统的 Activity 转场（见 [PageStack]、[PageMotion]）：
 * 二级页从右边整页滑进来，下面一层（连同底栏）左移 1/4 并压暗；返回反过来，系统返回手势跟手。
 * 页签之间直接切换（和系统应用一样）；访问过的页签留在组合里，切回来不重新组合。
 * [entry] 是从宿主、通知打开的页：是页签就停在那个页签，不是就只显示那一页，返回即 [onExit]。
 * 每页的滚动位置、展开的分组都在（页留在组合里；放掉组合的也由状态容器记住）。页面要知道自己是哪页用 [LocalPageRoute]。
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
    // 返回：栈里有页时由页面栈处理（预测式返回跟手）；页面有离开确认时先问；单页入口在栈底时退出；页签页不在第一个时回第一个
    val guarded = nav.guard != null
    val root = if (single) 1 else 0
    BackHandler(enabled = stack.isEmpty() && current != 0) { current = 0 }
    BackHandler(enabled = guarded || (single && stack.size <= root)) { nav.back() }
    LaunchedEffect(Unit) { if (!single) onStart(nav) }
    val c = Clear.colors
    val reduceMotion = Clear.reduceMotion
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 从二级页回到页签的次数（见 rememberTabReturns）
    val returns = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { snapshotFlow { stack.isEmpty() }.drop(1).collect { if (it) returns.intValue++ } }
    CompositionLocalProvider(LocalNavigator provides nav) {
        Box(Modifier.fillMaxSize().background(c.ground)) {
            PageStack(
                stack = stack,
                saved = saved,
                gestureEnabled = !guarded && stack.size > root,
                onBack = { nav.back() },
                originOf = nav::originOf,
                reduceMotion = reduceMotion,
                base = if (single) null else { visible ->
                    CompositionLocalProvider(LocalTabReturns provides returns) {
                        TabBase(tabs, current, visible, TabBarSize.space + navBar, navBar, onSelect = { current = it }) { t ->
                            saved.SaveableStateProvider("tab:${t.key}") { tab(t) }
                        }
                    }
                },
                page = page,
            )
        }
    }
}

/**
 * 页签那一层（页面栈的最底层）：当前页签和它上面浮着的液态玻璃底栏，压上二级页时一起左移、压暗。
 * 访问过的页签都留在组合里，不是当前的不摆放（不画、不接触摸）；切页签只是换一个摆放，不重新组合。
 * 玻璃底栏要取页签内容做背景：这一层不在屏幕上（被二级页盖住停稳了）时不取。
 */
@Composable
private fun TabBase(
    tabs: List<ShellTab>,
    current: Int,
    visible: State<Boolean>,
    inset: Dp,
    navBar: Dp,
    onSelect: (Int) -> Unit,
    tab: @Composable (ShellTab) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val capture = Clear.glass && Glass.supported && visible.value
    val visited = remember { mutableStateListOf(current) }
    if (current !in visited) visited += current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (capture) Modifier.layerBackdrop(backdrop) else Modifier)) {
            visited.forEach { i ->
                key(i) {
                    val shown by rememberUpdatedState(i == current)
                    Box(
                        Modifier.fillMaxSize().layout { measurable, constraints ->
                            val p = measurable.measure(constraints)
                            layout(p.width, p.height) { if (shown) p.place(0, 0) }
                        },
                    ) {
                        CompositionLocalProvider(LocalTabBarInset provides inset) { tab(tabs[i.coerceIn(0, tabs.lastIndex)]) }
                    }
                }
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides if (capture) backdrop else null) {
            GlassTabBar(tabs, current, bottom = navBar, modifier = Modifier.align(Alignment.BottomCenter)) { onSelect(it) }
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
