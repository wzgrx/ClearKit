package io.github.wzgrx.clearkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.wzgrx.clearkit.ui.icons.ClearIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.wzgrx.clearkit.ui.theme.CardPadding
import io.github.wzgrx.clearkit.ui.theme.CardRadius
import io.github.wzgrx.clearkit.ui.theme.Clear
import io.github.wzgrx.clearkit.ui.theme.PageGutter
import io.github.wzgrx.clearkit.ui.theme.Radius
import io.github.wzgrx.clearkit.ui.theme.Space

/**
 * 卡片的拼接状态：页面里一串行（开关行、导航行……）按出现顺序拼成一张卡片。
 * 分组标题打开一张卡片，`divider = false` 的行把它收口；独立的块（提示条、摘要卡）出现前先收口。
 * 每个行在第一次组合时记住自己是不是卡片的第一行，之后重组不再变。
 */
class CardFlow {
    var open: Boolean = false
}

val LocalCardFlow = staticCompositionLocalOf { CardFlow() }

/** 已经在卡片的一段里面（里面的输入框、提示不再自己套一层）。 */
val LocalInCard = staticCompositionLocalOf { false }

/**
 * 分组标题要不要画：模块的“示意图”视图里列表大多收起时为 true，标题不出，剩下的行接在一起。
 * 由页面骨架提供。
 */
val LocalHideSectionLabels = staticCompositionLocalOf { false }

/** 卡片的一段：[top] 时上边是圆角，[bottom] 时下边是圆角。 */
@Composable
fun CardSegment(top: Boolean, bottom: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(
        topStart = if (top) CardRadius else 0.dp, topEnd = if (top) CardRadius else 0.dp,
        bottomStart = if (bottom) CardRadius else 0.dp, bottomEnd = if (bottom) CardRadius else 0.dp,
    )
    Column(Modifier.padding(horizontal = PageGutter).fillMaxWidth().clip(shape).background(Clear.colors.surface)) {
        CompositionLocalProvider(LocalInCard provides true) { content() }
    }
    if (bottom) Spacer(Modifier.height(Space.card))
}

/** 一行放进当前卡片：自动判断首行（上圆角），[divider] 为 false 时收口（下圆角）。 */
@Composable
fun CardRow(divider: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val flow = LocalCardFlow.current
    val top = remember { !flow.open }
    flow.open = divider
    CardSegment(top, !divider) {
        content()
        if (divider) HorizontalDivider(Modifier.padding(horizontal = CardPadding), color = Clear.colors.line)
    }
}

/** 收口：前面还开着的卡片补一个下圆角的尾巴。独立的块出现前调用。 */
@Composable
fun CloseCard() {
    val flow = LocalCardFlow.current
    val wasOpen = remember { flow.open }
    flow.open = false
    if (wasOpen) CardSegment(top = false, bottom = true) { Spacer(Modifier.height(6.dp)) }
}

/** 把自定义内容放进当前卡片（不画分隔线），比如卡片里的说明、chips、进度条。 */
@Composable
fun InCard(last: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val flow = LocalCardFlow.current
    val top = remember { !flow.open }
    flow.open = !last
    CardSegment(top, last) { Column(Modifier.fillMaxWidth().padding(horizontal = CardPadding, vertical = 10.dp), content = content) }
}

/**
 * 分组标题（规范 v1）：写在卡片外、左上方的一行小字，下面的行拼成一张新卡片。
 * 示意图视图里列表收起时（[LocalHideSectionLabels]）不画，剩下的行接在一起。
 */
@Composable
fun SectionLabel(text: String, action: String? = null, onAction: (() -> Unit)? = null, always: Boolean = false) {
    if (LocalHideSectionLabels.current && !always) return
    CloseCard()
    LocalCardFlow.current.open = false
    if (action == null || onAction == null) {
        Text(text, style = Clear.type.label, color = Clear.colors.textSecondary, modifier = Modifier.padding(horizontal = PageGutter + 4.dp).padding(top = 2.dp, bottom = 8.dp))
        return
    }
    // 右边带一个文字动作（“清除记录”“全部”）
    Row(Modifier.fillMaxWidth().padding(start = PageGutter + 4.dp, end = PageGutter - 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Clear.type.label, color = Clear.colors.textSecondary, modifier = Modifier.weight(1f).padding(top = 2.dp, bottom = 8.dp))
        Text(
            action, style = Clear.type.label, color = Clear.colors.accentInk,
            modifier = Modifier.clip(RoundedCornerShape(Radius.block)).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 卡片外的分组标题（首页“常用开关”这类）：一行字，下面接独立的块。 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    CloseCard()
    Text(text, style = Clear.type.label, color = Clear.colors.textSecondary, modifier = modifier.padding(horizontal = PageGutter + 4.dp).padding(top = 4.dp, bottom = 8.dp))
}

/** 页面顶部的摘要卡：小标签、大标题、一句说明（强调浅底）。 */
@Composable
fun SummaryCard(label: String, title: String, body: String, modifier: Modifier = Modifier) {
    CloseCard()
    val c = Clear.colors
    Column(
        modifier.padding(horizontal = PageGutter).padding(bottom = Space.card).fillMaxWidth().clip(RoundedCornerShape(Radius.hero)).background(c.accentSoft)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(label, style = Clear.type.caption, color = c.accentInk)
        Spacer(Modifier.height(4.dp))
        Text(title, style = Clear.type.section, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(body, style = Clear.type.body, color = c.textSecondary)
    }
}

/** 独立的一张卡片（不参与拼接）：首页、我的这些页上的块。 */
@Composable
fun Card(modifier: Modifier = Modifier, padding: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    CloseCard()
    Column(
        modifier.padding(horizontal = PageGutter).padding(bottom = Space.card).fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(Clear.colors.surface)
            .then(if (padding) Modifier.padding(horizontal = CardPadding, vertical = 14.dp) else Modifier),
        content = content,
    )
}

/**
 * 入口卡（“我的”里的记录、足迹）：强调浅底的图标、标题、一句说明和箭头，整张可点；下面可放缩略图、计数小块（[CountChip]）。
 */
@Composable
fun EntryCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit = {}) {
    CloseCard()
    val c = Clear.colors
    Column(
        Modifier.padding(horizontal = PageGutter).padding(bottom = Space.card).fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(c.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(Radius.icon)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Clear.type.itemStrong, color = c.text)
                Text(subtitle, style = Clear.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(ClearIcons.Chevron, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
        }
        content()
    }
}

/** 计数小块：浅灰胶囊里“名字 数字”，点了去对应的页。 */
@Composable
fun CountChip(label: String, count: String, onClick: () -> Unit) {
    val c = Clear.colors
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(c.muted).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = Clear.type.label, color = c.text)
        Text(count, style = Clear.type.label, color = c.textSecondary)
    }
}

/**
 * 一页的摘要（规范 v1 “设置页”）：“已开 30 / 35”和一句说明（改过默认值几项、几项要重开），右边可放一两个按钮（“全部打开”“撤销”）。
 */
@Composable
fun PageSummary(title: String, detail: String?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    CloseCard()
    val c = Clear.colors
    Row(
        modifier.padding(horizontal = PageGutter).padding(bottom = Space.card).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
            .heightIn(min = 56.dp).padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Clear.type.itemStrong, color = c.text)
            if (!detail.isNullOrEmpty()) Text(detail, style = Clear.type.caption, color = c.textSecondary)
        }
        actions()
    }
}

/** 页脚留白（列表最后一行和底部按钮之间）。 */
@Composable
fun ColumnScope.PageEnd() {
    CloseCard()
    Spacer(Modifier.height(24.dp))
}
