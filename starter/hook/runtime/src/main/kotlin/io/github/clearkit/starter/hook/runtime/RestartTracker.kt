package io.github.clearkit.starter.hook.runtime

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * “待重启”提示条的记账（和 App 的 SettingsStore 一致）：需重启的项记下第一次改之前的值，
 * 改回原值就不再算待重启。单个写入、批量写入、回滚都要先 [before] 再写、写完 [after]，
 * 否则批量 / 回滚改回原值后提示条清不掉。
 */
internal class RestartTracker {
    val pending: MutableSet<String> = CopyOnWriteArraySet()
    private val baseline = ConcurrentHashMap<String, Any>()

    /** 写入本进程之前：记下这些需重启的键现在的值（已经记过的不覆盖）。[current] 取不到时返回 null。 */
    fun before(keys: Collection<String>, current: (String) -> Any?) {
        for (k in keys) current(k)?.let { baseline.putIfAbsent(k, it) }
    }

    /** 写入本进程之后：和原值相同就从待重启里去掉，否则加入。 */
    fun after(keys: Collection<String>, current: (String) -> Any?) {
        for (k in keys) {
            val now = current(k)
            if (now != null && baseline[k] == now) {
                pending -= k
                baseline.remove(k)
            } else {
                pending += k
            }
        }
    }
}
