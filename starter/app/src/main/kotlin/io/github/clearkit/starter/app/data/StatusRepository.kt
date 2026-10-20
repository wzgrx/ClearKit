package io.github.clearkit.starter.app.data

import android.content.Context
import androidx.core.content.edit
import io.github.clearkit.starter.model.bridge.Bridge
import io.github.clearkit.starter.model.bridge.RuntimeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 【宿主】进程经桥接报来的运行状态，按客户端包名各留最近一份（App 重启后仍在）。 */
class StatusRepository(context: Context) {
    private val prefs = context.getSharedPreferences("runtime_status", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    /** 客户端包名 → 最近一次报告。 */
    val all: StateFlow<Map<String, RuntimeStatus>> = _all
    private val _status = MutableStateFlow(_all.value.values.maxByOrNull { it.reportedAt })
    /** 最近一次报告（不管哪个客户端）。 */
    val status: StateFlow<RuntimeStatus?> = _status

    fun update(json: String) {
        val parsed = runCatching { Bridge.json.decodeFromString(RuntimeStatus.serializer(), json) }.getOrNull() ?: return
        prefs.edit { putString("last:${parsed.hostPackage}", json) }
        _all.value = _all.value + (parsed.hostPackage to parsed)
        _status.value = parsed
    }

    private fun load(): Map<String, RuntimeStatus> = prefs.all.filterKeys { it.startsWith("last:") }.values.mapNotNull { v ->
        (v as? String)?.let { runCatching { Bridge.json.decodeFromString(RuntimeStatus.serializer(), it) }.getOrNull() }
    }.associateBy { it.hostPackage }
}
