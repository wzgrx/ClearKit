package io.github.wzgrx.clearkit.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wzgrx.clearkit.ui.theme.CardPadding
import io.github.wzgrx.clearkit.ui.theme.Clear
import io.github.wzgrx.clearkit.ui.theme.PageGutter
import io.github.wzgrx.clearkit.ui.theme.Radius
import io.github.wzgrx.clearkit.ui.theme.Space

/** 细进度条：浅灰轨道上一段强调色。 */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val c = Clear.colors
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.muted)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.accent))
    }
}

/**
 * 进度卡（“正在下载 2 个”）：图标、标题、右边速度，下面一行当前在做的和进度条；可带一个按钮（“全部停止”）。
 */
@Composable
fun ProgressCard(icon: ImageVector, title: String, trailing: String?, line: String?, progress: Float?, action: String? = null, onAction: (() -> Unit)? = null) {
    CloseCard()
    val c = Clear.colors
    Column(
        Modifier.padding(horizontal = PageGutter).padding(bottom = Space.card).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = Clear.type.body, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = Clear.type.caption, color = c.textSecondary)
        }
        if (line != null) Text(line, style = Clear.type.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (progress != null) ProgressBar(progress)
        if (action != null && onAction != null) PillButton(action, onAction, style = PillStyle.Tonal, height = 36.dp)
    }
}

/** 封面右下角的小黑标（时长、页数）。 */
@Composable
fun BoxScope.CoverBadge(text: String) {
    Text(
        text, fontSize = 11.sp, lineHeight = 18.sp, color = Color.White, maxLines = 1,
        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp),
    )
}

/**
 * 封面卡（媒体库、下载的帖子）：上面是封面（[cover] 自己画，可用 [CoverBadge] 标时长），下面两行标题和一行说明。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverCard(
    title: String, meta: String, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
    cover: @Composable BoxScope.() -> Unit,
) {
    val c = Clear.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.small)).background(c.surface)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().background(c.muted), contentAlignment = Alignment.Center, content = cover)
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Clear.type.label, fontWeight = FontWeight.Bold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, fontSize = 11.sp, lineHeight = 15.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 两列（或 [columns] 列）错落排列：依次分到各列，每列从上往下排。放在页面内容里，不另外滚动。 */
@Composable
fun <T> MasonryColumns(items: List<T>, columns: Int = 2, item: @Composable (T) -> Unit) {
    CloseCard()
    Row(Modifier.padding(horizontal = PageGutter).padding(bottom = Space.card), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        repeat(columns) { col ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.filterIndexed { i, _ -> i % columns == col }.forEach { item(it) }
            }
        }
    }
}

/**
 * 带缩略图的一行（下载任务）：左边缩略图，标题、说明，可选进度条和进度文字、状态标签，右边一个动作。
 */
@Composable
fun ThumbRow(
    title: String,
    summary: String?,
    divider: Boolean,
    thumb: @Composable () -> Unit,
    progress: Float? = null,
    progressText: String? = null,
    tag: Pair<String, Tone>? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Clear.colors
    CardRow(divider) {
        Row(
            (if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).fillMaxWidth().heightIn(min = 64.dp)
                .padding(start = CardPadding - 4.dp, end = CardPadding - 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            thumb()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = Clear.type.item, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (tag != null) {
                        Spacer(Modifier.width(6.dp))
                        Badge(tag.first, tag.second)
                    }
                }
                if (!summary.isNullOrEmpty()) Text(summary, style = Clear.type.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    ProgressBar(progress)
                    if (progressText != null) Text(progressText, style = Clear.type.caption, color = c.textSecondary)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/** 缩略图的框：16:10 小图，圆角，浅灰底；[content] 放图片或图标，[badge] 标在右下角。 */
@Composable
fun ThumbBox(badge: String? = null, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(width = 76.dp, height = 48.dp).clip(RoundedCornerShape(Radius.block)).background(Clear.colors.muted), contentAlignment = Alignment.Center) {
        content()
        if (badge != null) {
            Text(
                badge, fontSize = 10.sp, lineHeight = 14.sp, color = Color.White, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp),
            )
        }
    }
}
