package io.github.wzgrx.clearkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wzgrx.clearkit.ui.icons.ClearIcons
import io.github.wzgrx.clearkit.ui.theme.Clear
import io.github.wzgrx.clearkit.ui.theme.PageGutter
import io.github.wzgrx.clearkit.ui.theme.Radius

/** 底部弹层的外壳（规范 v1 “弹层”）：28 圆角，标题、副标题，下面可滚动的内容放在白卡里。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearSheet(title: String, subtitle: String? = null, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Clear.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        containerColor = c.ground,
        contentColor = c.text,
        scrimColor = c.scrim,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Column(Modifier.padding(horizontal = PageGutter).padding(bottom = 10.dp)) {
                Text(title, style = Clear.type.section, color = c.text)
                subtitle?.let { Text(it, style = Clear.type.caption, color = c.textSecondary) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.small)).background(c.surface)) { content() }
            }
        }
    }
}

/** 弹层里的一行：单选是圆点，多选是方框；一行 48 点高。 */
@Composable
fun SheetOption(label: String, selected: Boolean, multi: Boolean, detail: String? = null, onClick: () -> Unit) {
    val c = Clear.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = if (multi) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { stateDescription = if (selected) "已选" else "未选" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Clear.type.body, color = c.text, fontWeight = if (selected) FontWeight.Medium else null)
            detail?.let { Text(it, style = Clear.type.caption, color = c.textSecondary) }
        }
        Spacer(Modifier.width(12.dp))
        val shape = if (multi) RoundedCornerShape(5.dp) else CircleShape
        Box(
            Modifier.size(20.dp).clip(shape).background(if (selected) c.accent else c.surface)
                .border(2.dp, if (selected) c.accent else c.outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                if (multi) Icon(ClearIcons.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(14.dp))
                else Box(Modifier.size(8.dp).clip(CircleShape).background(c.onAccent))
            }
        }
    }
}

/** 正在加载：转圈 + 说明在做什么；不遮住已经显示的内容。 */
@Composable
fun LoadingState(text: String = "正在读取…") {
    Column(Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Clear.colors.accent, strokeWidth = 3.dp)
        Text(text, style = Clear.type.body, color = Clear.colors.textSecondary)
    }
}

/** 没有结果：说明为什么空、下一步做什么；可带一个动作（如清除筛选）。 */
@Composable
fun EmptyState(title: String, detail: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = Clear.type.itemStrong, color = Clear.colors.text, textAlign = TextAlign.Center)
        detail?.let { Text(it, style = Clear.type.body, color = Clear.colors.textSecondary, textAlign = TextAlign.Center) }
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = Clear.colors.accentInk) }
    }
}

/** 失败与重试：写清失败原因，重试只在点了才发生，不自动循环。 */
@Composable
fun ErrorState(reason: String, onRetry: (() -> Unit)? = null) {
    Notice("没成功：$reason", Tone.DANGER, action = if (onRetry != null) "重试" else null, onAction = onRetry)
}
