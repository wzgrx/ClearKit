package io.github.clearkit.starter.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ui.theme.Bc
import io.github.clearkit.starter.app.ui.theme.Hue

/** 页面左右留白：卡片、提示条、按钮都按这个对齐。 */
val PageGutter = 16.dp

/** 卡片内的左右留白。 */
val CardPadding = 16.dp

/** 卡片圆角。 */
val CardRadius = 20.dp

/**
 * 卡片的拼接状态：页面里一串行（开关行、导航行……）按出现顺序拼成一张卡片。
 * 分组标题打开一张卡片，`divider = false` 的行把它收口；独立的块（提示条、摘要卡）出现前先收口。
 * 每个行在第一次组合时记住自己是不是卡片的第一行，之后重组不再变。
 */
class CardFlow {
    var open: Boolean = false
}

val LocalCardFlow = staticCompositionLocalOf { CardFlow() }

/** 卡片的一段：[top] 时上边是圆角，[bottom] 时下边是圆角。 */
@Composable
fun CardSegment(top: Boolean, bottom: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(
        topStart = if (top) CardRadius else 0.dp, topEnd = if (top) CardRadius else 0.dp,
        bottomStart = if (bottom) CardRadius else 0.dp, bottomEnd = if (bottom) CardRadius else 0.dp,
    )
    Column(Modifier.padding(horizontal = PageGutter).fillMaxWidth().clip(shape).background(Bc.colors.surface)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalInCard provides true) { content() }
    }
    if (bottom) Spacer(Modifier.height(12.dp))
}

/** 已经在卡片的一段里面（里面的输入框、提示不再自己套一层）。 */
val LocalInCard = staticCompositionLocalOf { false }

