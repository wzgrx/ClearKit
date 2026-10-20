package io.github.clearkit.starter.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.ChoiceSetting
import io.github.clearkit.starter.model.settings.ConfigScope
import io.github.clearkit.starter.model.settings.FloatSetting
import io.github.clearkit.starter.model.settings.IntSetting
import io.github.clearkit.starter.model.settings.MapSettingsReader
import io.github.clearkit.starter.model.settings.SETTINGS_GROUP
import io.github.clearkit.starter.model.settings.Setting
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.model.settings.SettingsReader
import io.github.clearkit.starter.model.settings.StringSetSetting
import io.github.clearkit.starter.model.settings.StringSetting
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App 里的这份设置是准的；每次写入同步到框架的 RemotePreferences，【宿主】进程从那里读。
 * 模块服务（重新）连上时整份推一次，模块没启用时改的设置也不会丢。
 */
private const val TAG = "BcSettings"

class SettingsStore(context: Context) : SettingsReader {
    private val local: SharedPreferences = context.getSharedPreferences("host_settings", Context.MODE_PRIVATE)
    private val _values = MutableStateFlow(snapshot())
    val values: StateFlow<Map<String, Any?>> = _values

    /** Keys changed since start that need a host restart to apply. */
    private val _pendingRestart = MutableStateFlow<Set<String>>(emptySet())
    val pendingRestart: StateFlow<Set<String>> = _pendingRestart

    /** 需重启的设置第一次改动前的值；改回这个值就不再算待重启。只在 [lock] 里访问。 */
    private val restartBaseline = HashMap<String, Any?>()

    /**
     * 写入锁：【宿主】经桥接（多个 binder 线程）和 App 界面会同时改设置，读-改-写（名单类设置）
     * 不加锁会丢一次修改。可重入：[atomically] 里可以再调 [set]、[reset]。
     */
    private val lock = Any()
    /** [atomically] 嵌套层数和攒着的 [onWrite] 回调：回调在最外层释放锁之后再发，免得在锁里调外部代码。 */
    private var depth = 0
    private val notices = ArrayList<Triple<String, Any?, Any?>>()

    /** 待重启的改动从什么时候开始有（没有为 0）：【宿主】在这之后重新启动过，改动就已经生效。 */
    @Volatile var pendingSince = 0L
        private set

    private fun trackRestart(setting: Setting<*>, before: Any?, after: Any?) {
        if (!setting.needsRestart) return
        if (setting.key !in restartBaseline) restartBaseline[setting.key] = before
        val was = _pendingRestart.value
        _pendingRestart.value = if (restartBaseline[setting.key] == after) was - setting.key else was + setting.key
        if (was.isEmpty() && _pendingRestart.value.isNotEmpty()) pendingSince = System.currentTimeMillis()
    }

    /** 【宿主】报告了在 [startedAt] 重新启动：在待重启的改动之后启动的，改动都已生效，清掉提示。 */
    fun hostStarted(startedAt: Long) {
        if (restartedAfter(pendingSince, startedAt)) clearPendingRestart()
    }

    private val reader = MapSettingsReader { _values.value[it] }

    init {
        ModuleService.onBind(::pushAll)
    }

    /**
     * 分层配置（设计 026）：开着且选了某个渠道时，读到的是这一层实际生效的值（全局 + 覆盖），
     * 写入和恢复默认只动这一层的覆盖键。
     */
    private fun layer(all: Map<String, *> = local.all): String? {
        if (all[Settings.Scope.enabled.key] != true) return null
        return (all[Settings.Scope.edit.key] as? String)?.takeIf { it != ConfigScope.GLOBAL }
    }

    /** 现在编辑的是哪一层（null = 全局）。 */
    val editingLayer: String? get() = layer()

    private fun keyFor(setting: Setting<*>): String {
        val l = layer()
        return if (l != null && ConfigScope.scopable(setting)) ConfigScope.key(l, setting.key) else setting.key
    }

    /** 存储里的全部原值（含各层覆盖键），给作用域页用。 */
    fun stored(): Map<String, Any?> = HashMap(local.all)

    private fun snapshot(): Map<String, Any?> = local.all.let { ConfigScope.effective(HashMap(it), layer(it)) }

    /**
     * 每次写入后回调（键、写之前存储里的原值、写之后的值；null 表示没有显式保存，即默认值）。
     * 操作历史（SettingsJournal）靠它记录所有来源的改动。
     */
    @Volatile
    var onWrite: ((key: String, before: Any?, after: Any?) -> Unit)? = null

    /** 存储里的原值（没有显式保存时为 null）。 */
    fun raw(key: String): Any? = _values.value[key]

    /**
     * 在写入锁里执行 [block]：里面读到的值到写回之前不会被别的线程改掉（例如【宿主】和 App 同时改同一个名单）。
     * 写入的 [onWrite] 回调在锁外、按顺序发出。
     */
    fun <R> atomically(block: SettingsStore.() -> R): R {
        var due: List<Triple<String, Any?, Any?>> = emptyList()
        val result = synchronized(lock) {
            depth++
            try {
                block()
            } finally {
                depth--
                if (depth == 0 && notices.isNotEmpty()) { due = ArrayList(notices); notices.clear() }
            }
        }
        due.forEach { (key, before, after) -> onWrite?.invoke(key, before, after) }
        return result
    }

