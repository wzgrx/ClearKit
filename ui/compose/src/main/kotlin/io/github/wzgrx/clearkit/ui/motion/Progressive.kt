package io.github.wzgrx.clearkit.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos

/**
 * 一次排不完的长内容（示意图的几十个小块）分几帧显示：第一次进页面时先给 [first] 项，之后每帧多 [step] 项，直到 [total]。
 * 进子页的转场里新页前 90ms 本来就是透明的（[ClearMotion]），这样第一帧不会被一大块排版卡住，
 * 新页淡入前就已经画完。返回到这一页时（状态恢复）直接全部显示，滚动位置不会因为内容变短被挤掉。
 */
@Composable
fun rememberProgressiveCount(total: Int, first: Int = 1, step: Int = 1): Int {
    var shown by rememberSaveable { mutableIntStateOf(first) }
    LaunchedEffect(total) {
        while (shown < total) {
            withFrameNanos { }
            shown += step
        }
    }
    return shown.coerceAtMost(total)
}

/**
 * 和 [rememberProgressiveCount] 一样分帧放出，但每次这一页重新组合（返回到这一页、切页签回来）都重来一遍：
 * 导航不保留上一页，返回时整页要在转场的第一帧里重新组合，这一帧最容易掉。返回、切页签时新页开头同样是透明的。
 * [enabled] 只在第一次组合时问一次（不订阅它读到的状态，传滚动位置也不会每帧重组）；为假时（比如列表不在顶上，
 * 要按保存的位置恢复滚动）一次全给，免得内容变短把位置挤掉。
 */
@Composable
fun rememberStagedCount(total: Int, first: Int = 1, step: Int = 1, enabled: () -> Boolean = { true }): Int {
    var shown by remember { mutableIntStateOf(if (Snapshot.withoutReadObservation(enabled)) first else Int.MAX_VALUE) }
    LaunchedEffect(total) {
        while (shown < total) {
            withFrameNanos { }
            shown += step
        }
    }
    return shown.coerceAtMost(total)
}
