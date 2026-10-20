package io.github.clearkit.starter.app.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.model.settings.BoolSetting

/** 读一项开关并在设置变化时重组。 */
@Composable
fun boolSetting(setting: BoolSetting): Boolean {
    val values by ModuleApp.instance.settings.values.collectAsState()
    return (values[setting.key] as? Boolean) ?: setting.default
}

@Composable
fun <T> collect(flow: kotlinx.coroutines.flow.StateFlow<T>): State<T> = flow.collectAsState()
