package io.github.clearkit.starter.hook.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.clearkit.starter.model.bridge.Bridge
import io.github.clearkit.starter.model.bridge.SettingsChange
import io.github.clearkit.starter.model.bridge.SettingsSnapshot
import io.github.clearkit.starter.model.bridge.SettingsWriteResult
import io.github.clearkit.starter.model.settings.Setting

/**
 * 【宿主】里改设置：经桥接交给 App（App 按设置定义校验后写入，并同步到 LSPosed 远程设置），
 * App 确认后本进程的 [HostSettings] 立刻按新值生效。【宿主】进程自己不能写 RemotePreferences。
 */
object SettingsWriter {
    /** [reachable] 为 false 表示没连上【模块】（什么都没写）。 */
    data class Result(val reachable: Boolean, val applied: List<String>, val rejected: Map<String, String>, val needsRestart: List<String>) {
        val ok: Boolean get() = reachable && rejected.isEmpty()
    }

    private val main = Handler(Looper.getMainLooper())

    private val restart = RestartTracker()

    /** 本次运行里改过、要重启【宿主】才生效的键（设置菜单底部的重启提示条用）。 */
    val pendingRestart: Set<String> get() = restart.pending

    fun encode(values: Map<Setting<*>, Any>, reset: Collection<Setting<*>>): SettingsChange {
        val bools = HashMap<String, Boolean>()
        val ints = HashMap<String, Int>()
        val floats = HashMap<String, Float>()
        val strings = HashMap<String, String>()
        val sets = HashMap<String, List<String>>()
        for ((s, v) in values) when (v) {
            is Boolean -> bools[s.key] = v
            is Int -> ints[s.key] = v
            is Float -> floats[s.key] = v
            is String -> strings[s.key] = v
            is Set<*> -> sets[s.key] = v.map { it.toString() }
            else -> error("unsupported value for ${s.key}: $v")
        }
        return SettingsChange(SettingsSnapshot(bools, ints, floats, strings, sets), reset.map { it.key })
    }

    /** 写入；[onResult] 在主线程回调。 */
    fun write(
        context: Context,
        settings: HostSettings,
        values: Map<Setting<*>, Any>,
        reset: Collection<Setting<*>> = emptyList(),
        onResult: (Result) -> Unit = {},
    ) {
        if (values.isEmpty() && reset.isEmpty()) {
            main.post { onResult(Result(true, emptyList(), emptyMap(), emptyList())) }
            return
        }
        val json = Bridge.json.encodeToString(SettingsChange.serializer(), encode(values, reset))
        BridgeClient.post(context.applicationContext, Bridge.METHOD_SETTINGS_SET, json) { reply ->
            val result = reply?.getString(Bridge.KEY_JSON)
                ?.let { runCatching { Bridge.json.decodeFromString(SettingsWriteResult.serializer(), it) }.getOrNull() }
            main.post {
                if (result == null) {
                    ModuleLog.w("settings write not delivered: ${values.keys + reset}")
                    onResult(Result(false, emptyList(), emptyMap(), emptyList()))
                    return@post
                }
                val byKey = values.mapKeys { it.key.key }
                val resetKeys = reset.map { it.key }.toSet()
                val local = result.applied.associateWith { k -> if (k in resetKeys) null else byKey[k] }
                // 需重启的项记下第一次改之前的值；改回原值就不再算“待重启”。
                restart.before(result.needsRestart, current(settings))
                settings.applyLocal(local)
                restart.after(result.needsRestart, current(settings))
                if (result.rejected.isNotEmpty()) ModuleLog.w("settings rejected by app: ${result.rejected}")
                onResult(Result(true, result.applied, result.rejected, result.needsRestart))
            }
        }
    }

    /**
     * 请 App 用 root 重启【宿主】；没有 root 时回调 false，由调用方退回到普通重启（重开首页再结束进程）。
     */
    fun requestRootRestart(context: Context, onResult: (Boolean) -> Unit) {
        BridgeClient.post(context.applicationContext, Bridge.METHOD_HOST_RESTART) { reply ->
            val rooted = reply?.getBoolean(Bridge.KEY_OK) == true
            main.post { onResult(rooted) }
        }
    }

    /**
     * 分组批量（批量开关、恢复本组默认）：App 先存回滚点再写入，记成一条操作。结果和 [write] 一样，
     * [onResult] 第二个参数是回滚点 id。
     */
    fun batch(
        context: Context,
        settings: HostSettings,
        label: String,
        values: Map<Setting<*>, Any>,
        reset: Collection<Setting<*>>,
        onResult: (Result, Long) -> Unit,
    ) {
        val request = io.github.clearkit.starter.model.bridge.SettingsBatchRequest(label, encode(values, reset))
        val json = Bridge.json.encodeToString(io.github.clearkit.starter.model.bridge.SettingsBatchRequest.serializer(), request)
        BridgeClient.post(context.applicationContext, Bridge.METHOD_SETTINGS_BATCH, json) { reply ->
            val r = reply?.getString(Bridge.KEY_JSON)
                ?.let { runCatching { Bridge.json.decodeFromString(io.github.clearkit.starter.model.bridge.SettingsBatchResult.serializer(), it) }.getOrNull() }
            main.post {
                if (r == null) { onResult(Result(false, emptyList(), emptyMap(), emptyList()), 0); return@post }
                val byKey = values.mapKeys { it.key.key }
                val resetKeys = reset.map { it.key }.toSet()
                restart.before(r.result.needsRestart, current(settings))
                settings.applyLocal(r.result.applied.associateWith { k -> if (k in resetKeys) null else byKey[k] })
                restart.after(r.result.needsRestart, current(settings))
                onResult(Result(true, r.result.applied, r.result.rejected, r.result.needsRestart), r.pointId)
            }
        }
    }

    /** 回滚（撤销一条、回到某条之前、恢复回滚点）后，把 App 写入的值立刻用到本进程。 */
    fun applyRollback(settings: HostSettings, result: io.github.clearkit.starter.model.bridge.RollbackResult) {
        val local = HashMap<String, Any?>(result.values.toMap())
        result.reset.forEach { local[it] = null }
        restart.before(result.needsRestart, current(settings))
        if (local.isNotEmpty()) settings.applyLocal(local)
        restart.after(result.needsRestart, current(settings))
    }

    /** 按键取本进程现在的值（含默认值）；不认识的键为 null。 */
    private fun current(settings: HostSettings): (String) -> Any? = { key ->
        io.github.clearkit.starter.model.settings.Settings.byKey(key)?.let { settings.value(it) }
    }
}
