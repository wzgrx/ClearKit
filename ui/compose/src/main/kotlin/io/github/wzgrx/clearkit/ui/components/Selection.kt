package io.github.wzgrx.clearkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wzgrx.clearkit.ui.icons.ClearIcons
import io.github.wzgrx.clearkit.ui.theme.Clear
import io.github.wzgrx.clearkit.ui.theme.PageGutter
import io.github.wzgrx.clearkit.ui.theme.Radius

/** 分段控件（同级切换，例如“示意图 / 列表”“任务 / 媒体库”）：14 圆角的槽，选中段是白底浮起。 */
@Composable
fun Segmented(items: List<String>, selected: Int, modifier: Modifier = Modifier, height: Dp = 32.dp, onSelect: (Int) -> Unit) {
    val c = Clear.colors
    Row(
        modifier.padding(horizontal = PageGutter).fillMaxWidth().clip(RoundedCornerShape(Radius.field)).background(c.seg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(height).then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp)).background(if (on) c.segOn else Color.Transparent)
                    .semantics { this.selected = on }.clickable(role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Clear.type.body, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) c.text else c.textSecondary, maxLines = 1)
            }
        }
    }
}

/** 筛选芯片（全部 / 开着的…）：选中是实心强调色，没选是细描边（规范 v1）。[count] 跟在文字后面。 */
@Composable
fun FilterPill(label: String, selected: Boolean, count: String? = null, onClick: () -> Unit) {
    val c = Clear.colors
    Row(
        Modifier.height(32.dp).clip(CircleShape).background(if (selected) c.accent else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.line, CircleShape))
            .semantics { this.selected = selected }.clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = Clear.type.caption.copy(fontSize = Clear.type.label.fontSize), color = if (selected) c.onAccent else c.textSecondary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        if (count != null) Text(count, style = Clear.type.caption, color = (if (selected) c.onAccent else c.textSecondary).copy(alpha = 0.75f), maxLines = 1)
    }
}

/** 一排可横向滚动的筛选芯片；[counts] 按位置给每个芯片的数字（“全部 6”“失败 1”）。 */
@Composable
fun FilterPills(items: List<String>, selected: Int, counts: List<String?> = emptyList(), onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PageGutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, s -> FilterPill(s, i == selected, counts.getOrNull(i)) { onSelect(i) } }
    }
}

/** 选项芯片：多选时选中带对勾、浅强调底，[dot] 是分类颜色（空降片段）。 */
@Composable
fun ClearChip(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val c = Clear.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.heightIn(min = 36.dp).clip(shape).background(if (selected) c.accentSoft else c.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.chipBorder, shape))
            .semantics { this.selected = selected }.clickable(role = Role.Checkbox, onClick = onClick)
            .padding(start = if (selected) 8.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(ClearIcons.Check, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = Clear.type.body, color = if (selected) c.accentInk else c.text, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

/** 假的搜索框（功能页顶上）：浅灰圆角条，点一下进搜索页。 */
@Composable
fun SearchBar(hint: String, onClick: () -> Unit) {
    val c = Clear.colors
    Row(
        Modifier.padding(horizontal = PageGutter).padding(bottom = 10.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(Radius.small)).background(c.muted)
            .clickable(role = Role.Button, onClickLabel = "搜索", onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ClearIcons.Search, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(hint, style = Clear.type.body, color = c.textTertiary, maxLines = 1)
    }
}
