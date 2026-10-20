package io.github.clearkit.starter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.clearkit.starter.app.ui.AppShell
import io.github.clearkit.starter.app.ui.theme.BcTheme

/** 桌面图标和 LSPosed 管理器打开的完整 App（首页 / 功能 / 工具 / 我的）。带着页面打开的入口见 [RouteActivity]。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { BcTheme { AppShell() } }
    }
}
