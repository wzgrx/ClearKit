package io.github.clearkit.starter.app.bridge

import android.app.Activity
import android.os.Bundle
import io.github.clearkit.starter.app.ModuleApp

/**
 * 【宿主】看不见桥接 Provider 时启动它：给【宿主】授予 URI 权限后系统就让它看得见模块。没有窗口，立刻结束。
 */
class HandshakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModuleApp.instance.grantHostVisibility()
        finish()
    }
}
