package io.github.wzgrx.clearkit.ui.shell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.wzgrx.clearkit.ui.components.Text

/** 页面间跳转：[open] 压一页，[back] 退一页（栈底时退出单页入口）。 */
class Navigator(
    private val stack: MutableList<String>,
    private val onExit: (() -> Unit)?,
    /** 一页离开返回栈（退回、被换掉）时调用，丢掉它记住的滚动位置等状态。 */
    private val onDrop: (String) -> Unit = {},
) {
    val top: String? get() = stack.lastOrNull()
    val depth: Int get() = stack.size
    fun open(route: String) { guard = null; stack += route }

    /** 换掉当前页（不留在返回栈里）。 */
    fun replace(route: String) {
        if (stack.isEmpty()) stack += route else { onDrop(pageKey(stack.size, stack.last())); stack[stack.lastIndex] = route }
    }

    /** 当前页的离开确认：返回 false 表示先不走（页面自己弹确认）。 */
    var guard: (() -> Boolean)? = null

    fun back() {
        if (guard?.invoke() == false) return
        force()
    }

    /** 不经过离开确认直接返回（确认“放弃修改”之后用）。 */
    fun force() {
        guard = null
        // 单页入口：退到入口页再返回就结束，回到宿主
        if (onExit != null && stack.size <= 1) onExit.invoke() else if (stack.isNotEmpty()) {
            onDrop(pageKey(stack.size, stack.last()))
            stack.removeAt(stack.lastIndex)
        }
    }
}

/** 页面状态的键：同一路由在栈里不同深度是不同的页。 */
internal fun pageKey(depth: Int, route: String) = "page:$depth:$route"

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("no navigator") }

/**
 * 未保存修改：页面有没保存的改动时，返回（页头箭头、系统返回）先问“放弃修改？”。[dirty] 为 false 时直接返回。
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
