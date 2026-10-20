package io.github.clearkit.starter.model.config

import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.ChoiceSetting
import io.github.clearkit.starter.model.settings.FloatSetting
import io.github.clearkit.starter.model.settings.IntSetting
import io.github.clearkit.starter.model.settings.Setting
import io.github.clearkit.starter.model.settings.SettingWrite
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.model.settings.StringSetSetting
import io.github.clearkit.starter.model.settings.StringSetting
import io.github.clearkit.starter.model.settings.TextFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 配置文档（设计 027、028、030、133）：导出、备份、版本历史、远程备份共用的一份 JSON。
 *
 * - 只带用户显式保存过的设置（没保存过的是默认值，不写），不带凭据（访问密钥、语义过滤密钥这类 [TextFormat.SECRET]）
 *   和一次性令牌（[Settings.transient]）。
 * - 读入时也认偏好 XML（新版自己的；设置带 legacy 键时也按旧键迁移）。
 * - 恢复先出计划（[plan]）：逐项列出新增、修改、恢复默认，值不合法的、不认识的键单独列出，不写入。
 */
object ConfigDocument {
    const val FORMAT = "clearstarter-config"
    const val VERSION = 1
    /** 读入的文件上限（字节数按字符算）：正常配置几十 KB，名单很长也到不了这个量。 */
    const val MAX_SIZE = 4 * 1024 * 1024

    data class Meta(
        val createdAt: Long,
        /** 来源：手动保存、恢复前自动保存、应用方案前自动保存、导入文件、远程下载……（界面直接显示）。 */
        val source: String,
        val appVersion: String = "",
        /** 用户起的名字，可以空。 */
        val label: String = "",
    )

    enum class Origin(val label: String) {
        CURRENT("【模块】配置"), LEGACY_XML("旧版导出的偏好文件"), PREFS_XML("新版偏好文件"),
    }

    data class Document(
        val meta: Meta,
        /** 键 → 原始值（JSON 里读到的类型；名单是 Set）。 */
        val values: Map<String, Any>,
        val origin: Origin = Origin.CURRENT,
        /** 旧版文件迁移时跳过的旧键 → 原因。 */
        val skipped: Map<String, String> = emptyMap(),
    )

    sealed interface Parsed {
        data class Ok(val document: Document) : Parsed
        data class Bad(val reason: String) : Parsed
    }

    /** 这项设置进不进备份。 */
    fun backupable(s: Setting<*>): Boolean = s !in Settings.transient && !(s is StringSetting && s.format == TextFormat.SECRET)

