package io.github.clearkit.starter.model.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 接口返回数据的改写规则：【宿主】解析一份 JSON（fastjson、kotlinx，个别 Gson）之前，按 JSON 内容找到规则，
 * 改 JSON 文本再交回去。规则按接口的字段名写（跨版本、跨渠道都稳定），不依赖模型类名（模型类名常被混淆）。
 *
 * [markers]：文本里必须都出现的片段（一般是带引号的字段名，如 `"splash_request_id"`），用来快速放过无关的 JSON；
 * 命中以后 [edit] 还要自己核对结构。
 * [models]：额外按模型名触发（只用于 Gson 从 Reader 读、看不到文本的场合，例如开屏的本地缓存）。
 * [edit]：改 `data`（外层是 `{code, data}` 包装时）或整个文档，没有改动返回 null。
 */
class JsonRule(
    val id: String,
    val markers: List<String>,
    val models: Set<String> = emptySet(),
    /** `data` 是数组时（例如搜索页的区块列表）用它改；不处理数组的规则留空。 */
    val editList: ((JsonArray) -> JsonArray?)? = null,
    /** 只在解析目标类型（含泛型参数）里有其中一个类名时触发；空 = 不看类型。看不到类型的解析路径不触发这种规则。 */
    val types: Set<String> = emptySet(),
    val edit: (JsonObject) -> JsonObject? = { null },
) {
    /** 文本里是否出现了全部特征片段（有 [types] 时还要目标类型对上）。 */
    fun matches(text: String, typeNames: Set<String>? = null): Boolean =
        (types.isEmpty() || typeNames != null && types.any { it in typeNames }) && markers.all { text.contains(it) }
}

object JsonRewrite {
    /**
     * 按 [rules] 改写 [text]；全部规则都没改动、或不是 JSON 对象时返回 null（调用方照原文解析）。
     * 每条规则抛出的异常交给 [onError]（记到那条规则所属的功能上），其余规则照常执行。
     */
    fun rewrite(
        text: String,
        rules: List<JsonRule>,
        onError: (JsonRule, Throwable) -> Unit = { _, _ -> },
        onChanged: (JsonRule) -> Unit = {},
    ): String? {
        if (rules.isEmpty()) return null
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        (root["data"] as? JsonArray)?.takeIf { "code" in root }?.let { list -> return rewriteList(root, list, rules, onError, onChanged) }
        val wrapped = "code" in root && root["data"] is JsonObject
        var data = if (wrapped) root["data"] as JsonObject else root
        var changed = false
        for (rule in rules) {
            try {
                rule.edit(data)?.let { data = it; changed = true; onChanged(rule) }
            } catch (t: Throwable) {
                onError(rule, t)
            }
        }
        if (!changed) return null
        return (if (wrapped) JsonObject(root + ("data" to data)) else data).toString()
    }

    private fun rewriteList(
        root: JsonObject,
        list: JsonArray,
        rules: List<JsonRule>,
        onError: (JsonRule, Throwable) -> Unit,
        onChanged: (JsonRule) -> Unit,
    ): String? {
        var data = list
        var changed = false
        for (rule in rules) {
            val edit = rule.editList ?: continue
            try {
                edit(data)?.let { data = it; changed = true; onChanged(rule) }
            } catch (t: Throwable) {
                onError(rule, t)
            }
        }
        return if (changed) JsonObject(root + ("data" to data)).toString() else null
    }

    /** 把 [keys] 里非空的数组换成空数组；都已经是空的（或没有）时返回 null。 */
    fun JsonObject.emptyArrays(vararg keys: String): JsonObject? {
        val hit = keys.filter { k -> (this[k] as? JsonArray)?.isNotEmpty() == true }
        if (hit.isEmpty()) return null
        return JsonObject(this + hit.associateWith { JsonArray(emptyList()) })
    }

    /** 对象里删掉 [keys]（有的才删）；一个都没有时返回 null。 */
    fun JsonObject.without(vararg keys: String): JsonObject? {
        if (keys.none { it in this }) return null
        return JsonObject(filterKeys { it !in keys })
    }

    /** 数组里去掉满足 [drop] 的元素；没有去掉的返回 null。 */
    fun JsonArray.filterOut(drop: (JsonElement) -> Boolean): JsonArray? {
        val kept = filterNot(drop)
        return if (kept.size == size) null else JsonArray(kept)
    }
}
