package io.github.clearkit.starter.app.data

import android.content.Context
import androidx.core.content.edit
import io.github.clearkit.starter.model.bridge.Bridge
import io.github.clearkit.starter.model.bridge.HostStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 【宿主】报来的计数（只有次数）：存最近一份，App 重开后还能看到。清除计数时先在这里清掉，
 * 【宿主】那边看到令牌变化后自己清零。
 */
class StatsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("host_stats", Context.MODE_PRIVATE)
    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<HostStats?> = _stats

    private fun load(): HostStats? = prefs.getString("last", null)?.let { runCatching { Bridge.json.decodeFromString(HostStats.serializer(), it) }.getOrNull() }

    fun update(json: String) {
        val s = runCatching { Bridge.json.decodeFromString(HostStats.serializer(), json) }.getOrNull() ?: return
        _stats.value = s
        prefs.edit { putString("last", json) }
    }

    fun clearSponsor() = clearPrefix("sponsor.")

    fun clearPrefix(prefix: String) {
        val s = _stats.value ?: return
        val cleared = s.copy(totals = s.totals.filterKeys { !it.startsWith(prefix) })
        _stats.value = cleared
        prefs.edit { putString("last", Bridge.json.encodeToString(HostStats.serializer(), cleared)) }
    }
}
