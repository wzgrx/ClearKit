package io.github.wzgrx.clearkit.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import io.github.wzgrx.clearkit.ui.components.Text
import io.github.wzgrx.clearkit.ui.motion.ClearSprings
import io.github.wzgrx.clearkit.ui.motion.liquidGlass
import io.github.wzgrx.clearkit.ui.theme.Clear
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 底栏上的一个页签。 */
data class ShellTab(val key: String, val label: String, val icon: ImageVector)

/** 悬浮底栏的尺寸。页签页的列表底部要留出 [space]，最后一行不会被底栏挡住。 */
object TabBarSize {
    val height = 64.dp
    val side = 16.dp
    val gap = 12.dp
    /** 底栏占掉的高度（不含系统导航条）。 */
    val space = height + gap + 12.dp
}

/**
 * 液态玻璃底栏（规范 v1 “底栏”）：浮在页面上的胶囊，内容从下面滚过去。
 * - 选中项下面一颗“水滴”，换页签时用欠阻尼弹簧滑过去，滑得快时横向拉长、纵向压扁；
 * - 手指在底栏上左右拖，水滴跟着走，松手停在最近的一项并切过去。
 */
@Composable
fun GlassTabBar(tabs: List<ShellTab>, selected: Int, bottom: Dp, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val pill = remember { Animatable(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduce = Clear.reduceMotion
    LaunchedEffect(selected) { if (!dragging) { if (reduce) pill.snapTo(selected.toFloat()) else pill.animateTo(selected.toFloat(), ClearSprings.pill) } }
    val c = Clear.colors
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = TabBarSize.side, end = TabBarSize.side, bottom = bottom + TabBarSize.gap)
            .height(TabBarSize.height)
            .liquidGlass(Capsule())
            .pointerInput(tabs.size) {
                val step = { size.width.toFloat() / tabs.size }
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        val target = pill.value.roundToInt().coerceIn(0, tabs.lastIndex)
                        if (target != selected) onSelect(target) else scope.launch { pill.animateTo(selected.toFloat(), ClearSprings.pill) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { pill.animateTo(selected.toFloat(), ClearSprings.pill) }
                    },
                ) { change, dx ->
                    change.consume()
                    scope.launch { pill.snapTo((pill.value + dx / step()).coerceIn(-0.15f, tabs.lastIndex + 0.15f)) }
                }
            },
    ) {
        // 水滴：宽一格，按位置平移；速度越快拉得越长
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f / tabs.size)
                .graphicsLayer {
                    translationX = pill.value * size.width
                    val v = abs(pill.velocity)
                    val lift = if (dragging) 1.06f else 1f
                    scaleX = lift * (1f + (v * 0.035f).coerceAtMost(0.3f))
                    scaleY = lift * (1f - (v * 0.012f).coerceAtMost(0.12f))
                }
                .padding(5.dp)
                .background(c.accentSoft.copy(alpha = if (c.isDark) 0.9f else 0.95f), Capsule()),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, tab ->
                val on = i == selected
                val tint = if (on) c.accentInk else c.textSecondary
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(selected = on, onClick = { onSelect(i) }, role = Role.Tab),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(tab.label, color = tint, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
