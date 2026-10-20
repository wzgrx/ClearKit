package io.github.clearkit.starter.app.data

import android.content.Context
import androidx.core.content.edit
import io.github.clearkit.starter.model.settings.SettingPages
import io.github.clearkit.starter.model.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 最近改动（首页“最近改动”）：App 和【宿主】里改的设置都记一条（键、改之前、改之后、时间），最多 [KEEP] 条。
 * 撤销 = 把改之前的值写回去（之前没显式保存的恢复默认）。内部设置（位置、令牌、队列）不记。
 */
class ChangeJournal(context: Context) {
    data class Change(val key: String, val before: Any?, val after: Any?, val at: Long)

    private val prefs = context.getSharedPreferences("change_journal", Context.MODE_PRIVATE)
    private val _changes = MutableStateFlow(load())
    val changes: StateFlow<List<Change>> = _changes

    /** 撤销时写回旧值也会触发记录：撤销中的那一项不记。 */
    @Volatile private var undoing: String? = null

    @Volatile private var pause = 0

    /** [block] 里的写入不记（旧设置迁移、应用方案这种整批写入）。 */
    fun <R> paused(block: () -> R): R {
        synchronized(this) { pause++ }
        try { return block() } finally { synchronized(this) { pause-- } }
    }

    fun record(key: String, before: Any?, after: Any?) {
        if (key == undoing || pause > 0) return
        val base = key.substringAfter("@").let { k -> if (Settings.byKey(k) != null) k else key }
        if (base in SettingPages.INTERNAL || Settings.byKey(base) == null) return
        val now = System.currentTimeMillis()
        val list = _changes.value.toMutableList()
        // 同一项连着改（比如名单逐个加）在 1 分钟内合并成一条，保留最早的“改之前”
        val last = list.firstOrNull()
        if (last != null && last.key == base && now - last.at < 60_000) list[0] = last.copy(after = after, at = now)
        else list.add(0, Change(base, before, after, now))
        val trimmed = list.take(KEEP)
        _changes.value = trimmed
        prefs.edit { putString("list", encode(trimmed)) }
    }

    /** 把这一条改回去，成功后从列表里去掉。 */
    fun undo(change: Change, store: SettingsStore) {
        val s = Settings.byKey(change.key) ?: return
        undoing = change.key
        try {
            val v = change.before
            @Suppress("UNCHECKED_CAST")
            if (v == null) store.reset(s) else store.set(s as io.github.clearkit.starter.model.settings.Setting<Any>, v)
        } finally {
            undoing = null
        }
        val rest = _changes.value.filterNot { it === change }
        _changes.value = rest
        prefs.edit { putString("list", encode(rest)) }
    }

    private fun load(): List<Change> = runCatching {
        val text = prefs.getString("list", null) ?: return emptyList()
        Json.parseToJsonElement(text).jsonArray.map { e ->
            val o = e.jsonObject
            Change(o["k"]!!.jsonPrimitive.content, value(o["b"]), value(o["a"]), o["t"]!!.jsonPrimitive.longOrNull ?: 0)
        }
    }.getOrDefault(emptyList())

    private fun encode(list: List<Change>): String = JsonArray(
        list.map { c -> JsonObject(mapOf("k" to JsonPrimitive(c.key), "b" to element(c.before), "a" to element(c.after), "t" to JsonPrimitive(c.at))) },
    ).toString()

    private fun element(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Boolean -> JsonObject(mapOf("b" to JsonPrimitive(v)))
        is Int -> JsonObject(mapOf("i" to JsonPrimitive(v)))
        is Long -> JsonObject(mapOf("i" to JsonPrimitive(v)))
        is Float -> JsonObject(mapOf("f" to JsonPrimitive(v)))
        is String -> JsonObject(mapOf("s" to JsonPrimitive(v)))
        is Set<*> -> JsonObject(mapOf("set" to JsonArray(v.map { JsonPrimitive(it.toString()) })))
        else -> JsonNull
    }

    private fun value(e: JsonElement?): Any? {
        val o = (e as? JsonObject) ?: return null
        o["b"]?.let { return it.jsonPrimitive.booleanOrNull }
        o["i"]?.let { return it.jsonPrimitive.intOrNull }
        o["f"]?.let { return it.jsonPrimitive.floatOrNull }
        o["s"]?.let { return it.jsonPrimitive.contentOrNull }
        o["set"]?.let { return it.jsonArray.map { x -> x.jsonPrimitive.content }.toSet() }
        return null
    }

    companion object {
        const val KEEP = 40
    }
}
