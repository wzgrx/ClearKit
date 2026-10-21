package io.github.wzgrx.clearkit.ui.components

import androidx.compose.foundation.background
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

/** 分组标题：开一张新卡片，标题写在卡片顶部。 */
@Composable
fun SectionLabel(text: String) {
    if (LocalHideSectionLabels.current) return
    CloseCard()
    val flow = LocalCardFlow.current
    flow.open = true
    Spacer(Modifier.height(4.dp))
    CardSegment(top = true, bottom = false) {
        Text(text, style = Clear.type.label, color = Clear.colors.textSecondary, modifier = Modifier.padding(start = CardPadding, end = CardPadding, top = 14.dp, bottom = 2.dp))
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

/** 页脚留白（列表最后一行和底部按钮之间）。 */
@Composable
fun ColumnScope.PageEnd() {
    CloseCard()
    Spacer(Modifier.height(24.dp))
}
