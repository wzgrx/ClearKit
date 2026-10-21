package io.github.wzgrx.clearkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import io.github.wzgrx.clearkit.ui.icons.ClearIcons
import io.github.wzgrx.clearkit.ui.theme.CardPadding
import io.github.wzgrx.clearkit.ui.theme.CardRadius
import io.github.wzgrx.clearkit.ui.theme.Clear
import io.github.wzgrx.clearkit.ui.theme.ClearHue
import io.github.wzgrx.clearkit.ui.theme.PageGutter
import io.github.wzgrx.clearkit.ui.theme.Radius
import io.github.wzgrx.clearkit.ui.theme.Space

/** 首页上的区块标题（“常用开关”“快捷入口”“最近改动”）：卡片外一行大字，右边一个文字动作。 */
@Composable
fun BlockHeading(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    CloseCard()
    Row(
        Modifier.fillMaxWidth().padding(start = PageGutter + 4.dp, end = PageGutter - 6.dp, top = 4.dp, bottom = 6.dp).heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Clear.type.section, color = Clear.colors.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = Clear.type.body, fontWeight = FontWeight.Medium, color = Clear.colors.accentInk,
                modifier = Modifier.clip(RoundedCornerShape(Radius.block)).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** 状态卡上的浅色底（诊断按钮、分类小块）。 */
private val heroChip: Color @Composable get() = Color.White.copy(alpha = if (Clear.colors.isDark) 0.08f else 0.14f)

/**
 * 首页状态大卡（规范 v1 “首页”）：强调色底，一行状态（绿点 + “一切正常”）和右边的动作，
 * 下面一行宿主版本，再下面放大数字（[HeroNumber]）和分类小块（[HeroChips]）。
 */
@Composable
fun HeroCard(status: String, action: String? = null, onAction: (() -> Unit)? = null, line: String? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    CloseCard()
    val c = Clear.colors
    Column(
        Modifier.padding(horizontal = PageGutter).padding(bottom = Space.card).fillMaxWidth().clip(RoundedCornerShape(Radius.hero)).background(c.hero)
            .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (c.isDark) c.accent else Color(0xFF9FF0D9)))
            Spacer(Modifier.width(8.dp))
            Text(status, style = Clear.type.item, fontWeight = FontWeight.Bold, color = c.onHero, modifier = Modifier.weight(1f))
            if (action != null && onAction != null) {
                Row(
                    Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(heroChip).clickable(role = Role.Button, onClick = onAction)
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(action, style = Clear.type.label, color = c.onHero)
                    Icon(ClearIcons.Chevron, contentDescription = null, tint = c.onHero, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (line != null) Text(line, style = Clear.type.label, color = c.onHeroSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

/** 状态卡里的大数字：上面一句小字，数字后面跟单位。 */
@Composable
fun HeroNumber(label: String?, number: String, unit: String) {
    val c = Clear.colors
    Column {
        if (label != null) Text(label, style = Clear.type.label, color = c.onHeroSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(number, style = Clear.type.display, color = c.onHero)
            Spacer(Modifier.width(8.dp))
            Text(unit, style = Clear.type.body, color = c.onHero, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

/** 状态卡里的分类小块（“推荐流广告 142”）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroChips(items: List<Pair<String, String>>) {
    if (items.isEmpty()) return
    val c = Clear.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, n) ->
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(heroChip).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("$label ", style = Clear.type.caption, color = c.onHero)
                Text(n, style = Clear.type.caption, fontWeight = FontWeight.Bold, color = c.onHero)
            }
        }
    }
}

/** 两列方块排列（常用开关）：[tile] 拿到每格该用的 Modifier。 */
@Composable
fun <T> TileGrid(items: List<T>, columns: Int = 2, tile: @Composable (T, Modifier) -> Unit) {
    CloseCard()
    Column(Modifier.padding(horizontal = PageGutter).padding(bottom = Space.card), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 常用开关的一格：场景色图标 + 右上角开关，下面是名字和它在哪。整格点一下开关。 */
@Composable
fun ToggleTile(icon: ImageVector, hue: ClearHue, title: String, subtitle: String?, checked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Clear.colors
    Column(
        modifier.clip(RoundedCornerShape(CardRadius)).background(c.surface).clickable(role = Role.Switch, onClickLabel = title, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HueTile(icon, hue, 36.dp)
            Spacer(Modifier.weight(1f))
            ClearSwitch(checked)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Clear.type.item, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle.orEmpty(), style = Clear.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 快捷入口的一项。 */
data class QuickEntry(val key: String, val label: String, val icon: ImageVector)

/** 快捷入口：一张卡片里四列图标，点了去对应的页。 */
@Composable
fun QuickGrid(entries: List<QuickEntry>, onClick: (QuickEntry) -> Unit) {
    CloseCard()
    val c = Clear.colors
    Column(
        Modifier.padding(horizontal = PageGutter).padding(bottom = Space.card).fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(c.surface)
            .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entries.chunked(4).forEach { row ->
            Row {
                row.forEach { e ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(Radius.small)).clickable(role = Role.Button, onClickLabel = e.label) { onClick(e) }.padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(Radius.small)).background(c.muted), contentAlignment = Alignment.Center) {
                            Icon(e.icon, contentDescription = null, tint = c.text, modifier = Modifier.size(22.dp))
                        }
                        Text(e.label, style = Clear.type.caption, color = c.text, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 最近改动的一行：说明、时间，右边浅底的“撤销”。 */
@Composable
fun UndoRow(title: String, time: String, divider: Boolean, onUndo: () -> Unit) {
    val c = Clear.colors
    CardRow(divider) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = CardPadding, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Clear.type.item, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(time, style = Clear.type.caption, color = c.textSecondary)
            }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(Radius.field)).background(c.accentSoft).clickable(role = Role.Button, onClick = onUndo)
                    .padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(ClearIcons.Undo, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(16.dp))
                Text("撤销", style = Clear.type.label, fontWeight = FontWeight.Bold, color = c.accentInk)
            }
        }
    }
}
