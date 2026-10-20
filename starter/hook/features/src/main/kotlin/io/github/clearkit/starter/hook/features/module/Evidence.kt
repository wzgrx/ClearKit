package io.github.clearkit.starter.hook.features.module

import io.github.clearkit.starter.hook.runtime.Feature
import io.github.clearkit.starter.model.settings.SettingsReader
import java.util.concurrent.ConcurrentHashMap

/**
 * 起作用的证据（6.0）：功能真的改了一次东西（网络规则改了回复、拦下了一次弹窗……）就记一次，
 * 按周记在它开着的开关名下（`w<年周>.ev.<设置键>`），App 在开关下面写“这周起作用 N 次”。只记次数。
 * 同一个功能 1.5 秒内只算一次（一个回复里几条规则都改了算一次）。
 */
internal object Evidence {
    private val last = ConcurrentHashMap<String, Long>()

    fun acted(feature: Feature, settings: SettingsReader?) {
        val s = settings ?: return
        val now = System.currentTimeMillis()
        val prev = last.put(feature.id, now)
        if (prev != null && now - prev < 1_500) return
        val keys = feature.switches.filter { s[it] }.map { it.key } + feature.listSwitches.filter { s[it].isNotEmpty() }.map { it.key }
        keys.forEach { HostCounters.week("ev.$it") }
    }

    /** 没有开关列表的功能（视频节点这类）：直接记在给出的、开着的开关名下。 */
    fun actedFor(id: String, settings: SettingsReader?, vararg switches: io.github.clearkit.starter.model.settings.BoolSetting) {
        val s = settings ?: return
        val now = System.currentTimeMillis()
        val prev = last.put(id, now)
        if (prev != null && now - prev < 1_500) return
        switches.filter { s[it] }.forEach { HostCounters.week("ev.${it.key}") }
    }
}
