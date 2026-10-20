package io.github.clearkit.starter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.theme.Bc

/** 正在加载（设计 051）：转圈 + 说明在做什么；不遮住已经显示的内容。 */
@Composable
fun LoadingState(text: String = "正在读取…") {
    Column(Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Bc.colors.accent, strokeWidth = 3.dp)
        Text(text, style = Bc.type.body, color = Bc.colors.textSecondary)
    }
}

/** 没有结果（设计 052）：说明为什么空、下一步做什么；可带一个动作（如清除筛选）。 */
@Composable
fun EmptyState(title: String, detail: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = Bc.type.itemTitle, color = Bc.colors.text)
        detail?.let { Text(it, style = Bc.type.body, color = Bc.colors.textSecondary) }
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = Bc.colors.accent) }
    }
}

/** 连接失败与重试（设计 053）：写清失败原因，重试只在点了才发生，不自动循环。 */
@Composable
fun ErrorState(reason: String, onRetry: (() -> Unit)? = null) {
    Notice("没成功：$reason", Tone.DANGER, action = if (onRetry != null) "重试" else null, onAction = onRetry)
}

/**
 * 未保存修改（设计 054）：页面有没保存的改动时，返回（页头箭头、系统返回）先问“放弃修改？”。
 * [dirty] 为 false 时直接返回。
 */
@Composable
fun UnsavedGuard(dirty: Boolean, onSave: (() -> Unit)? = null) {
    val nav = LocalNavigator.current
    var asking by remember { mutableStateOf(false) }
    DisposableEffect(dirty) {
        nav.guard = if (dirty) ({ asking = true; false }) else null
        onDispose { if (nav.guard != null) nav.guard = null }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("还有没保存的修改") },
            text = { Text("离开后这些修改会丢掉。") },
            confirmButton = {
                if (onSave != null) TextButton(onClick = { asking = false; onSave(); nav.force() }) { Text("保存并离开") }
                else TextButton(onClick = { asking = false }) { Text("继续编辑") }
            },
            dismissButton = { TextButton(onClick = { asking = false; nav.force() }) { Text("放弃修改") } },
        )
    }
}