    /** 从存储里的原值（键 → 值，没保存过的键不在里面）挑出要备份的。 */
    fun collect(stored: Map<String, Any?>): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for (s in Settings.all) {
            if (!backupable(s)) continue
            val v = stored[s.key] ?: continue
            out[s.key] = v
        }
        return out
    }

    fun export(stored: Map<String, Any?>, meta: Meta): String = encode(Document(meta, collect(stored)))

    fun encode(doc: Document): String {
        val values = LinkedHashMap<String, JsonElement>()
        for ((k, v) in doc.values.toSortedMap()) values[k] = toJson(v) ?: continue
        val root = JsonObject(
            linkedMapOf(
                "format" to JsonPrimitive(FORMAT),
                "version" to JsonPrimitive(VERSION),
                "createdAt" to JsonPrimitive(doc.meta.createdAt),
                "source" to JsonPrimitive(doc.meta.source),
                "appVersion" to JsonPrimitive(doc.meta.appVersion),
                "label" to JsonPrimitive(doc.meta.label),
                "values" to JsonObject(values),
            ),
        )
        return PRETTY.encodeToString(JsonObject.serializer(), root)
    }

    private val PRETTY = Json { prettyPrint = true }

    private fun toJson(v: Any): JsonElement? = when (v) {
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Float -> JsonPrimitive(v)
        is Double -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        is Set<*> -> JsonArray(v.filterIsInstance<String>().sorted().map(::JsonPrimitive))
        else -> null
    }

    fun parse(text: String, now: Long = System.currentTimeMillis()): Parsed {
        if (text.length > MAX_SIZE) return Parsed.Bad("文件太大（超过 ${MAX_SIZE / 1024 / 1024} MB），不像配置文件")
        val t = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return when {
            t.startsWith("{") -> parseJson(t)
            t.startsWith("<") -> parseXml(t, now)
            else -> Parsed.Bad("认不出这个文件：既不是【模块】的配置，也不是旧版导出的偏好文件")
        }
    }

    private fun parseJson(text: String): Parsed {
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return Parsed.Bad("数据文件格式不对")
        if ((root["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) return Parsed.Bad("不是【模块】的配置文件（格式标记不对）")
        val version = (root["version"] as? JsonPrimitive)?.longOrNull ?: return Parsed.Bad("缺少版本号")
        if (version > VERSION) return Parsed.Bad("这是更新版本的【模块】导出的（格式 $version），请先更新【模块】")
        val values = root["values"] as? JsonObject ?: return Parsed.Bad("缺少设置内容")
        val out = LinkedHashMap<String, Any>()
        for ((k, e) in values) fromJson(e)?.let { out[k] = it }
        fun str(name: String) = (root[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val meta = Meta((root["createdAt"] as? JsonPrimitive)?.longOrNull ?: 0, str("source"), str("appVersion"), str("label"))
        return Parsed.Ok(Document(meta, out))
    }

    private fun fromJson(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.doubleOrNull
        }
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.toSet()
        is JsonObject -> null
    }

    // ---- 偏好 XML（SharedPreferences 的文件格式） ----

    private fun parseXml(text: String, now: Long): Parsed {
        if (text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<!ENTITY", ignoreCase = true)) {
            return Parsed.Bad("偏好文件里有不安全的声明，不读")
        }
        val map = prefsXml(text) ?: return Parsed.Bad("不是偏好文件")
        val ours = map.keys.any { k -> Settings.byKey(k) != null }
        if (ours) {
            val values = LinkedHashMap<String, Any>()
            for ((k, v) in map) v?.let { values[k] = it }
            return Parsed.Ok(Document(Meta(now, "新版偏好文件"), values, Origin.PREFS_XML))
        }
        return Parsed.Bad("这个偏好文件里没有能认出的设置")
    }

    private val ELEMENT = Regex("""<(boolean|int|long|float|string|set)\s+name="([^"]*)"(?:\s+value="([^"]*)")?\s*(/>|>([\s\S]*?)</\1>)""")
    private val SET_ITEM = Regex("""<string>([\s\S]*?)</string>""")

    /** 偏好 XML → 键值（null 表示 `<null>` 一类不认识的）。不是这个格式返回 null。 */
    fun prefsXml(text: String): Map<String, Any?>? {
        if (!text.contains("<map")) return null
        val out = LinkedHashMap<String, Any?>()
        for (m in ELEMENT.findAll(text)) {
            val (type, rawName, value) = m.destructured
            val name = unescape(rawName)
            val body = m.groupValues[5]
            out[name] = when (type) {
                "boolean" -> value.toBooleanStrictOrNull()
                "int" -> value.toIntOrNull()
                "long" -> value.toLongOrNull()
                "float" -> value.toFloatOrNull()
                "string" -> unescape(body)
                "set" -> SET_ITEM.findAll(body).map { unescape(it.groupValues[1]) }.toSet()
                else -> null
            }
        }
        return out
    }

    private fun unescape(s: String): String = s
        .replace(Regex("""&#(x?[0-9a-fA-F]+);""")) { m ->
            val n = m.groupValues[1]
            val code = if (n.startsWith("x")) n.drop(1).toIntOrNull(16) else n.toIntOrNull()
            code?.let { String(Character.toChars(it)) } ?: m.value
        }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    // ---- 恢复计划 ----

    enum class Mode(val label: String, val summary: String) {
        MERGE("只改文件里有的", "文件里没有的设置保持现在的值"),
        REPLACE("整份替换", "文件里没有的设置恢复默认（凭据和一次性令牌不动）"),
    }

    enum class Kind(val label: String) { ADD("新设置"), CHANGE("修改"), RESET("恢复默认") }

    /** 一项变化：[before] / [after] 是存储里的原值，null 表示没保存过（默认值）。 */
    data class Change(val setting: Setting<*>, val kind: Kind, val before: Any?, val after: Any?)

    data class Plan(
        val changes: List<Change>,
        /** 键 → 为什么不写（值不合法、凭据、一次性令牌）。 */
        val rejected: Map<String, String>,
        /** 这个版本不认识的键（可能来自更新的版本），原样列出、不写。 */
        val unknown: List<String>,
        /** 和现在一样、不用改的项数。 */
        val same: Int,
    ) {
        val writes: Map<Setting<*>, Any> get() = changes.filter { it.kind != Kind.RESET }.associate { it.setting to it.after!! }
        val resets: List<Setting<*>> get() = changes.filter { it.kind == Kind.RESET }.map { it.setting }
    }

    fun plan(stored: Map<String, Any?>, doc: Document, mode: Mode): Plan {
        val changes = ArrayList<Change>()
        val rejected = LinkedHashMap<String, String>()
        val unknown = ArrayList<String>()
        var same = 0
        val inDoc = HashSet<String>()
        for ((key, raw) in doc.values) {
            val s = Settings.byKey(key)
            if (s == null) { unknown += key; continue }
            if (!backupable(s)) { rejected[key] = if (s in Settings.transient) "一次性令牌，不恢复" else "凭据不随配置恢复"; continue }
            val value = when (val r = SettingWrite.checkOne(s, raw)) {
                is SettingWrite.Result.Ok -> r.value
                is SettingWrite.Result.Bad -> { rejected[key] = r.reason; continue }
            }
            inDoc += key
            val before = stored[key]
            when {
                before == value -> same++
                before == null -> changes += Change(s, Kind.ADD, null, value)
                else -> changes += Change(s, Kind.CHANGE, before, value)
            }
        }
        if (mode == Mode.REPLACE) {
            for (s in Settings.all) {
                if (!backupable(s) || s.key in inDoc) continue
                val before = stored[s.key] ?: continue
                changes += Change(s, Kind.RESET, before, null)
            }
        }
        return Plan(changes, rejected, unknown, same)
    }

    /** 界面上显示一个值（null = 默认值）。 */
    fun describe(s: Setting<*>, value: Any?): String {
        if (value == null) return "默认（${describe(s, s.default)}）"
        return when (s) {
            is BoolSetting -> if (value == true) "开" else "关"
            is ChoiceSetting -> s.label(value.toString())
            is IntSetting -> "$value${s.unit}"
            is FloatSetting -> value.toString()
            is StringSetSetting -> (value as? Set<*>)?.let { set ->
                if (set.isEmpty()) "空" else {
                    val labels = set.map { id -> s.choices.firstOrNull { it.id == id }?.label ?: id.toString() }.sorted()
                    labels.take(3).joinToString("、") + if (labels.size > 3) " 等 ${labels.size} 项" else ""
                }
            } ?: value.toString()
            is StringSetting -> value.toString().ifEmpty { "空" }.let { if (it.length > 40) it.take(40) + "…" else it }
        }
    }
}
