package io.github.clearkit.starter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ui.theme.Bc

/** 高密度的取值行：标题（加一行说明）在左，当前值和箭头在右，点开选。 */
@Composable
fun PickerRow(title: String, summary: String?, value: String, divider: Boolean = true, badge: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Bc.colors
    ListRow(title, summary, divider, enabled, onClick = onClick, badge = badge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value, style = Bc.type.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(BcIcons.Chevron, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

/** 底部选单的外壳：标题、可滚动的内容，跟着 App 的颜色走。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BcSheet(title: String, subtitle: String? = null, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Bc.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.ground,
        contentColor = c.text,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Column(Modifier.padding(horizontal = PageGutter).padding(bottom = 8.dp)) {
                Text(title, style = Bc.type.blockTitle, color = c.text)
                subtitle?.let { Text(it, style = Bc.type.caption, color = c.textSecondary) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)) { content() }
            }
        }
    }
}

/** 选单里的一行：单选是圆点，多选是方框；一行 48 点高。 */
@Composable
fun SheetOption(label: String, selected: Boolean, multi: Boolean, detail: String? = null, onClick: () -> Unit) {
    val c = Bc.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = if (multi) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { stateDescription = if (selected) "已选" else "未选" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Bc.type.body, color = c.text, fontWeight = if (selected) FontWeight.Medium else null)
            detail?.let { Text(it, style = Bc.type.caption, color = c.textSecondary) }
        }
        Spacer(Modifier.width(12.dp))
        val shape = if (multi) RoundedCornerShape(5.dp) else CircleShape
        Box(
            Modifier.size(20.dp).clip(shape).background(if (selected) c.accent else c.surface)
                .border(2.dp, if (selected) c.accent else c.outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                if (multi) Icon(BcIcons.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(14.dp))
                else Box(Modifier.size(8.dp).clip(CircleShape).background(c.onAccent))
            }
        }
    }
}

/**
 * “选上 = 隐藏”的多选：每项一个小格；显示的照常，隐藏的变灰、划掉、右上角打叉（不用打勾，免得看成“要”）。
 * 顶上一行写隐藏了几项，有隐藏时给“全部恢复”。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HideTiles(
    title: String,
    summary: String?,
    options: List<Pair<String, String>>,
    hidden: Set<String>,
    divider: Boolean = true,
    badge: String? = null,
    locked: Set<String> = emptySet(),
    onToggle: (String) -> Unit,
    onRestoreAll: () -> Unit,
) {
    val c = Bc.colors
    val count = options.count { it.first in hidden }
    CardRow(divider) {
        Column(Modifier.fillMaxWidth().padding(horizontal = CardPadding, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = Bc.type.itemTitle, color = c.text, modifier = Modifier.weight(1f, fill = false))
                    if (badge != null) { Spacer(Modifier.width(6.dp)); Badge(badge) }
                }
                if (count > 0) {
                    Text(
                        "全部恢复", style = Bc.type.caption, color = c.accent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onRestoreAll).padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
            val line = listOfNotNull(if (options.isEmpty()) null else if (count == 0) "都显示 · 点一下隐藏" else "隐藏 $count / ${options.size} · 再点一下恢复", summary)
            if (line.isNotEmpty()) Text(line.joinToString(" · "), style = Bc.type.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (options.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (id, label) -> HideTile(label, id in hidden, id in locked) { onToggle(id) } }
                }
            }
        }
    }
}

/** 一个可隐藏的项。[locked] 的项不能藏（例如“设置”入口），点了没有反应，显示成半透明。 */
@Composable
fun HideTile(label: String, hidden: Boolean, locked: Boolean = false, onClick: () -> Unit) {
    val c = Bc.colors
    val shape = RoundedCornerShape(10.dp)
    Box {
        Row(
            Modifier.clip(shape)
                .background(if (hidden) c.seg else c.ground)
                .border(1.dp, if (hidden) c.chipBorder else c.line, shape)
                .clickable(enabled = !locked, role = Role.Checkbox, onClick = onClick)
                .heightIn(min = 36.dp).padding(start = 10.dp, end = if (hidden) 18.dp else 10.dp)
                .semantics { stateDescription = if (locked) "不能隐藏" else if (hidden) "已隐藏" else "显示"; contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, style = Bc.type.body,
                color = if (hidden || locked) c.textDisabled else c.text,
                textDecoration = if (hidden) TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (hidden) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp).size(13.dp).clip(CircleShape).background(c.dangerSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(BcIcons.Close, contentDescription = null, tint = c.danger, modifier = Modifier.size(9.dp)) }
        }
    }
}
