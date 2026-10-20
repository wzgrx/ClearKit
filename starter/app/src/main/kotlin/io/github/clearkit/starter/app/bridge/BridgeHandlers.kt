package io.github.clearkit.starter.app.bridge

import android.os.Bundle
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.data.HostControl
import io.github.clearkit.starter.model.adapt.AdaptProgress
import io.github.clearkit.starter.model.bridge.Bridge
import io.github.clearkit.starter.model.bridge.SettingsBatchRequest
import io.github.clearkit.starter.model.bridge.SettingsBatchResult
import io.github.clearkit.starter.model.bridge.SettingsChange
import io.github.clearkit.starter.model.bridge.SettingsSnapshot
import io.github.clearkit.starter.model.bridge.SettingsWriteResult
import io.github.clearkit.starter.model.settings.Setting
import io.github.clearkit.starter.model.settings.SettingWrite
import io.github.clearkit.starter.model.settings.Settings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer

/** 一次桥接调用：方法名、`arg` 和 [Bridge.KEY_JSON] 里的正文。 */
internal class BridgeCall(val app: ModuleApp, val method: String, val arg: String?, val json: String?) {
    /** 必须有的 JSON 正文；没有或解析失败抛 IllegalArgumentException，由 BridgeProvider 统一回错误。 */
    fun <T> body(serializer: KSerializer<T>): T = Bridge.json.decodeFromString(serializer, requireNotNull(json) { "$method 缺少正文" })
}

internal typealias BridgeHandler = (BridgeCall) -> Bundle

internal fun bridgeOk(): Bundle = Bundle().apply { putBoolean(Bridge.KEY_OK, true) }

internal fun bridgeFail(message: String): Bundle = Bundle().apply { putBoolean(Bridge.KEY_OK, false); putString(Bridge.KEY_ERROR, message) }

internal fun <T> bridgeReply(serializer: KSerializer<T>, value: T): Bundle =
    bridgeOk().apply { putString(Bridge.KEY_JSON, Bridge.json.encodeToString(serializer, value)) }

/** 桥接方法表：方法名 → 处理，按功能分组登记。 */
internal object BridgeHandlers {
    val registry: Map<String, BridgeHandler> = buildMap {
        runtime()
        settings()
    }

    // ---- 运行状态、本机适配、重启 ----

    private fun MutableMap<String, BridgeHandler>.runtime() {
        put(Bridge.METHOD_PING) { bridgeOk() }
        put(Bridge.METHOD_STATUS) { c -> c.json?.let(c.app.status::update); bridgeOk() }
        put(Bridge.METHOD_STATS) { c -> c.json?.let(c.app.stats::update); bridgeOk() }
        put(Bridge.METHOD_HOST_ADAPT) { c ->
            // 【宿主】启动时没有可用的符号表：后台判断并本机扫描，结果经 LSPosed 远程文件送回，【宿主】那边轮询后热适配
            val pkg = c.arg?.substringBefore(':')?.takeIf { it.isNotEmpty() }
            c.app.hostAdapter.check("【宿主】启动时请求（${c.arg.orEmpty()}）", pkg)
            bridgeOk().apply { putString(Bridge.KEY_JSON, "已开始检查") }
        }
        put(Bridge.METHOD_HOST_ADAPT_STATUS) { c ->
            // 【宿主】里的适配卡片每半秒来问一次进度
            val s = c.app.hostAdapter.active.value
            bridgeReply(AdaptProgress.serializer(), AdaptProgress(s.phase.name, s.progress, s.total, s.message))
        }
        put(Bridge.METHOD_HOST_RESTART) { c ->
            // force-stop 会结束调用方本身，所以先回复、再在后台执行。
            val rooted = HostControl.rootAvailable()
            val pkg = c.app.status.status.value?.hostPackage
            if (rooted && pkg != null) Thread({ runBlocking { HostControl.restart(c.app, pkg) } }, "bc-restart").apply { isDaemon = true }.start()
            Bundle().apply { putBoolean(Bridge.KEY_OK, rooted && pkg != null) }
        }
    }

    // ---- 设置 ----

    private fun MutableMap<String, BridgeHandler>.settings() {
        put(Bridge.METHOD_SETTINGS) { c -> bridgeReply(SettingsSnapshot.serializer(), SettingsSnapshot.of(c.app.settings.stored())) }
        put(Bridge.METHOD_SETTINGS_SET) { c -> bridgeReply(SettingsWriteResult.serializer(), write(c.app, c.body(SettingsChange.serializer()))) }
        put(Bridge.METHOD_SETTINGS_BATCH) { c ->
            val r = c.body(SettingsBatchRequest.serializer())
            bridgeReply(SettingsBatchResult.serializer(), SettingsBatchResult(write(c.app, r.change)))
        }
    }

    /**
     * 【宿主】里改的设置：每项按 Setting 定义校验（SettingWrite），合格的写入、恢复默认的删除，结果逐项返回。
     * 写入走 SettingsStore，和在 App 里改一样会同步到 LSPosed 远程设置并记下“需重启”。
     */
    private fun write(app: ModuleApp, change: SettingsChange): SettingsWriteResult {
        val outcome = SettingWrite.check(change.values.toMap())
        @Suppress("UNCHECKED_CAST")
        outcome.accepted.forEach { (setting, value) -> app.settings.set(setting as Setting<Any>, value) }
        val resetRejected = LinkedHashMap<String, String>()
        val reset = change.reset.mapNotNull { key ->
            val s = Settings.byKey(key)
            if (s == null) { resetRejected[key] = "没有这项设置"; null } else { app.settings.reset(s); key }
        }
        val applied = outcome.accepted.keys.map { it.key } + reset
        return SettingsWriteResult(applied = applied, rejected = outcome.rejected + resetRejected, needsRestart = SettingWrite.needsRestart(applied))
    }
}