/** 一行放进当前卡片：自动判断首行（上圆角），[divider] 为 false 时收口（下圆角）。 */
@Composable
fun CardRow(divider: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val flow = LocalCardFlow.current
    val top = remember { !flow.open }
    flow.open = divider
    CardSegment(top, !divider) {
        content()
        if (divider) HorizontalDivider(Modifier.padding(horizontal = CardPadding), color = Bc.colors.line)
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
 * 页头：二级页是返回 + 标题 + 副标题；页签页（[onBack] 为空）是大标题。右边可放操作（[actions]）。
 */
@Composable
fun PageHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Bc.colors
    Row(
        Modifier.fillMaxWidth().background(c.ground).statusBarsPadding()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = if (onBack != null) 4.dp else 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(BcIcons.Back, "返回", onBack)
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = if (onBack != null) Bc.type.sceneTitle else Bc.type.pageTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = Bc.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** 圆形图标按钮（48 触摸区）。[filled] 时白底描边。 */
@Composable
fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit, filled: Boolean = false, tint: Color? = null) {
    val c = Bc.colors
    val base = Modifier.size(48.dp).clip(CircleShape)
    val styled = if (filled) base.background(c.surface).border(1.dp, c.line, CircleShape) else base
    Box(styled.clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint ?: c.text, modifier = Modifier.size(22.dp))
    }
}

/** 分组标题：开一张新卡片，标题写在卡片顶部。 */
@Composable
fun SectionLabel(text: String) {
    // 示意图视图里列表大多收起，分组标题也不出（剩下的行接在一起）
    if (LocalDiagramKeys.current.isNotEmpty()) return
    CloseCard()
    val flow = LocalCardFlow.current
    flow.open = true
    Spacer(Modifier.height(4.dp))
    CardSegment(top = true, bottom = false) {
        Text(text, style = Bc.type.sectionLabel, color = Bc.colors.textSecondary, modifier = Modifier.padding(start = CardPadding, end = CardPadding, top = 14.dp, bottom = 2.dp))
    }
}

/** 页面顶部的摘要卡：小标签、大标题、一句说明（强调浅底）。 */
@Composable
fun SummaryCard(label: String, title: String, body: String, modifier: Modifier = Modifier) {
    CloseCard()
    val c = Bc.colors
    Column(
        modifier.padding(horizontal = PageGutter).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.accentSoft)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(label, style = Bc.type.caption, color = c.accentInk)
        Spacer(Modifier.height(4.dp))
        Text(title, style = Bc.type.cardTitle, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(body, style = Bc.type.body, color = c.textSecondary)
    }
}

/** 一行：标题、说明、右边的值或控件；可点时整行可点。卡片里行与行之间画分隔线（[divider]），最后一行传 false 收口。 */
@Composable
fun ListRow(
    title: String,
    summary: String? = null,
    divider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role? = Role.Button,
    badge: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Bc.colors
    val dense = Bc.compact
    CardRow(divider) {
        val click = if (onClick != null && enabled) Modifier.clickable(role = role, onClick = onClick) else Modifier
        Row(
            click.fillMaxWidth().heightIn(min = if (dense) 48.dp else 60.dp).padding(horizontal = CardPadding, vertical = if (dense) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                // 标记（重开生效、即将支持）跟在标题后面，不再单独占一行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = Bc.type.itemTitle, color = if (enabled) c.text else c.textDisabled, modifier = Modifier.weight(1f, fill = false))
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Badge(badge)
                    }
                }
                if (!summary.isNullOrEmpty()) {
                    // 高密度时说明只显示一行；被截断时点说明展开
                    var expanded by remember(summary) { mutableStateOf(false) }
                    var cut by remember(summary) { mutableStateOf(false) }
                    Text(
                        summary, style = Bc.type.caption, color = if (enabled) c.textSecondary else c.textDisabled,
                        maxLines = if (dense && !expanded) 1 else Int.MAX_VALUE,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) cut = it.hasVisualOverflow },
                        modifier = if (dense && (cut || expanded)) Modifier.clickable(onClickLabel = if (expanded) "收起说明" else "展开说明") { expanded = !expanded } else Modifier,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

@Composable
fun NavRow(title: String, summary: String? = null, value: String? = null, divider: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Bc.colors
    ListRow(title, summary, divider, enabled, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (value != null) Text(value, style = Bc.type.body, color = if (enabled) c.textSecondary else c.textDisabled, maxLines = 1)
            Icon(BcIcons.Chevron, contentDescription = null, tint = if (enabled) c.textSecondary else c.textDisabled, modifier = Modifier.size(18.dp))
        }
    }
}

/** 只显示值的行。 */
@Composable
fun ValueRow(title: String, summary: String? = null, value: String, divider: Boolean = true, valueColor: Color? = null) {
    ListRow(title, summary, divider, onClick = null) {
        Text(value, style = Bc.type.body, color = valueColor ?: Bc.colors.textSecondary)
    }
}

/** 开关行：整行可点。[badge] 写“重开生效”这类标签。 */
@Composable
fun SwitchRow(title: String, summary: String? = null, checked: Boolean, divider: Boolean = true, enabled: Boolean = true, badge: String? = null, onChange: (Boolean) -> Unit) {
    ListRow(title, summary, divider, enabled, onClick = { onChange(!checked) }, role = Role.Switch, badge = badge) {
        BcSwitch(checked, enabled)
    }
}

/**
 * Material 3 样式的开关：开 = 强调色轨道 + 带对勾的大滑块；关 = 描边轨道 + 小滑块。
 * 只显示，点击交给整行（[SwitchRow]）。
 */
@Composable
fun BcSwitch(checked: Boolean, enabled: Boolean = true) {
    val c = Bc.colors
    val motion = if (Bc.reduceMotion) snap<Dp>() else spring(stiffness = 700f)
    val thumb by animateDpAsState(if (checked) 24.dp else 16.dp, motion, label = "thumb")
    val x by animateDpAsState(if (checked) 24.dp else 8.dp, motion, label = "x")
    val track by animateColorAsState(if (checked) c.accent else c.seg, label = "track")
    val alpha = if (enabled) 1f else 0.38f
    Box(
        Modifier.size(52.dp, 32.dp).clip(CircleShape).background(track.copy(alpha = alpha))
            .then(if (checked) Modifier else Modifier.border(2.dp, c.outline.copy(alpha = alpha), CircleShape)),
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbColor = when {
            checked && c.isDark -> c.onAccent
            checked -> Color.White
            else -> c.outline
        }
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(x.roundToPx(), 0) }.size(thumb).clip(CircleShape).background(thumbColor.copy(alpha = alpha)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(BcIcons.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
        }
    }
}

/** 小标签（“重开生效”“大会员”）。 */
@Composable
fun Badge(text: String, tone: Tone = Tone.WARN) {
    val c = Bc.colors
    val (bg, fg) = when (tone) {
        Tone.WARN -> c.warnSoft to c.onWarnSoft
        Tone.OK -> c.accentSoft to c.accentInk
        Tone.DANGER -> c.dangerSoft to c.onDangerSoft
        Tone.INFO -> c.muted to c.textSecondary
    }
    Text(text, style = Bc.type.caption, color = fg, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** 场景图标：场景色的圆角方块里放线条图标。 */
@Composable
fun HueTile(icon: ImageVector, hue: Hue, size: Dp = 40.dp) {
    val dark = Bc.colors.isDark
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(hue.bg(dark)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = hue.fg(dark), modifier = Modifier.size(size / 2))
    }
}

enum class Tone { INFO, OK, WARN, DANGER }

/** 提示条：按语气上色的圆角块，说明当前状态或下一步；可带一个动作。 */
@Composable
fun Notice(text: String, tone: Tone = Tone.INFO, action: String? = null, onAction: (() -> Unit)? = null) {
    // 分组标题下面紧跟的提示放进那张卡片里；其它地方是独立的一块
    val flow = LocalCardFlow.current
    val nested = LocalInCard.current
    val inCard = remember { flow.open || nested }
    if (nested) {
        NoticeBody(text, tone, action, onAction, Modifier.padding(vertical = 6.dp))
        return
    }
    if (inCard) {
        CardSegment(top = false, bottom = false) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { NoticeBody(text, tone, action, onAction) }
        }
        return
    }
    NoticeBody(text, tone, action, onAction, Modifier.padding(horizontal = PageGutter).padding(bottom = 12.dp))
}

@Composable
private fun NoticeBody(text: String, tone: Tone, action: String?, onAction: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = Bc.colors
    val (bg, fg, strong) = when (tone) {
        Tone.INFO -> Triple(c.muted, c.textSecondary, c.accent)
        Tone.OK -> Triple(c.accentSoft, c.accentInk, c.accent)
        Tone.WARN -> Triple(c.warnSoft, c.onWarnSoft, c.warn)
        Tone.DANGER -> Triple(c.dangerSoft, c.onDangerSoft, c.danger)
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
            .heightIn(min = 52.dp).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (tone) { Tone.WARN -> BcIcons.Restart.takeIf { text.contains("重启") || text.contains("重开") } ?: BcIcons.Alert; Tone.DANGER -> BcIcons.Alert; Tone.OK -> BcIcons.Check; Tone.INFO -> BcIcons.Info }
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Bc.type.body, color = fg, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        if (action != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            PillButton(action, onAction, style = PillStyle.Custom(strong, if (tone == Tone.WARN && !c.isDark) Color.White else if (tone == Tone.WARN) Color(0xFF2A1E00) else c.onAccent), height = 36.dp)
        }
    }
}

sealed interface PillStyle {
    data object Primary : PillStyle
    data object Tonal : PillStyle
    data object Outline : PillStyle
    data object Text : PillStyle
    data class Custom(val bg: Color, val fg: Color) : PillStyle
}

/** 胶囊按钮。 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 44.dp,
) {
    val c = Bc.colors
    val (bg, fg) = when (style) {
        PillStyle.Primary -> (if (enabled) c.accent else c.muted) to (if (enabled) c.onAccent else c.textDisabled)
        PillStyle.Tonal -> c.accentSoft to (if (enabled) c.accentInk else c.textDisabled)
        PillStyle.Outline -> c.surface to (if (enabled) c.text else c.textDisabled)
        PillStyle.Text -> Color.Transparent to (if (enabled) c.accent else c.textDisabled)
        is PillStyle.Custom -> style.bg to style.fg
    }
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier.heightIn(min = height).clip(shape).background(bg)
            .then(if (style == PillStyle.Outline) Modifier.border(1.dp, c.chipBorder, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = if (style == PillStyle.Text) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Bc.type.body, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
    }
}

/** 页底固定的一对按钮：左次要（描边）、右主要（实心）。 */
@Composable
fun BottomActions(secondary: String? = null, onSecondary: () -> Unit = {}, primary: String, primaryEnabled: Boolean = true, onPrimary: () -> Unit) {
    val c = Bc.colors
    Row(Modifier.fillMaxWidth().background(c.ground).padding(horizontal = PageGutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (secondary != null) PillButton(secondary, onSecondary, Modifier.weight(0.42f), PillStyle.Outline, height = 48.dp)
        PillButton(primary, onPrimary, Modifier.weight(0.58f), PillStyle.Primary, enabled = primaryEnabled, height = 48.dp)
    }
}

/** 底部页签：选中的图标外面是一个浅色胶囊。 */
@Composable
fun BottomTabs(items: List<Pair<String, ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Bc.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)) {
            items.forEachIndexed { i, (label, icon) ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).fillMaxWidth().semantics { this.selected = on }.clickable(role = Role.Tab, onClick = { onSelect(i) }).padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(64.dp, 32.dp).clip(CircleShape).background(if (on) c.accentSoft else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, contentDescription = null, tint = if (on) c.accent else c.textSecondary, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = Bc.type.caption, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) c.accent else c.textSecondary)
                }
            }
        }
    }
}

