package io.github.wzgrx.clearkit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wzgrx.clearkit.ui.icons.ClearIcons
import io.github.wzgrx.clearkit.ui.theme.CardPadding
import io.github.wzgrx.clearkit.ui.theme.Clear
import io.github.wzgrx.clearkit.ui.theme.ClearHue

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
    changed: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Clear.colors
    val dense = Clear.compact
    CardRow(divider) {
        val click = if (onClick != null && enabled) Modifier.clickable(role = role, onClick = onClick) else Modifier
        Row(
            click.fillMaxWidth().heightIn(min = if (dense) 52.dp else 60.dp).padding(horizontal = CardPadding, vertical = if (dense) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                // 标记（重开生效、即将支持）跟在标题后面，不再单独占一行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 改过默认值的设置：标题前一个强调色小圆点
                    if (changed) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(title, style = Clear.type.item, color = if (enabled) c.text else c.textDisabled, modifier = Modifier.weight(1f, fill = false))
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Badge(badge)
                    }
                }
                if (!summary.isNullOrEmpty()) {
                    // 高密度时说明只显示两行；被截断时点说明展开
                    var expanded by remember(summary) { mutableStateOf(false) }
                    var cut by remember(summary) { mutableStateOf(false) }
                    Text(
                        summary, style = Clear.type.caption, color = if (enabled) c.textSecondary else c.textDisabled,
                        maxLines = if (dense && !expanded) 2 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) cut = it.hasVisualOverflow },
                        modifier = Modifier.padding(top = 2.dp).then(
                            if (dense && (cut || expanded)) Modifier.clickable(onClickLabel = if (expanded) "收起说明" else "展开说明") { expanded = !expanded } else Modifier,
                        ),
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

/** 点了进下一页的行：右边是当前值和箭头。[icon] 给“我的”这类入口页画左边的图标；[valueTone] 为 OK 时值用绿色（“已适配”“已连接”）。 */
@Composable
fun NavRow(
    title: String, summary: String? = null, value: String? = null, divider: Boolean = true, enabled: Boolean = true,
    icon: ImageVector? = null, valueTone: Tone? = null, changed: Boolean = false, onClick: () -> Unit,
) {
    val c = Clear.colors
    val valueColor = when (valueTone) { Tone.OK -> c.ok; Tone.WARN -> c.warn; Tone.DANGER -> c.danger; else -> c.textSecondary }
    ListRow(title, summary, divider, enabled, onClick, leading = icon?.let { { IconTile(it) } }, changed = changed) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (value != null) {
                Text(value, style = Clear.type.body, color = if (enabled) valueColor else c.textDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
            }
            Icon(ClearIcons.Chevron, contentDescription = null, tint = if (enabled) c.textTertiary else c.textDisabled, modifier = Modifier.size(16.dp))
        }
    }
}

