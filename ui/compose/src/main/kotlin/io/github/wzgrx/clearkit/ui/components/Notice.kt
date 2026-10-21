package io.github.wzgrx.clearkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wzgrx.clearkit.ui.icons.ClearIcons
import io.github.wzgrx.clearkit.ui.theme.Clear
import io.github.wzgrx.clearkit.ui.theme.PageGutter
import io.github.wzgrx.clearkit.ui.theme.Radius
import io.github.wzgrx.clearkit.ui.theme.Space

/**
 * 提示条：按语气上色的圆角块，说明当前状态或下一步；可带一个动作。
 * [title] 有值时是“粗体一句 + 下面说明”的两行式（规范 v1 的提示条），否则一行。
 */
@Composable
fun Notice(text: String, tone: Tone = Tone.INFO, action: String? = null, title: String? = null, onAction: (() -> Unit)? = null) {
    // 分组标题下面紧跟的提示放进那张卡片里；其它地方是独立的一块
    val flow = LocalCardFlow.current
    val nested = LocalInCard.current
    val inCard = remember { flow.open || nested }
    if (nested) {
        NoticeBody(text, tone, action, title, onAction, Modifier.padding(vertical = 6.dp))
        return
    }
    if (inCard) {
        CardSegment(top = false, bottom = false) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { NoticeBody(text, tone, action, title, onAction) }
        }
        return
    }
    NoticeBody(text, tone, action, title, onAction, Modifier.padding(horizontal = PageGutter).padding(bottom = Space.card))
}

@Composable
private fun NoticeBody(text: String, tone: Tone, action: String?, title: String?, onAction: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = Clear.colors
    val (bg, fg, strong) = when (tone) {
        Tone.INFO -> Triple(c.muted, c.textSecondary, c.accent)
        Tone.OK -> Triple(c.accentSoft, c.accentInk, c.accent)
        Tone.WARN -> Triple(c.warnSoft, c.onWarnSoft, c.warn)
        Tone.DANGER -> Triple(c.dangerSoft, c.onDangerSoft, c.danger)
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.small)).background(bg)
            .heightIn(min = 52.dp).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (tone) {
            Tone.WARN -> ClearIcons.Restart.takeIf { text.contains("重启") || text.contains("重开") } ?: ClearIcons.Alert
            Tone.DANGER -> ClearIcons.Alert
            Tone.OK -> ClearIcons.Check
            Tone.INFO -> ClearIcons.Info
        }
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            if (title != null) Text(title, style = Clear.type.body, fontWeight = FontWeight.Bold, color = fg)
            Text(text, style = if (title != null) Clear.type.caption else Clear.type.body, color = fg)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            val onStrong = when {
                tone == Tone.WARN && !c.isDark -> Color.White
                tone == Tone.WARN -> Color(0xFF2A1E00)
                tone == Tone.DANGER && c.isDark -> Color(0xFF3A0A08)
                tone == Tone.DANGER -> Color.White
                else -> c.onAccent
            }
            PillButton(action, onAction, style = PillStyle.Custom(strong, onStrong), height = 36.dp)
        }
    }
}
