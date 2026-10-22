package io.github.wzgrx.clearkit.ui.shell

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wzgrx.clearkit.ui.theme.Clear
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 页面转场参数，照 HyperOS 系统的 Activity 转场（小米窗口管理 AppTransitionInjector）：
 * 新页不透明、从右边整页滑入；下层左移 1/4 宽，盖上一层 0 → 0.5 的黑色遮罩；物理插值器（response 0.8、damping 0.95），500ms。
 * 页面本身不做透明度变化：没有“消失再浮现”的空档，也不用离屏合成。
 */
object PageMotion {
    const val DURATION = 500

    /** 下层左移的比例。 */
    const val PARALLAX = 0.25f

    /** 下层完全被盖住时遮罩的不透明度。 */
    const val DIM = 0.5f

    /** 小米的物理插值器：欠阻尼弹簧的位移曲线，[response] 秒一个周期、[damping] 阻尼比，在 0..1 的时间上走完。 */
    fun physicEasing(response: Float, damping: Float): Easing {
        val k = (2.0 * Math.PI / response).let { it * it }
        val c = damping * 4.0 * Math.PI / response
        val w = sqrt(4.0 * k - c * c) / 2.0
        val r = -c / 2.0
        val c2 = r / w
        return Easing { t -> (exp(r * t) * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat().coerceAtMost(1f) }
    }

    val Easing: Easing = physicEasing(response = 0.8f, damping = 0.95f)

    /** 从静止开始的整步进出。 */
    val Step: AnimationSpec<Float> = tween(DURATION, easing = Easing)

    /**
     * 离终点不到这么多（页）就算到位：栈顶页从这时起是 RESUMED（页面按它决定响应点击的，比如防连点返回）。
     * 物理曲线前 250ms 走完 92%，后面一半时间只是慢慢贴近终点，看不出还在动；等完全停住再响应，点了没反应像卡住。
     */
    const val SETTLED = 0.08f

    /** 中途改方向、手势松手：临界阻尼弹簧接着当前位置和速度走（单位是“页”，一整步约半秒停稳）。 */
    val Settle: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 146f, visibilityThreshold = 0.0025f)
}

/** 记下一张卡片在屏幕上的位置（根布局坐标），点开时传给 [Navigator.open]：新页从这张卡片展开，返回时缩回来。 */
class CardOrigin {
    var rect: Rect? = null
        internal set
}

/** 让 [origin] 一直记着这块的位置。 */
fun Modifier.cardOrigin(origin: CardOrigin): Modifier = this.onGloballyPositioned { origin.rect = it.boundsInRoot() }

/** 栈里的一页。[index] 是它在栈里的位置（页签页那一层是 0）；正在退出的页在动画走完前还留着。 */
private class StackEntry(val key: String, val route: String, var index: Int, val origin: Rect?) {
    var leaving = false
}

/** 记账：上一次的栈、还在退出的页。只在组合里按栈的内容算一次，同样的栈重复组合时直接给上次的结果。 */
private class Presented {
    private var last: List<Pair<String, String>>? = null
    private var entries: List<StackEntry> = emptyList()
    private val leaving = ArrayList<StackEntry>()
    var result: List<StackEntry> = emptyList()
        private set

    /** 换掉栈顶（[Navigator.replace]）：新页从栈顶前一格滑进来，旧页当作被盖住的那一页。 */
    var snapFrom: Float? = null

    /** [version] 只是让调用处读到它：退出的页走完后加一，重新组合时拿到去掉它们的结果。 */
    @Suppress("UNUSED_PARAMETER")
    fun reconcile(stack: List<String>, originOf: (String) -> Rect?, version: Int): List<StackEntry> {
        val desired = stack.mapIndexed { i, r -> pageKey(i + 1, r) to r }
        if (desired == last) return result
        val known = (entries + leaving).associateBy { it.key }
        val next = desired.mapIndexed { i, (k, r) -> (known[k] ?: StackEntry(k, r, i + 1, originOf(k))).also { it.index = i + 1; it.leaving = false } }
        val keys = next.mapTo(HashSet()) { it.key }
        leaving.removeAll { it.key in keys }
        val removed = entries.filter { it.key !in keys }
        val replaced = last != null && removed.size == 1 && next.size == entries.size && removed[0].index == next.size
        removed.forEach { it.leaving = true; if (replaced) it.index -= 1 }
        if (replaced) snapFrom = next.size - 1f
        leaving += removed
        entries = next
        last = desired
        // 画的顺序：位置小的在下；同一位置上正在退出的（被换掉的旧页）压在原来那页上面
        result = (next + leaving).sortedWith(compareBy<StackEntry> { it.index }.thenBy { it.leaving })
        return result
    }

