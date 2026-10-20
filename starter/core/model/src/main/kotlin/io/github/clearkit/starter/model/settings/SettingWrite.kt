package io.github.clearkit.starter.model.settings


/**
 * 【宿主】里改设置时（桥接 `settings.set`），App 用这里按 [Setting] 定义逐项校验再写入。
 * 和编辑器共用 [SettingInput] 的规则，两边接受的值一致。
 */
object SettingWrite {
    const val MAX_TEXT = 4096
    const val MAX_SET_SIZE = 5000
    const val MAX_SET_ITEM = 512

    /** [accepted]：规范化后要写入的值；[rejected]：键 → 原因（中文，直接显示给用户）。 */
    data class Outcome(val accepted: Map<Setting<*>, Any>, val rejected: Map<String, String>)

    fun check(values: Map<String, Any?>): Outcome {
        val accepted = LinkedHashMap<Setting<*>, Any>()
        val rejected = LinkedHashMap<String, String>()
        for ((key, raw) in values) {
            val setting = Settings.byKey(key)
            if (setting == null) {
                rejected[key] = "没有这项设置"
                continue
            }
            when (val r = checkOne(setting, raw)) {
                is Result.Ok -> accepted[setting] = r.value
                is Result.Bad -> rejected[key] = r.reason
            }
        }
        return Outcome(accepted, rejected)
    }

    sealed interface Result {
        data class Ok(val value: Any) : Result
        data class Bad(val reason: String) : Result
    }

    fun checkOne(setting: Setting<*>, raw: Any?): Result {
        if (raw == null) return Result.Bad("值为空")
        return when (setting) {
            is BoolSetting -> (raw as? Boolean)?.let { Result.Ok(it) } ?: Result.Bad("应为开或关")
            is IntSetting -> {
                val n = when (raw) {
                    is Int -> raw.toLong()
                    is Long -> raw
                    is Float, is Double -> (raw as Number).toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toLong()
                    else -> null
                } ?: return Result.Bad("应为整数")
                val r = setting.range
                if (n < r.first || n > r.last) Result.Bad("超出范围 ${r.first}–${if (r.last == Int.MAX_VALUE) "∞" else r.last}") else Result.Ok(n.toInt())
            }
            is FloatSetting -> {
                val v = (raw as? Number)?.toFloat() ?: return Result.Bad("应为数字")
                if (!v.isFinite()) return Result.Bad("应为数字")
                Result.Ok(v)
            }
            is ChoiceSetting -> {
                val id = raw as? String ?: return Result.Bad("应为选项")
                if (setting.options.none { it.id == id }) Result.Bad("没有“$id”这个选项") else Result.Ok(id)
            }
            is StringSetting -> {
                val text = raw as? String ?: return Result.Bad("应为文字")
                if (text.length > MAX_TEXT) return Result.Bad("太长了（最多 $MAX_TEXT 字）")
                if (setting.format == TextFormat.PLAIN) return Result.Ok(text)
                checkText(setting.format, text.trim())
            }
            is StringSetSetting -> {
                val items = when (raw) {
                    is Set<*> -> raw
                    is Collection<*> -> raw
                    else -> return Result.Bad("应为列表")
                }
                if (items.any { it !is String }) return Result.Bad("列表里只能是文字")
                val set = items.map { it as String }.toSet()
                if (set.size > MAX_SET_SIZE) return Result.Bad("最多 $MAX_SET_SIZE 项")
                set.firstOrNull { it.length > MAX_SET_ITEM }?.let { return Result.Bad("单项最多 $MAX_SET_ITEM 字") }
                if (setting.choices.isNotEmpty()) {
                    val unknown = set.filter { id -> setting.choices.none { it.id == id } }
                    if (unknown.isNotEmpty()) return Result.Bad("不认识的选项：${unknown.joinToString("、")}")
                }
                Result.Ok(set)
            }
        }
    }

    /** 有格式要求的文字：网址、时间段（`HH:mm-HH:mm`）、正则表达式、颜色。空值都允许（表示不用）。 */
    fun checkText(format: TextFormat, text: String): Result {
        if (text.isEmpty()) return Result.Ok(text)
        return when (format) {
            TextFormat.PLAIN -> Result.Ok(text)
            TextFormat.URL -> if (Regex("https?://[^\\s]+").matches(text)) Result.Ok(text) else Result.Bad("应为 http:// 或 https:// 开头的网址")
            TextFormat.TIME_WINDOW -> if (Regex("([01]\\d|2[0-3]):[0-5]\\d-([01]\\d|2[0-3]):[0-5]\\d").matches(text)) Result.Ok(text) else Result.Bad("应为 22:00-07:00 这样的时间段")
            TextFormat.COLOR -> if (Regex("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})").matches(text)) Result.Ok(text) else Result.Bad("应为八位色值（如 FFFFFFFF，前两位是透明度）或六位色值")
            TextFormat.HOST -> if (Regex("[A-Za-z0-9.-]+(:\\d{1,5})?").matches(text)) Result.Ok(text) else Result.Bad("只写主机和端口，例如 api.example.com 或 api.example.com:8443，不要 https:// 和路径")
            TextFormat.SECRET -> if (text.any { it.isWhitespace() }) Result.Bad("不能有空格或换行") else Result.Ok(text)
            TextFormat.REGEX -> runCatching { Regex(text) }.fold({ Result.Ok(text) }, { Result.Bad("正则表达式写法不对：${it.message?.lineSequence()?.firstOrNull().orEmpty()}") })
        }
    }

    /** 写入后需要重启【宿主】才生效的键（用于“N 项改动在重启后生效”）。 */
    fun needsRestart(keys: Collection<String>): List<String> = keys.filter { Settings.byKey(it)?.needsRestart == true }

}
