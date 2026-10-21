package io.github.clearkit.starter.model.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 【宿主】进程 → 模块 App 的通道：`ContentResolver.call(Bridge.URI, method, arg, extras)`。
 * App 的 Provider 只回应【宿主】的 UID；数据以 JSON 字符串放在 [KEY_JSON] 里，两边按同一份结构解析。
 */
object Bridge {
    const val AUTHORITY = "io.github.clearkit.starter.bridge"
    const val URI = "content://$AUTHORITY"
    const val KEY_JSON = "json"
    const val KEY_OK = "ok"
    const val KEY_ERROR = "error"

    /** 没有界面的 Activity：【宿主】显式打开它一次，系统就让【宿主】看得见 [AUTHORITY]（Android 11+ 的包可见性）。 */
    const val HANDSHAKE_ACTIVITY = "io.github.clearkit.starter.app.bridge.HandshakeActivity"

    /** 从【宿主】打开模块页面：放进【宿主】当前的任务，按一次返回回到原页面；页面由 [EXTRA_ROUTE] 指定。 */
    const val ROUTE_ACTIVITY = "io.github.clearkit.starter.app.RouteActivity"
    const val EXTRA_ROUTE = "route"

    const val METHOD_PING = "ping"
    /** Body：[RuntimeStatus]。【宿主】启动、功能状态变化时报给 App。 */
    const val METHOD_STATUS = "runtime.status"
    /** 回复 KEY_JSON：[SettingsSnapshot]，App 当前的全部设置（【宿主】回到前台时拉一次）。 */
    const val METHOD_SETTINGS = "settings.snapshot"
    /** 没有可用适配表时请 App 在后台本机适配；arg：客户端包名 + 版本号（`包名:版本号`）。 */
    const val METHOD_HOST_ADAPT = "host.adapt"
    /** 回复 KEY_JSON：[io.github.clearkit.starter.model.adapt.AdaptProgress]，本机适配的当前进度（适配卡片用）。 */
    const val METHOD_HOST_ADAPT_STATUS = "host.adaptStatus"
    /** 【宿主】里改设置：body 是 [SettingsChange]，App 按设置定义逐项校验后写入，回复 KEY_JSON：[SettingsWriteResult]。 */
    const val METHOD_SETTINGS_SET = "settings.set"
    /** 分组批量改设置（先存回滚点）：body 是 [SettingsBatchRequest]，回复 KEY_JSON：[SettingsBatchResult]。 */
    const val METHOD_SETTINGS_BATCH = "settings.batch"
    /** 【宿主】里的计数（这周挡下几条、开关起作用几次）：body 是 [HostStats]，App 存最近一份。 */
    const val METHOD_STATS = "host.stats"
    /**
     * 免 Root 加载（LSPatch / NPatch，没有 LSPosed 远程文件）时取本机适配表：arg 是“包名:版本号”，
     * 回复 KEY_JSON 是 App 存的这一版本机表（没有时不带）。【宿主】拿到后存进自己的目录，下次启动直接用。
     */
    const val METHOD_SYMBOL_TABLE = "symbols.table"
    /** 让 App 用 root 重启【宿主】（不清数据）；回复 KEY_OK=false 表示没有 root，由【宿主】自己退回普通重启。 */
    const val METHOD_HOST_RESTART = "host.restart"

    /**
     * 模块更新后、【宿主】重启前，两边的模型类版本可能不同：新字段旧类忽略（ignoreUnknownKeys），
     * 认不出的枚举值回退到属性默认值（coerceInputValues，所以 App 发给【宿主】的模型里枚举属性都要有默认值）。
     */
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
}

@Serializable
data class RuntimeStatus(
    val moduleVersion: String,
    val hostPackage: String,
    val hostVersionCode: Long,
    val hostVersionName: String,
    /** 有可用的适配表（内置或本机）。 */
    val adapted: Boolean,
    val safeMode: Boolean,
    val masterEnabled: Boolean,
    val features: List<FeatureStatus>,
    val reportedAt: Long,
    /** 这次【宿主】进程开始运行的时间（App 据此判断“需重启”的改动是否已被新进程读到）。 */
    val startedAt: Long = 0,
    /** 读得到 LSPosed 的远程设置；false 是免 Root 加载（LSPatch / NPatch 修补的【宿主】），设置从 App 拉取并缓存。 */
    val remoteSettings: Boolean = true,
    /** 这次运行的适配报告：App 的适配页、首页和【宿主】里的卡片说同样的话。 */
    val report: io.github.clearkit.starter.model.adapt.AdaptReport? = null,
)

