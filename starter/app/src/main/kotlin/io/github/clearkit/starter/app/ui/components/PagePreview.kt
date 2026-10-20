package io.github.clearkit.starter.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.core.content.edit
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.ui.theme.Bc
import io.github.clearkit.starter.model.preview.PagePreviews
import io.github.clearkit.starter.model.preview.PagePreviews.Block
import io.github.clearkit.starter.model.preview.PagePreviews.Effect
import io.github.clearkit.starter.model.preview.PagePreviews.Style
import kotlinx.coroutines.delay

/** 示意图视图里已经画出来的设置（列表里不再重复）；列表视图时为空。 */
val LocalDiagramKeys = androidx.compose.runtime.compositionLocalOf { emptySet<String>() }

/** 当前视图里这一项是不是只在示意图里显示（列表行直接不出）。 */
@Composable
fun inDiagram(key: String): Boolean = key in LocalDiagramKeys.current

/** 示意图 / 列表（6.2）：全局一个选择，记在本机。 */
object PreviewMode {
    private val prefs by lazy { ModuleApp.instance.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var list by mutableStateOf(runCatching { prefs.getBoolean("listMode", false) }.getOrDefault(false))
        private set

    fun set(list: Boolean) {
        this.list = list
        runCatching { prefs.edit { putBoolean("listMode", list) } }
    }
}

/** 页头下面的“示意图 / 列表”切换。 */
@Composable
fun PreviewModeSwitch() {
    Segmented(
        listOf("示意图", "列表"), if (PreviewMode.list) 1 else 0,
        Modifier.padding(horizontal = PageGutter).padding(bottom = 6.dp).fillMaxWidth(), height = 32.dp,
    ) { PreviewMode.set(it == 1) }
}

/**
 * 实物预览卡（6.0）：页面顶上一张【宿主】页面的示意图，点块就改对应设置；
 * 下面列表里改了设置，示意图里对应的块闪一下，知道它管的是哪里。
 */
@Composable
fun PagePreviewCard(route: String, divider: Boolean = false) {
    val page = PagePreviews.byRoute(route) ?: return
    val app = ModuleApp.instance
    val values by app.settings.values.collectAsState()
    // 和上一次的值比，找出刚改过的绑定，让它们闪一下
    var last by remember { mutableStateOf(values) }
    var flash by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(values) {
        val changed = page.binds.filter { PagePreviews.active(it, values) != PagePreviews.active(it, last) || values[it.setting.key] != last[it.setting.key] }
            .map { id(it) }.toSet()
        last = values
        if (changed.isNotEmpty()) {
            flash = changed
            delay(900)
            flash = emptySet()
        }
    }
    val (on, _) = PagePreviews.count(page, values)
    val c = Bc.colors
    // 点不能直接切换的块（名单、文字、数值）或长按任何块：弹出这一项的说明和完整控件
    var editing by remember { mutableStateOf<io.github.clearkit.starter.model.settings.Setting<*>?>(null) }
    CardRow(divider) {
        Column(Modifier.fillMaxWidth().padding(horizontal = CardPadding, vertical = 8.dp)) {
            // 一行：标题、说明、改了几处（6.2 高密度：图例不再每张卡重复）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(page.title, style = Bc.type.itemTitle, color = c.text)
                page.note?.let {
                    Text(" · $it", style = Bc.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                } ?: Spacer(Modifier.weight(1f))
                Text(if (on == 0) "点块改 · 长按看说明" else "改了 $on 处", style = Bc.type.caption, color = if (on > 0) c.accentInk else c.textSecondary, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(c.ground).border(1.dp, c.line, shape).padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                page.blocks.forEach { b ->
                    PreviewBlock(
                        b, values, flash,
                        onTap = { block ->
                            if (PagePreviews.tappable(block)) PagePreviews.step(block, values).takeIf { it.isNotEmpty() }?.let { app.settings.setAll(*it.toTypedArray()) }
                            else editing = block.bind?.setting
                        },
                        onHold = { block -> editing = block.bind?.setting },
                    )
                }
            }
        }
    }
    editing?.let { st ->
        BcSheet(page.title, null, onDismiss = { editing = null }) { io.github.clearkit.starter.app.ui.pages.SettingEditor(st) }
    }
}

private fun id(b: PagePreviews.Bind) = b.setting.key + "#" + b.option

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PreviewBlock(b: Block, values: Map<String, Any?>, flash: Set<String>, onTap: (Block) -> Unit, onHold: (Block) -> Unit) {
    val c = Bc.colors
    val on = b.bind?.let { PagePreviews.active(it, values) } ?: false
    val hit = b.bind?.let { id(it) in flash } ?: false
    val glow by animateColorAsState(if (hit) c.accent else Color.Transparent, tween(250), label = "flash")
    when (b.style) {
        Style.ROW -> FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.children.forEach { child -> PreviewBlock(child, values, flash, onTap, onHold) }
        }
        Style.MEDIA -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1E2523)).border(2.dp, glow, RoundedCornerShape(10.dp))
                .heightIn(min = 52.dp).padding(7.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(b.label, style = Bc.type.caption, color = Color(0xCCFFFFFF))
            if (b.children.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    b.children.forEach { child -> PreviewBlock(child, values, flash, onTap, onHold) }
                }
            }
        }
        Style.GROUP, Style.TABS -> {
            val groupOff = on && b.effect == Effect.HIDE
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(if (groupOff) c.seg else c.surface).border(if (hit) 2.dp else 1.dp, if (hit) glow else c.line, shape).padding(horizontal = 7.dp, vertical = 5.dp),
            ) {
                if (b.label.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.label, style = Bc.type.caption, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                            color = if (groupOff) c.textDisabled else c.textSecondary, textDecoration = if (groupOff) TextDecoration.LineThrough else null,
                        )
                        val bind = b.bind
                        if (bind != null) {
                            Text(
                                if (groupOff) "整块已隐藏 · 恢复" else "整块隐藏", style = Bc.type.caption, color = c.accent,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Switch) { onTap(b) }.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                if (!groupOff) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                        b.children.forEach { child -> PreviewBlock(child, values, flash, onTap, onHold) }
                    }
                }
            }
        }
        Style.PLAIN, Style.OVERLAY -> {
            val overlay = b.style == Style.OVERLAY
            val struck = on && b.effect == Effect.HIDE
            val added = b.effect == Effect.ADD
            // 数值块：写当前值，改过的用强调色
            val valued = b.effect == Effect.VALUE && PagePreviews.changed(b, values)
            val shape = RoundedCornerShape(if (overlay) 6.dp else 7.dp)
            // 没绑设置的块只是陪衬（“视频”“简介”）：不描边、字浅，一眼分得出哪些能点
            val context = b.bind == null
            val bg = when {
                context && overlay -> Color(0x1AFFFFFF)
                context -> c.muted
                overlay && struck -> Color(0x33FFFFFF)
                overlay -> Color(0x40FFFFFF)
                struck -> c.seg
                added && on -> c.accentSoft
                valued && !overlay -> c.accentSoft
                else -> c.surface
            }
            val fg = when {
                context -> if (overlay) Color(0x99FFFFFF) else c.textSecondary
                overlay -> if (struck) Color(0x80FFFFFF) else Color.White
                struck || (added && !on) -> c.textDisabled
                added || valued -> c.accentInk
                else -> c.text
            }
            val border = when {
                hit -> glow
                context -> Color.Transparent
                added && !on -> c.chipBorder
                struck -> c.chipBorder
                overlay -> Color.Transparent
                else -> c.line
            }
            val bind = b.bind
            Row(
                Modifier.clip(shape).background(bg).border(if (hit) 2.dp else 1.dp, border, shape)
                    .then(if (bind != null) Modifier.combinedClickable(role = Role.Checkbox, onLongClick = { onHold(b) }) { onTap(b) } else Modifier)
                    .semantics { if (bind != null) stateDescription = if (on) (if (b.effect == Effect.HIDE) "已隐藏" else "已开") else "未改" }
                    .heightIn(min = if (overlay) 26.dp else 30.dp).padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (added && !on) "+ ${b.label}" else PagePreviews.label(b, values), style = Bc.type.caption, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                )
                if (struck) { Spacer(Modifier.width(4.dp)); Text("✕", style = Bc.type.caption, color = if (overlay) Color(0xFFFFB4AB) else c.danger) }
                val markText = b.mark
                if (on && b.effect == Effect.MARK && markText != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(markText, style = Bc.type.caption, color = c.accentInk, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(c.accentSoft).padding(horizontal = 4.dp))
                } else if (!on && b.effect == Effect.MARK && markText != null && PagePreviews.hintWhenOff(b)) {
                    // 开关没开时淡淡写出点了会怎样，不然只看到一个名字
                    Text(" · $markText", style = Bc.type.caption, color = if (overlay) Color(0x80FFFFFF) else c.textDisabled, maxLines = 1)
                }
            }
        }
    }
}
