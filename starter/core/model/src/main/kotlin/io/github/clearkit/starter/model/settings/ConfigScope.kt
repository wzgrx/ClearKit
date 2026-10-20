package io.github.clearkit.starter.model.settings

/**
 * 配置作用域（设计 026、084）：各个渠道可以各自覆盖一部分设置，没覆盖的用全局值。
 * 覆盖值和全局值存在同一份偏好里，键写成 `层|设置键`（例如 `play|player.defaultSpeed`）。
 * 模块自身、作用域本身、外观和一次性令牌只有全局值。
 * 账号层（`u<UID>`，旧版 per_account_configuration）叠在最上面：【宿主】里按当前登录的账号读，
 * 顺序是 账号覆盖 → 客户端覆盖 → 全局值。
 */
object ConfigScope {
    const val GLOBAL = "global"
    /** 全局 + 每个渠道一层（渠道见 HostBuild.Channel）。 */
    val LAYERS: List<Pair<String, String>> = listOf(GLOBAL to "全局") +
        io.github.clearkit.starter.model.HostBuild.Channel.entries.map { it.name.lowercase() to it.label }
    private const val SEP = "|"

    fun clientOf(hostPackage: String): String =
        (io.github.clearkit.starter.model.HostBuild.channelOf(hostPackage) ?: io.github.clearkit.starter.model.HostBuild.Channel.entries.first()).name.lowercase()

    fun label(layer: String) = LAYERS.firstOrNull { it.first == layer }?.second ?: accountMid(layer)?.let { "账号 $it" } ?: layer

    /** 账号层的名字：`u` + UID。 */
    fun accountLayer(mid: Long) = "u$mid"

    /** 账号层 → UID；不是账号层返回 null。 */
    fun accountMid(layer: String): Long? = layer.takeIf { it.startsWith("u") }?.drop(1)?.toLongOrNull()?.takeIf { it > 0 }

    /** 存储里出现过覆盖的账号层。 */
    fun accountLayers(stored: Map<String, Any?>): List<String> =
        stored.keys.mapNotNull { split(it)?.first }.filter { accountMid(it) != null }.distinct().sorted()

    fun scopable(s: Setting<*>): Boolean = s.key.substringBefore('.') !in GLOBAL_ONLY && s !in Settings.transient

    private val GLOBAL_ONLY = setOf("module", "scope", "appearance")

    fun key(layer: String, key: String) = "$layer$SEP$key"

    /** `层|键` → (层, 键)；不是覆盖键返回 null。 */
    fun split(raw: String): Pair<String, String>? {
        val i = raw.indexOf(SEP)
        if (i <= 0) return null
        return raw.substring(0, i) to raw.substring(i + 1)
    }

    /** 某一层的覆盖：设置键 → 值。 */
    fun overrides(stored: Map<String, Any?>, layer: String): Map<String, Any?> {
        if (layer == GLOBAL) return emptyMap()
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in stored) { val (l, key) = split(k) ?: continue; if (l == layer) out[key] = v }
        return out
    }

    /** 【宿主】里读一项：账号覆盖 → 客户端覆盖 → 全局值。 */
    fun lookup(stored: Map<String, Any?>, key: String, client: String?, account: String?): Any? =
        account?.let { stored[key(it, key)] } ?: client?.let { stored[key(it, key)] } ?: stored[key]

    /** 这一层实际生效的值：全局值叠上这一层的覆盖。 */
    fun effective(stored: Map<String, Any?>, layer: String?): Map<String, Any?> {
        if (layer == null || layer == GLOBAL) return stored
        val ov = overrides(stored, layer)
        if (ov.isEmpty()) return stored
        return HashMap(stored).apply { putAll(ov) }
    }
}