@Serializable
data class FeatureStatus(
    val id: String,
    val title: String,
    val state: State = State.UNAVAILABLE,
    /** 不是 ACTIVE / OFF 时的原因（中文）。 */
    val detail: String? = null,
) {
    /**
     * OBSERVING：本机适配的新版本上先观察（只记录、不改行为），契约验证通过后才生效。
     * RESTART_PENDING：已经装上，但它在启动时就要起作用（开屏、底栏……），这次运行里适配表来晚了，重开【宿主】后生效。
     * 旧版 App 不认识新状态时按 coerceInputValues 回退成默认值 UNAVAILABLE。
     */
    enum class State { ACTIVE, OFF, UNAVAILABLE, FAILED, CRASH_DISABLED, SAFE_MODE_OFF, OBSERVING, RESTART_PENDING }
}

/**
 * 计数：[totals] 跨次累计（存在【宿主】的偏好文件里，比如 `sponsor.<分类>` 跳过次数），[process] 只算这次运行
 * （比如 `w<年周>.card` 这周去掉的条数）。只有次数，没有内容。
 */
@Serializable
data class HostStats(
    val hostPackage: String = "",
    val totals: Map<String, Long> = emptyMap(),
    val process: Map<String, Long> = emptyMap(),
    val startedAt: Long = 0,
    val reportedAt: Long = 0,
)

/** 显式设置过的值；没有的键表示默认值。 */
@Serializable
data class SettingsSnapshot(
    val bools: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    val strings: Map<String, String> = emptyMap(),
    val sets: Map<String, List<String>> = emptyMap(),
) {
    fun toMap(): Map<String, Any> = bools + ints + floats + strings + sets.mapValues { it.value.toSet() }

    companion object {
        fun of(values: Map<String, Any?>): SettingsSnapshot = SettingsSnapshot(
            bools = values.filterValues { it is Boolean }.mapValues { it.value as Boolean },
            ints = values.filterValues { it is Int }.mapValues { it.value as Int },
            floats = values.filterValues { it is Float }.mapValues { it.value as Float },
            strings = values.filterValues { it is String }.mapValues { it.value as String },
            sets = values.filterValues { it is Set<*> }.mapValues { (it.value as Set<*>).map(Any?::toString) },
        )
    }
}

/** 【宿主】里改设置：[values] 是新值，[reset] 是要恢复默认（删除）的键。 */
@Serializable
data class SettingsChange(
    val values: SettingsSnapshot = SettingsSnapshot(),
    val reset: List<String> = emptyList(),
)

/** [SettingsChange] 的结果：[applied] 已写入（含恢复默认的键），[rejected] 键 → 原因，[needsRestart] 其中要重启【宿主】才生效的键。 */
@Serializable
data class SettingsWriteResult(
    val applied: List<String> = emptyList(),
    val rejected: Map<String, String> = emptyMap(),
    val needsRestart: List<String> = emptyList(),
)

/** 回滚结果：写入的新值和恢复默认的键（【宿主】据此立刻更新本进程的设置），没动的冲突键。 */
@Serializable
data class RollbackResult(
    val ok: Boolean,
    val message: String,
    val values: SettingsSnapshot = SettingsSnapshot(),
    val reset: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val needsRestart: List<String> = emptyList(),
)

/** 分组批量开关、恢复本组默认：[label] 记进操作历史；先存回滚点。 */
@Serializable
data class SettingsBatchRequest(val label: String, val change: SettingsChange, val source: String = "BATCH")

@Serializable
data class SettingsBatchResult(val result: SettingsWriteResult = SettingsWriteResult(), val pointId: Long = 0)

