package io.github.wzgrx.clearkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wzgrx.clearkit.ui.theme.Clear
import io.github.wzgrx.clearkit.ui.theme.PageGutter

sealed interface PillStyle {
    /** 实心强调色：一页里最主要的动作。 */
    data object Primary : PillStyle
    /** 浅强调底：次要但常用。 */
    data object Tonal : PillStyle
    /** 描边：取消、返回修改。 */
    data object Outline : PillStyle
    /** 只有字。 */
    data object Text : PillStyle
    /** 危险操作：删除、清空。 */
    data object Danger : PillStyle
    data class Custom(val bg: Color, val fg: Color) : PillStyle
}

/** 胶囊按钮（规范 v1：按钮都是胶囊）。 */
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
    val c = Clear.colors
    val (bg, fg) = when (style) {
        PillStyle.Primary -> (if (enabled) c.accent else c.muted) to (if (enabled) c.onAccent else c.textDisabled)
        PillStyle.Tonal -> c.accentSoft to (if (enabled) c.accentInk else c.textDisabled)
        PillStyle.Outline -> Color.Transparent to (if (enabled) c.text else c.textDisabled)
        PillStyle.Text -> Color.Transparent to (if (enabled) c.accentInk else c.textDisabled)
        PillStyle.Danger -> c.dangerSoft to (if (enabled) c.danger else c.textDisabled)
        is PillStyle.Custom -> style.bg to style.fg
    }
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier.heightIn(min = height).clip(shape).background(bg)
            .then(if (style == PillStyle.Outline) Modifier.border(1.dp, c.line, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = if (style == PillStyle.Text) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Clear.type.body, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

/** 页底固定的一对按钮：左次要（描边）、右主要（实心）。 */
@Composable
fun BottomActions(secondary: String? = null, onSecondary: () -> Unit = {}, primary: String, primaryEnabled: Boolean = true, onPrimary: () -> Unit) {
    val c = Clear.colors
    Row(Modifier.fillMaxWidth().background(c.ground).padding(horizontal = PageGutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (secondary != null) PillButton(secondary, onSecondary, Modifier.weight(0.42f), PillStyle.Outline, height = 48.dp)
        PillButton(primary, onPrimary, Modifier.weight(0.58f), PillStyle.Primary, enabled = primaryEnabled, height = 48.dp)
    }
}

/** 图标按钮（44 触摸区）。[filled] 时是浅灰圆底（一级页页头右边的按钮）。 */
@Composable
fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit, filled: Boolean = false, tint: Color? = null) {
    val c = Clear.colors
    val base = Modifier.size(44.dp).clip(CircleShape)
    val styled = if (filled) base.background(c.muted) else base
    Box(styled.clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint ?: c.text, modifier = Modifier.size(22.dp))
    }
}