/** 高密度的取值行：标题（加一行说明）在左，当前值和箭头在右，点开选。 */
@Composable
fun PickerRow(title: String, summary: String?, value: String, divider: Boolean = true, badge: String? = null, enabled: Boolean = true, changed: Boolean = false, onClick: () -> Unit) {
    val c = Clear.colors
    ListRow(title, summary, divider, enabled, onClick = onClick, badge = badge, changed = changed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = Clear.type.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
            Icon(ClearIcons.Chevron, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

/** 只显示值的行。 */
@Composable
fun ValueRow(title: String, summary: String? = null, value: String, divider: Boolean = true, valueColor: Color? = null) {
    ListRow(title, summary, divider, onClick = null) {
        Text(value, style = Clear.type.body, color = valueColor ?: Clear.colors.textSecondary)
    }
}

/** 开关行：整行可点。[badge] 写“重开生效”这类标签。 */
@Composable
fun SwitchRow(
    title: String, summary: String? = null, checked: Boolean, divider: Boolean = true, enabled: Boolean = true, badge: String? = null,
    icon: ImageVector? = null, changed: Boolean = false, onChange: (Boolean) -> Unit,
) {
    ListRow(title, summary, divider, enabled, onClick = { onChange(!checked) }, role = Role.Switch, badge = badge, leading = icon?.let { { IconTile(it) } }, changed = changed) {
        ClearSwitch(checked, enabled)
    }
}

/**
 * 开关（规范 v1）：44 × 26 的胶囊轨道，开 = 强调色，关 = 浅灰；白色圆钮带一点阴影，用弹簧滑过去。
 * 只显示，点击交给整行（[SwitchRow]）。
 * 一个进度同时管圆钮位置和轨道颜色，只在摆放、绘制时读：一页几十个开关少一半动画，拨动时也不重新组合。
 */
@Composable
fun ClearSwitch(checked: Boolean, enabled: Boolean = true) {
    val c = Clear.colors
    val motion = if (Clear.reduceMotion) snap<Float>() else spring(dampingRatio = 0.7f, stiffness = 700f)
    val p by animateFloatAsState(if (checked) 1f else 0f, motion, label = "switch")
    val alpha = if (enabled) 1f else 0.38f
    val off = c.switchOff
    val on = c.accent
    Box(
        Modifier.size(44.dp, 26.dp).clip(CircleShape).drawBehind { drawRect(lerp(off, on, p.coerceIn(0f, 1f)).copy(alpha = alpha)) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset { IntOffset((3.dp + 18.dp * p).roundToPx(), 0) }.size(20.dp).shadow(2.dp, CircleShape).clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.8f)),
        )
    }
}

enum class Tone { INFO, OK, WARN, DANGER }

/** 小标签（“重开生效”“大会员”）。 */
@Composable
fun Badge(text: String, tone: Tone = Tone.WARN) {
    val c = Clear.colors
    val (bg, fg) = when (tone) {
        Tone.WARN -> c.warnSoft to c.onWarnSoft
        Tone.OK -> c.accentSoft to c.accentInk
        Tone.DANGER -> c.dangerSoft to c.onDangerSoft
        Tone.INFO -> c.muted to c.textSecondary
    }
    Text(
        text, fontSize = 11.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp),
    )
}

/** 场景图标：场景色的圆角方块里放线条图标。 */
@Composable
fun HueTile(icon: ImageVector, hue: ClearHue, size: Dp = 36.dp) {
    val dark = Clear.colors.isDark
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.31f)).background(hue.bg(dark)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = hue.fg(dark), modifier = Modifier.size(size * 0.53f))
    }
}

/** 入口行的图标：浅灰圆角方块里放正文色的线条图标（“我的”、设置窗里的入口）。 */
@Composable
fun IconTile(icon: ImageVector, size: Dp = 32.dp) {
    val c = Clear.colors
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.31f)).background(c.muted), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(size * 0.56f))
    }
}

/**
 * 分类行（功能页“想做什么”“在哪里”）：40 的场景色图标、粗标题、一行说明，右边“开了几项 / 共几项”的小块；
 * [changed] 时小块前一个强调色圆点（这一类里有改过默认值的设置）。[on] 为空时右边是箭头。
 */
@Composable
fun CategoryRow(
    icon: ImageVector, hue: ClearHue, title: String, subtitle: String, on: Int?, total: Int?, changed: Boolean = false, divider: Boolean = true, onClick: () -> Unit,
) {
    val c = Clear.colors
    CardRow(divider) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 62.dp).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HueTile(icon, hue, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Clear.type.itemStrong, color = c.text, maxLines = 1)
                Text(subtitle, style = Clear.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            if (on != null && total != null) {
                if (changed) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "$on / $total", style = Clear.type.caption, fontWeight = FontWeight.Bold, color = if (on > 0) c.accentInk else c.textSecondary, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (on > 0) c.accentSoft else c.muted).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else {
                Icon(ClearIcons.Chevron, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** 页脚的图例：一个强调色圆点 + 一句说明（“有改过默认值的设置 · 数字是开了几项 / 共几项”）。 */
@Composable
fun Legend(text: String) {
    CloseCard()
    val c = Clear.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = io.github.wzgrx.clearkit.ui.theme.PageGutter + 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Clear.type.caption, color = c.textTertiary)
    }
}
