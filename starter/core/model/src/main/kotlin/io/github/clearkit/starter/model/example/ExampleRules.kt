package io.github.clearkit.starter.model.example

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * 示例 JSON 规则的逻辑（纯函数，不依赖 Android，能单测）。示例接口长这样：
 *
 * ```json
 * {"code":0,"data":{"items":[{"id":1,"title":"正常内容"},{"id":2,"title":"推广","is_ad":true}]}}
 * ```
 *
 * 换成真实宿主时：
 * 1. 打开“保存详细日志”，在 LSPosed 日志里找 `net fastjson …` 这类行，看接口返回的 JSON 长什么样；
 * 2. [MARKERS] 换成这份 JSON 里独有的字段名（带引号），用来快速跳过别的接口；
 * 3. [removeAds] 换成真实的判断（字段名照抄 JSON）；
 * 4. 改 ExampleRulesTest 里的样例 JSON，跑测试。
 */
object ExampleRules {
    /** 文本里必须都出现的片段：不含这些的 JSON 直接跳过，不解析。 */
    val MARKERS = listOf("\"items\"", "\"is_ad\"")

    /** 去掉 `items` 里 `is_ad` 为 true 的条目；没有可去的返回 null（表示“没改”）。 */
    fun removeAds(data: JsonObject): JsonObject? {
        val items = data["items"] as? JsonArray ?: return null
        val kept = items.filterNot { isAd(it as? JsonObject) }
        if (kept.size == items.size) return null
        // 报给首页“这周帮你挡下 N 条”；按 id 去重，同一条刷新几次只算一次
        items.filter { isAd(it as? JsonObject) }.forEach { io.github.clearkit.starter.model.Impact.hidden(io.github.clearkit.starter.model.Impact.CARD, id = (it as JsonObject)["id"]?.toString()) }
        return JsonObject(data + ("items" to JsonArray(kept)))
    }

    /** 顶部入口那份 JSON 里独有的字段名。 */
    val ENTRY_MARKERS = listOf("\"entries\"")

    /**
     * 去掉 `entries` 里 id 在 [hidden] 里的入口（多选设置“隐藏的入口”，选上 = 隐藏）；没有可去的返回 null。示例接口：
     * `{"code":0,"data":{"entries":[{"id":"shop","title":"商城"},{"id":"game","title":"游戏中心"}]}}`
     */
    fun removeEntries(data: JsonObject, hidden: Set<String>): JsonObject? {
        if (hidden.isEmpty()) return null
        val entries = data["entries"] as? JsonArray ?: return null
        val kept = entries.filterNot { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.content in hidden }
        if (kept.size == entries.size) return null
        return JsonObject(data + ("entries" to JsonArray(kept)))
    }

    private fun isAd(item: JsonObject?): Boolean = (item?.get("is_ad") as? JsonPrimitive)?.booleanOrNull == true
}
