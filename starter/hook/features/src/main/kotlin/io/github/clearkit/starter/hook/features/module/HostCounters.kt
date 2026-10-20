package io.github.clearkit.starter.hook.features.module

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.clearkit.starter.hook.runtime.BridgeClient
import io.github.clearkit.starter.hook.runtime.FeatureRuntime
import io.github.clearkit.starter.model.bridge.Bridge
import io.github.clearkit.starter.model.bridge.HostStats
import io.github.clearkit.starter.model.settings.Settings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 【宿主】里的计数，报给 App 显示（首页“这周帮你挡下 N 条”、开关下面的“这周起作用 N 次”）：
 * [total] 跨次累计，存在【宿主】的偏好文件 module_stats 里；[process] 只算这次运行。只记次数，不记内容。
 * 有变化后 5 秒合并成一次报给 App。
 */
internal object HostCounters {
    private const val PREFS = "module_stats"
    /** 前缀 → 清除令牌（App 里有“清零”按钮的计数在这里登记，例：`"foo." to Settings.Misc.fooResetToken`）。 */
    private val RESETS: List<Pair<String, io.github.clearkit.starter.model.settings.IntSetting>> = emptyList()

    private val totals = ConcurrentHashMap<String, AtomicLong>()
    private val process = ConcurrentHashMap<String, AtomicLong>()
    private val startedAt = System.currentTimeMillis()
    @Volatile private var loaded = false
    @Volatile private var scheduled = false
    private val main by lazy { Handler(Looper.getMainLooper()) }

    private fun context(): Context? = FeatureRuntime.current?.application

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        val p = context()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        p.all.forEach { (k, v) -> if (!k.startsWith("_") && v is Long) totals[k] = AtomicLong(v) }
        loaded = true
        syncResetToken()
    }

    /** App 的令牌变了：清掉那个前缀的累计。 */
    @Synchronized
    fun syncResetToken() {
        val settings = FeatureRuntime.current?.settings ?: return
        val p = context()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        for ((prefix, setting) in RESETS) {
            val token = settings[setting]
            val key = "_reset.$prefix"
            if (p.getInt(key, 0) == token) continue
            val e = p.edit().putInt(key, token)
            totals.keys.filter { it.startsWith(prefix) }.forEach { totals.remove(it); e.remove(it) }
            e.apply()
            schedule()
        }
    }

    /** 累计里的一项（没有为 null）。 */
    fun get(key: String): Long? {
        ensureLoaded()
        syncResetToken()
        return totals[key]?.get()
    }

    /** 直接设成某个值（播放记忆这种“状态”）。 */
    fun set(key: String, value: Long) {
        ensureLoaded()
        if (totals[key]?.get() == value) return
        totals.getOrPut(key) { AtomicLong() }.set(value)
        context()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putLong(key, value)?.apply()
        schedule()
    }

    fun total(key: String, n: Long = 1) {
        ensureLoaded()
        syncResetToken()
        val v = totals.getOrPut(key) { AtomicLong() }.addAndGet(n)
        context()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putLong(key, v)?.apply()
        schedule()
    }

    /**
     * 按周累计（首页“这周帮你挡下”）：键是 `w<年周>.<种类>`，只留这周和上周。
     * [Impact] 的隐藏计数在第一次用到计数时接上。
     */
    fun week(kind: String, n: Long = 1) {
        val id = weekId(System.currentTimeMillis())
        total("w$id.$kind", n)
        if (pruned != id) {
            pruned = id
            val keep = setOf("w$id.", "w${weekId(System.currentTimeMillis() - 7L * 86_400_000)}.")
            val stale = totals.keys.filter { it.startsWith("w") && it.contains('.') && keep.none(it::startsWith) && it.substringBefore('.').drop(1).all(Char::isDigit) }
            if (stale.isNotEmpty()) {
                val e = context()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
                stale.forEach { totals.remove(it); e?.remove(it) }
                e?.apply()
            }
        }
    }

    @Volatile private var pruned = 0

    /** 年 × 100 + ISO 周数（周一开始）。 */
    fun weekId(millis: Long): Int {
        val d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val wf = java.time.temporal.WeekFields.ISO
        return d.get(wf.weekBasedYear()) * 100 + d.get(wf.weekOfWeekBasedYear())
    }

    /** 接上规则里的隐藏计数（重复调用无妨）。 */
    fun attachImpact() {
        if (io.github.clearkit.starter.model.Impact.sink == null) io.github.clearkit.starter.model.Impact.sink = { kind, n -> week(kind, n.toLong()) }
        if (io.github.clearkit.starter.model.Impact.hitSink == null) {
            io.github.clearkit.starter.model.Impact.hitSink = { key, entry -> week("hit.$key.${io.github.clearkit.starter.model.Impact.entryId(entry)}") }
        }
    }

    fun process(key: String, n: Long = 1) {
        process.getOrPut(key) { AtomicLong() }.addAndGet(n)
        schedule()
    }

    private fun schedule() {
        if (scheduled) return
        scheduled = true
        main.postDelayed({ scheduled = false; report() }, 5_000)
    }

    private fun report() {
        val app = context() ?: return
        syncResetToken()
        val stats = HostStats(
            hostPackage = app.packageName,
            totals = totals.mapValues { it.value.get() },
            process = process.mapValues { it.value.get() },
            startedAt = startedAt,
            reportedAt = System.currentTimeMillis(),
        )
        BridgeClient.post(app, Bridge.METHOD_STATS, Bridge.json.encodeToString(HostStats.serializer(), stats))
    }
}
