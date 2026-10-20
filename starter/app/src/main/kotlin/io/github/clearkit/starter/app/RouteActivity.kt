package io.github.clearkit.starter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import io.github.clearkit.starter.app.ui.AppShell
import io.github.clearkit.starter.app.ui.theme.BcTheme
import io.github.clearkit.starter.model.bridge.Bridge

/**
 * 从【宿主】、通知打开的单个页面。【宿主】不加 `NEW_TASK` 启动它，它就在【宿主】当前的任务里，目标页是栈底：
 * 按一次返回就 `finish()` 回到原来的【宿主】页面。路由在 [Bridge.EXTRA_ROUTE] 里（如 `adapt`、`status`）。
 */
class RouteActivity : ComponentActivity() {
    private data class Entry(val route: String, val serial: Int)

    private val entry = mutableStateOf(Entry("", 0))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        entry.value = Entry(intent?.getStringExtra(Bridge.EXTRA_ROUTE).orEmpty(), 0)
        setContent {
            BcTheme {
                val e = entry.value
                key(e) { AppShell(entry = e.route, onExit = ::finish) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entry.value = Entry(intent.getStringExtra(Bridge.EXTRA_ROUTE).orEmpty(), entry.value.serial + 1)
    }
}