/** 分段控件（净化 / 过滤 / 增强 这类同级切换）。 */
@Composable
fun Segmented(items: List<String>, selected: Int, modifier: Modifier = Modifier, height: Dp = 40.dp, onSelect: (Int) -> Unit) {
    val c = Bc.colors
    Row(
        modifier.padding(horizontal = PageGutter).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.seg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(height).then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp)).background(if (on) c.segOn else Color.Transparent)
                    .semantics { this.selected = on }.clickable(role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Bc.type.body, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = if (on) c.text else c.textSecondary, maxLines = 1)
            }
        }
    }
}

/** 筛选用的 chip（全部 / 开着的…）：选中是实心深色。 */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Bc.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.height(36.dp).clip(shape).background(if (selected) c.text else c.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.chipBorder, shape))
            .semantics { this.selected = selected }.clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Bc.type.body, color = if (selected) c.ground else c.text, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal) }
}

/** 一排可横向滚动的筛选 chips。 */
@Composable
fun FilterPills(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PageGutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, s -> FilterPill(s, i == selected) { onSelect(i) } }
    }
}

/** 选项 chip：多选时选中带对勾，[dot] 是分类颜色（空降片段）。 */
@Composable
fun BcChip(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val c = Bc.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.heightIn(min = 36.dp).clip(shape).background(if (selected) c.accentSoft else c.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.chipBorder, shape))
            .semantics { this.selected = selected }.clickable(role = Role.Checkbox, onClick = onClick)
            .padding(start = if (selected) 8.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(BcIcons.Check, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = Bc.type.body, color = if (selected) c.accentInk else c.text, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

/** 页脚留白（列表最后一行和底部按钮之间）。 */
@Composable
fun ColumnScope.PageEnd() {
    CloseCard()
    Spacer(Modifier.height(24.dp))
}

/**
 * 输入框：白底、14 圆角、浅描边，聚焦时强调色描边。参数和 Material 的 OutlinedTextField 一样（常用的那些）。
 */
@Composable
fun BcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    // 在开着的卡片里（分组标题下面）时，放进卡片的一段里；调用方给的左右留白正好是卡片内边距
    val flow = LocalCardFlow.current
    val nested = LocalInCard.current
    val inCard = remember { flow.open && !nested }
    if (inCard) {
        CardSegment(top = false, bottom = false) {
            BcTextFieldBody(value, onValueChange, modifier, enabled, readOnly, label, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine, maxLines, minLines, textStyle)
        }
    } else {
        BcTextFieldBody(value, onValueChange, modifier, enabled, readOnly, label, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine, maxLines, minLines, textStyle)
    }
}

@Composable
private fun BcTextFieldBody(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    label: @Composable (() -> Unit)?,
    placeholder: @Composable (() -> Unit)?,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    prefix: @Composable (() -> Unit)?,
    suffix: @Composable (() -> Unit)?,
    supportingText: @Composable (() -> Unit)?,
    isError: Boolean,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions,
    singleLine: Boolean,
    maxLines: Int,
    minLines: Int,
    textStyle: androidx.compose.ui.text.TextStyle?,
) {
    val c = Bc.colors
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
        textStyle = textStyle ?: Bc.type.itemTitle.copy(color = c.text),
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, disabledContainerColor = c.muted, errorContainerColor = c.surface,
            focusedBorderColor = c.accent, unfocusedBorderColor = c.chipBorder, cursorColor = c.accent,
            focusedLabelColor = c.accent, unfocusedLabelColor = c.textSecondary,
        ),
    )
}