    /** 按当前值改：`update(blockedUsers) { it + id }`，读和写之间不会插进别的修改。返回写入后的值。 */
    fun <T : Any> update(setting: Setting<T>, transform: (T) -> T): T = atomically {
        val next = transform(value(setting))
        set(setting, next)
        next
    }

    fun <T : Any> set(setting: Setting<T>, value: T): Unit = atomically {
        val before = value(setting)
        if (before == value) return@atomically
        val rawBefore = raw(setting.key)
        val key = keyFor(setting)
        local.edit { put(this, setting, value, key) }
        _values.value = snapshot()
        trackRestart(setting, before, value)
        notices += Triple(setting.key, rawBefore, value)
        val service = ModuleService.service
        if (service == null) {
            android.util.Log.w(TAG, "remote not bound; ${setting.key} kept locally")
        } else {
            runCatching { service.getRemotePreferences(SETTINGS_GROUP).edit { put(this, setting, value, key) } }
                                .onFailure { android.util.Log.e(TAG, "remote write ${setting.key} failed", it) }
        }
    }

    /**
     * 一次写入多项：本地、远程（LSPosed）各只提交一次，【宿主】不会读到一半新一半旧的组合（例如位置的新纬度 + 旧经度，A13）。
     * 和 [SettingsJournal.batch] 一起用时操作历史记成一条。
     */
    fun setAll(vararg changes: Pair<Setting<*>, Any>): Unit = atomically {
        val todo = changes.filter { (s, v) -> value(s) != v }
        if (todo.isEmpty()) return@atomically
        val before = todo.map { (s, _) -> Triple(s, value(s), raw(s.key)) }
        val keys = todo.map { keyFor(it.first) }
        local.edit { todo.forEachIndexed { i, (s, v) -> put(this, s, v, keys[i]) } }
        _values.value = snapshot()
        before.zip(todo).forEach { (b, c) ->
            trackRestart(b.first, b.second, c.second)
            notices += Triple(b.first.key, b.third, c.second)
        }
        val service = ModuleService.service
        if (service == null) {
            android.util.Log.w(TAG, "remote not bound; ${todo.joinToString { it.first.key }} kept locally")
        } else {
            runCatching { service.getRemotePreferences(SETTINGS_GROUP).edit { todo.forEachIndexed { i, (s, v) -> put(this, s, v, keys[i]) } } }
                .onFailure { android.util.Log.e(TAG, "remote write ${todo.joinToString { it.first.key }} failed", it) }
        }
    }

    fun reset(setting: Setting<*>): Unit = atomically {
        val before = value(setting)
        val rawBefore = raw(setting.key)
        val key = keyFor(setting)
        local.edit { remove(key) }
        _values.value = snapshot()
        trackRestart(setting, before, value(setting))
        if (rawBefore != null) notices += Triple(setting.key, rawBefore, null)
        ModuleService.service?.let { s -> runCatching { s.getRemotePreferences(SETTINGS_GROUP).edit { remove(key) } } }
    }

    fun clearPendingRestart() = synchronized(lock) {
        restartBaseline.clear()
        _pendingRestart.value = emptySet()
        pendingSince = 0L
    }

    private fun pushAll(service: XposedService) = synchronized(lock) {
        runCatching {
            val remote = service.getRemotePreferences(SETTINGS_GROUP)
            val current = local.all
            remote.edit {
                remote.all.keys.filter { it !in current }.forEach { remove(it) }
                for ((k, v) in current) {
                    if (v == null) continue
                    val setting = Settings.byKey(k) ?: ConfigScope.split(k)?.second?.let(Settings::byKey) ?: continue
                    runCatching { put(this, setting, v, k) }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(editor: SharedPreferences.Editor, setting: Setting<*>, value: Any, key: String = setting.key) {
        when (setting) {
            is BoolSetting -> editor.putBoolean(key, value as Boolean)
            is IntSetting -> editor.putInt(key, value as Int)
            is FloatSetting -> editor.putFloat(key, value as Float)
            is StringSetting, is ChoiceSetting -> editor.putString(key, value as String)
            is StringSetSetting -> editor.putStringSet(key, value as Set<String>)
        }
    }

    /** 清掉某一层的全部覆盖（作用域页“清空这一层”）。 */
    fun clearLayer(layer: String): Unit = atomically {
        val keys = local.all.keys.filter { ConfigScope.split(it)?.first == layer }
        if (keys.isEmpty()) return@atomically
        local.edit { keys.forEach { remove(it) } }
        _values.value = snapshot()
        ModuleService.service?.let { s -> runCatching { s.getRemotePreferences(SETTINGS_GROUP).edit { keys.forEach { remove(it) } } } }
    }

    override fun get(setting: BoolSetting) = reader[setting]
    override fun get(setting: IntSetting) = reader[setting]
    override fun get(setting: FloatSetting) = reader[setting]
    override fun get(setting: StringSetting) = reader[setting]
    override fun get(setting: StringSetSetting) = reader[setting]
    override fun get(setting: ChoiceSetting) = reader[setting]

    companion object {
        /** 【宿主】在 [startedAt] 启动，是不是在待重启的改动（[pendingSince] 起）之后。旧版本不报启动时间（0）不算。 */
        fun restartedAfter(pendingSince: Long, startedAt: Long): Boolean = pendingSince > 0 && startedAt > pendingSince
    }
}