    /** 动画走完：退出的页不再画，也不再组合。返回放掉的页的键。 */
    fun dropLeaving(): List<String> {
        if (leaving.isEmpty()) return emptyList()
        val keys = leaving.map { it.key }
        leaving.clear()
        result = entries
        return keys
    }
}

/** 每页的 ViewModel 存储，放在 Activity 的 ViewModel 里：转屏不丢；页离开返回栈时清掉。 */
internal class PageStores : ViewModel() {
    private val stores = HashMap<String, ViewModelStore>()
    fun of(key: String): ViewModelStore = stores.getOrPut(key) { ViewModelStore() }
    fun clear(key: String) { stores.remove(key)?.clear() }
    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/** 一页自己的生命周期（到位的栈顶 RESUMED，见 [PageMotion.SETTLED]；其余留在组合里的 STARTED，不超过 Activity 的）和 ViewModel 存储。 */
private class PageOwner(override val viewModelStore: ViewModelStore) : LifecycleOwner, ViewModelStoreOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

/**
 * 页面栈：[base] 是最底下那层（页签页，没有就传 null），[stack] 里的页依次压在上面。
 * 整条栈由一个浮点数（栈顶位置）驱动，每页的位置、遮罩都是它的函数，只在图层属性里读，转场每帧不重新组合、不重新测量。
 *
 * - 进出：新页先在屏幕外组合、排好、画好（这一帧可能慢），下一帧才开始滑，开头不会卡一下。
 *   用 [Navigator.open] 带了卡片位置的页从卡片展开，返回时缩回卡片。
 * - 系统返回手势（预测式返回）1:1 跟手；松手按速度接着走完或弹回。[gestureEnabled] 为假时（页面有离开确认）不跟手。
 * - 栈里的页都留在组合里（返回不重新组合，滚动位置、展开的分组都在）；被盖住并且停稳后不摆放：不画、不接触摸。
 *   离栈顶超过 [KEEP] 层的页才放掉组合（状态由 [saved] 记住）。
 * - 每页有自己的生命周期和 ViewModel 存储（同 Jetpack Navigation 的每个目的地）。
 */
@Composable
fun PageStack(
    stack: List<String>,
    saved: SaveableStateHolder,
    gestureEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    originOf: (String) -> Rect? = { null },
    reduceMotion: Boolean = Clear.reduceMotion,
    base: (@Composable (placed: State<Boolean>) -> Unit)? = null,
    page: @Composable (String) -> Unit,
) {
    val c = Clear.colors
    val presented = remember { Presented() }
    var version by remember { mutableIntStateOf(0) }
    val entries = presented.reconcile(stack, originOf, version)
    val target = stack.size.toFloat()
    val top = remember { Animatable(target) }
    val targetState = rememberUpdatedState(target)
    var gesture by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val corner = rememberScreenCorner()
    val stores: PageStores = viewModel()
    val parent = LocalLifecycleOwner.current
    val parentState = parent.lifecycle.currentStateFlow.collectAsState()
    // 栈在根布局里的位置（卡片位置是根布局坐标，换成栈里的）和宽度（返回手势按手指移过的比例走）
    val at = remember { mutableStateOf(Offset.Zero) }
    val width = remember { mutableIntStateOf(0) }

    fun dropped(keys: List<String>) {
        if (keys.isEmpty()) return
        keys.forEach(stores::clear)
        version++
    }

    LaunchedEffect(target, entries) {
        if (gesture) return@LaunchedEffect
        presented.snapFrom?.let { top.snapTo(it); presented.snapFrom = null }
        val v = release
        release = null
        val fromRest = v == null && abs(top.velocity) < 0.01f && abs(top.value - top.value.roundToInt()) < 0.01f
        val spec = when {
            reduceMotion -> snap()
            fromRest -> PageMotion.Step
            else -> PageMotion.Settle
        }
        if (top.value != target) top.animateTo(target, spec, initialVelocity = v ?: top.velocity)
        dropped(presented.dropLeaving())
    }

    PredictiveBackHandler(enabled = gestureEnabled) { events ->
        val start = targetState.value
        gesture = true
        var lastP = 0f
        var lastT = 0L
        var velocity = 0f
        var startX = Float.NaN
        try {
            events.collect { e ->
                // 按手指实际移过的距离（占栈宽的比例）走，1:1 跟手：系统报的进度在 HyperOS 上比手指快得多
                // （K90 实测手指才移 15% 屏宽，进度已经到 1）。拿不到宽度时退回系统进度。不到 1：完全拉开前还算“在拖”
                if (startX.isNaN()) startX = e.touchX
                val w = width.intValue
                val p = (if (w > 0) abs(e.touchX - startX) / w else e.progress).coerceIn(0f, 0.999f)
                val now = System.nanoTime()
                if (lastT != 0L) {
                    val dt = (now - lastT) / 1e9f
                    if (dt > 0f) velocity = 0.6f * velocity + 0.4f * ((p - lastP) / dt)
                }
                lastP = p
                lastT = now
                top.snapTo(if (reduceMotion) start else start - p)
            }
            // 松手提交：把手指的速度交给弹簧（栈顶位置在减小）；没拖过（返回键、三键导航）按整步走
            release = if (lastT == 0L) null else -velocity.coerceAtLeast(0f)
            gesture = false
            onBack()
        } catch (e: CancellationException) {
            gesture = false
            scope.launch {
                top.animateTo(start, if (reduceMotion) snap() else PageMotion.Settle)
                dropped(presented.dropLeaving())
            }
            throw e
        }
    }

    // 哪些位置上的页是从卡片打开的：它下面那一页被盖住时不平移
    val fromCard = entries.filter { it.origin != null }.mapTo(HashSet()) { it.index }
    Box(modifier.fillMaxSize().onGloballyPositioned { at.value = it.positionInRoot(); width.intValue = it.size.width }) {
        if (base != null) {
            key("base") {
                val (placed, shown) = rememberLayerState(0, top, targetState) { gesture }
                PageHost(0, "base", null, 1 in fromCard, placed, shown, top, targetState, { gesture }, corner, at, c.ground, stores, parentState) { base(placed) }
            }
        }
        entries.forEach { entry ->
            // 离栈顶太远的页放掉组合，状态留在 saved 里
            if (entry.index >= target - KEEP || entry.leaving) {
                key(entry.key) {
                    val (placed, shown) = rememberLayerState(entry.index, top, targetState) { gesture }
                    PageHost(entry.index, entry.key, entry.origin, (entry.index + 1) in fromCard, placed, shown, top, targetState, { gesture }, corner, at, c.ground, stores, parentState) {
                        CompositionLocalProvider(LocalPageRoute provides entry.route, LocalPageDepth provides entry.index) {
                            saved.SaveableStateProvider(entry.key) { page(entry.route) }
                        }
                    }
                }
            }
        }
    }
}

/** 栈顶以下保留组合的层数。 */
private const val KEEP = 4

/**
 * 这一层的两个状态（derivedStateOf：位置每帧都算，只有跨过边界时才通知）：
 * - placed：要不要摆放。离栈顶不到一格的都摆放，包括正好被栈顶盖住的那一页——它停着时透明度是 0（系统直接跳过不画），
 *   但排版和绘制记录都留着：返回时只要把透明度改回来，不用重新摆放、排版、录绘制（以前这一帧主线程 9–17ms）。
 *   再往下的页不摆放。
 * - shown：要不要画出来。在屏幕上、或者马上要露出来（正在拖返回、刚开始退出）。
 */
@Composable
private fun rememberLayerState(index: Int, top: Animatable<Float, *>, target: State<Float>, gesture: () -> Boolean): Pair<State<Boolean>, State<Boolean>> {
    val idx by rememberUpdatedState(index)
    return remember {
        derivedStateOf { (top.value - idx).let { it >= -1f && it <= 1.0005f } } to
            derivedStateOf {
                val d = top.value - idx
                (d >= -1f && d < 0.999f) || (d <= 1.0005f && (gesture() || abs(top.value - target.value) > 0.001f))
            }
    }
}

/**
 * 一层：自己的生命周期和 ViewModel 存储；自己画底色（转场时不透明）。
 * - 普通的页：按栈顶位置整页平移；被盖住时左移 1/4、盖遮罩；在最上面并且在动时按屏幕圆角裁剪。
 * - 从卡片打开的页（[origin]）：从卡片的位置和大小展开到整屏（按宽度等比缩放，裁成圆角矩形），下层不平移、只盖遮罩。
 * 被盖住停着的那一页摆着但透明度 0（不画，无障碍服务也看不到），再往下的不摆放；组合都还在。
 */
@Composable
private fun PageHost(
    index: Int,
    key: String,
    origin: Rect?,
    coveredByCard: Boolean,
    placed: State<Boolean>,
    shown: State<Boolean>,
    top: Animatable<Float, *>,
    target: State<Float>,
    gesture: () -> Boolean,
    corner: Float,
    at: State<Offset>,
    ground: Color,
    stores: PageStores,
    parentState: State<Lifecycle.State>,
    content: @Composable () -> Unit,
) {
    val owner = remember { PageOwner(stores.of(key)) }
    // 到位的栈顶 RESUMED（离终点不到 PageMotion.SETTLED），其余 STARTED；不超过 Activity 自己的状态
    LaunchedEffect(owner) {
        snapshotFlow {
            val settledTop = !gesture() && index.toFloat() == target.value && abs(top.value - target.value) < PageMotion.SETTLED
            minOf(parentState.value, if (settledTop) Lifecycle.State.RESUMED else Lifecycle.State.STARTED)
        }.collect { owner.registry.currentState = it }
    }
    DisposableEffect(owner) { onDispose { owner.registry.currentState = Lifecycle.State.DESTROYED } }
    val slide = remember(corner) { RoundedCornerShape(corner) }
    Box(
        Modifier.fillMaxSize().layout { measurable, constraints ->
            val p = measurable.measure(constraints)
            layout(p.width, p.height) {
                if (placed.value) {
                    p.placeWithLayer(0, 0) {
                        // 被盖住停着的那一页：透明度 0，系统跳过不画、也不报给无障碍服务
                        val hidden = !shown.value
                        val d = top.value - index
                        val w = p.width.toFloat()
                        val h = p.height.toFloat()
                        if (d <= 0f) {
                            val moving = d < -0.0005f
                            if (origin != null && moving) {
                                // 从卡片展开：q 从 0（卡片）到 1（整屏）
                                val q = (1f + d).coerceIn(0f, 1f)
                                val o = origin.translate(-at.value)
                                val s = lerp(o.width / w, 1f, q)
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = s
                                scaleY = s
                                translationX = lerp(o.left, 0f, q)
                                translationY = lerp(o.top, 0f, q)
                                // 缩放后的坐标里，裁成卡片高度到整屏高度之间的圆角矩形
                                val clipH = lerp(o.height / s, h, q)
                                val r = lerp(16f * density, corner, q) / s
                                shape = RectShape(clipH, r)
                                clip = true
                                // 小的时候和卡片长得不一样（顶上是页头）：展开开头、缩回结尾那 1/4 跟着大小淡入淡出，融进卡片
                                alpha = if (hidden) 0f else (q / 0.25f).coerceIn(0f, 1f)
                            } else {
                                // 进来、退出：整页平移（取整到像素，圆角边缘移动时不闪）
                                transformOrigin = TransformOrigin.Center
                                scaleX = 1f
                                scaleY = 1f
                                translationY = 0f
                                translationX = ((-d).coerceIn(0f, 1f) * w).roundToInt().toFloat()
                                alpha = if (hidden) 0f else 1f
                                shape = slide
                                clip = moving && corner > 0f
                            }
                        } else {
                            // 被盖住：上面那页从卡片展开时不平移，只压暗
                            translationX = if (coveredByCard) 0f else -PageMotion.PARALLAX * d.coerceIn(0f, 1f) * w
                            alpha = if (hidden) 0f else 1f
                            clip = false
                        }
                    }
                }
            }
        }
            // 每层都占住触摸：落在上面一页空白处的点按不会穿到下面（被盖住的页还摆着）
            .pointerInput(Unit) {},
    ) {
        CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner) {
            Box(Modifier.fillMaxSize().background(ground)) { content() }
        }
        // 被盖住时的遮罩：只改图层透明度，停在栈顶时是 0（不画）
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = PageMotion.DIM * (top.value - index).coerceIn(0f, 1f) }.background(Color.Black),
        )
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** 左上角起、宽度整页、高 [height] 的圆角矩形（缩放后的坐标）。 */
private class RectShape(private val height: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, size.width, height.coerceAtMost(size.height), CornerRadius(radius)))
}

/** 屏幕圆角半径（像素）：Android 12 起系统报告；报告不了就是 0（不裁）。 */
@Composable
private fun rememberScreenCorner(): Float {
    val view = LocalView.current
    return remember(view) {
        if (Build.VERSION.SDK_INT >= 31) {
            view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f
        } else 0f
    }
}
