package io.github.clearkit.starter.model.settings

/**
 * 一项用户设置。存在模块的 RemotePreferences 分组 [SETTINGS_GROUP] 里：模块 App 写，【宿主】进程实时读。
 */
sealed class Setting<T : Any>(
    val key: String,
    val default: T,
    val title: String,
    val summary: String?,
    /** True when the host must restart before a change takes effect. */
    val needsRestart: Boolean,
) {
    /**
     * 设置页上已经有这一项，但功能还没实现。界面显示“即将支持”并禁用开关，
     * 实现后在 [Settings] 里去掉标记即可。只能在声明时由 [SettingGroup] 设定。
     */
    var comingSoon: Boolean = false
        internal set

    /**
     * 旧模块里对应的键（从别的模块迁移设置时用；新模块不用填）。按它把旧值迁移过来
     * （见 [LegacySettings]）；没有对应的旧设置为 null。写成 `gate&key` 时，只有旧版的 gate 开关开着才迁移
     * （旧版很多净化项要先开“启用隐藏功能”才生效）。
     */
    var legacyKey: String? = null
        internal set

    override fun toString() = key
    override fun equals(other: Any?) = other is Setting<*> && other.key == key
    override fun hashCode() = key.hashCode()
}

class BoolSetting(key: String, default: Boolean, title: String, summary: String? = null, needsRestart: Boolean = false) :
    Setting<Boolean>(key, default, title, summary, needsRestart)

class IntSetting(
    key: String,
    default: Int,
    title: String,
    summary: String? = null,
    needsRestart: Boolean = false,
    val range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    /** Shown after the value, e.g. "天", "赞". */
    val unit: String = "",
    /** Preset values offered as chips; 0 usually means 不限. */
    val presets: List<Int> = emptyList(),
) : Setting<Int>(key, default.coerceIn(range), title, summary, needsRestart)

class FloatSetting(
    key: String,
    default: Float,
    title: String,
    summary: String? = null,
    needsRestart: Boolean = false,
    val presets: List<Float> = emptyList(),
) : Setting<Float>(key, default, title, summary, needsRestart)

class StringSetting(
    key: String,
    default: String,
    title: String,
    summary: String? = null,
    needsRestart: Boolean = false,
    /** 决定用哪种编辑器、怎么校验（见 [SettingInput.checkText]）。 */
    val format: TextFormat = TextFormat.PLAIN,
    /** 编辑器里的预设：[ChoiceSetting.Option.id] 是值，label 是说明。 */
    val presets: List<ChoiceSetting.Option> = emptyList(),
) : Setting<String>(key, default, title, summary, needsRestart)

enum class TextFormat {
    PLAIN,
    /** 网址（解析服务器、WebDAV 等），要以 http:// 或 https:// 开头。 */
    URL,
    /** `HH:mm-HH:mm`，空 = 不限。 */
    TIME_WINDOW,
    /** 正则表达式（过滤规则），保存前校验能编译。 */
    REGEX,
    /** 颜色：十六进制 AARRGGBB 或 RRGGBB，可带 #。 */
    COLOR,
    /** 主机（可带端口），不带协议、路径和凭据。 */
    HOST,
    /** 凭据（访问密钥一类）：界面上遮挡显示，不进日志。 */
    SECRET,
}

/**
 * A set of ids (hidden tabs, blocked user ids, keywords...). Order is not kept. [choices]: the
 * fixed options when the set is a multi-select (empty = free-form list).
 */
class StringSetSetting(
    key: String,
    default: Set<String>,
    title: String,
    summary: String? = null,
    needsRestart: Boolean = false,
    val choices: List<ChoiceSetting.Option> = emptyList(),
) : Setting<Set<String>>(key, default, title, summary, needsRestart)

/** One of a fixed list of options, stored by [Option.id]. */
class ChoiceSetting(
    key: String,
    default: String,
    title: String,
    val options: List<Option>,
    summary: String? = null,
    needsRestart: Boolean = false,
) : Setting<String>(key, default, title, summary, needsRestart) {
    data class Option(val id: String, val label: String)

    init {
        require(options.any { it.id == default }) { "$key: default $default not in options" }
    }

    fun label(id: String) = options.firstOrNull { it.id == id }?.label ?: id
}

const val SETTINGS_GROUP = "settings"

/** Read access shared by the app (local store) and the host (RemotePreferences). */
interface SettingsReader {
    operator fun get(setting: BoolSetting): Boolean
    operator fun get(setting: IntSetting): Int
    operator fun get(setting: FloatSetting): Float
    operator fun get(setting: StringSetting): String
    operator fun get(setting: StringSetSetting): Set<String>
    operator fun get(setting: ChoiceSetting): String

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> value(setting: Setting<T>): T = when (setting) {
        is BoolSetting -> get(setting)
        is IntSetting -> get(setting)
        is FloatSetting -> get(setting)
        is StringSetting -> get(setting)
        is StringSetSetting -> get(setting)
        is ChoiceSetting -> get(setting)
    } as T
}

/** Reads settings from an Android-free key/value source; unknown or malformed values fall back to defaults. */
class MapSettingsReader(private val source: (String) -> Any?) : SettingsReader {
    override fun get(setting: BoolSetting) = source(setting.key) as? Boolean ?: setting.default
    override fun get(setting: IntSetting) = (source(setting.key) as? Int)?.coerceIn(setting.range) ?: setting.default
    override fun get(setting: FloatSetting) = source(setting.key) as? Float ?: setting.default
    override fun get(setting: StringSetting) = source(setting.key) as? String ?: setting.default

    @Suppress("UNCHECKED_CAST")
    override fun get(setting: StringSetSetting) = (source(setting.key) as? Set<String>) ?: setting.default
    override fun get(setting: ChoiceSetting): String {
        val id = source(setting.key) as? String ?: return setting.default
        return if (setting.options.any { it.id == id }) id else setting.default
    }
}
