package io.github.wzgrx.clearkit.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.dp
import io.github.wzgrx.clearkit.ui.theme.LocalClearColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 全局的按压反馈（代替水波纹）：按下时盖一层很淡的高亮；小控件（按钮、卡片、底栏项）同时缩小一点，
 * 松手用欠阻尼弹簧弹回，带一点过冲。整行宽的列表项只高亮不缩放（整行缩小看起来发虚）。
 * 只改绘制（缩放画布），不影响布局和点击区域。
 */
data object PressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource)
}

private class PressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val progress = Animatable(0f)
    private var job: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            var pressed = 0
            var pressedAt = 0L
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> {
                        pressed++
                        pressedAt = System.nanoTime()
                        job?.cancel()
                        job = launch { progress.animateTo(1f, PressIn) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        pressed = (pressed - 1).coerceAtLeast(0)
                        if (pressed == 0) {
                            val quick = System.nanoTime() - pressedAt < QUICK_TAP_NS
                            job?.cancel()
                            job = launch {
                                // 很快的轻点也要看得见：先补完按下，再弹回
                                if (quick && progress.value < 1f) progress.animateTo(1f, tween(QUICK_FILL_MS))
                                progress.animateTo(0f, PressOut)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = progress.value
        if (p == 0f) {
            drawContent()
            return
        }
        val small = size.width < SMALL_WIDTH.toPx() && size.height < SMALL_HEIGHT.toPx()
        val s = if (small) 1f - SHRINK * p else 1f
        scale(s, center) {
            this@draw.drawContent()
            // 弹回过冲时 p 会小于 0：高亮只在按下的一侧
            val dark = currentValueOf(LocalClearColors).isDark
            drawRect(if (dark) Color.White else Color.Black, alpha = (if (dark) 0.08f else 0.05f) * p.coerceIn(0f, 1f))
        }
    }

    private companion object {
        val PressIn = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f)
        val PressOut = spring<Float>(dampingRatio = 0.45f, stiffness = 420f)
        const val QUICK_TAP_NS = 90_000_000L
        const val QUICK_FILL_MS = 70
        const val SHRINK = 0.05f
        val SMALL_WIDTH = 280.dp
        val SMALL_HEIGHT = 220.dp
    }
}
