package io.github.clearkit.starter.model.example

import io.github.clearkit.starter.model.net.JsonRewrite
import io.github.clearkit.starter.model.net.JsonRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 示例规则的测试：写真实规则时照这个样子，用抓到的 JSON（去掉个人信息）做样例。 */
class ExampleRulesTest {
    private val rule = JsonRule("example.feedAds", markers = ExampleRules.MARKERS) { ExampleRules.removeAds(it) }

    @Test
    fun removesAdItems() {
        val text = """{"code":0,"data":{"items":[{"id":1,"title":"正常内容"},{"id":2,"title":"推广","is_ad":true}]}}"""
        assertEquals(
            """{"code":0,"data":{"items":[{"id":1,"title":"正常内容"}]}}""",
            JsonRewrite.rewrite(text, listOf(rule).filter { it.matches(text) }),
        )
    }

    @Test
    fun leavesCleanResponseAlone() {
        val text = """{"code":0,"data":{"items":[{"id":1,"title":"正常内容","is_ad":false}]}}"""
        assertNull(JsonRewrite.rewrite(text, listOf(rule)))
    }

    @Test
    fun skipsOtherApis() {
        assertEquals(false, rule.matches("""{"code":0,"data":{"user":{"name":"x"}}}"""))
    }

    @Test
    fun hidesChosenEntries() {
        val data = kotlinx.serialization.json.Json.parseToJsonElement("""{"entries":[{"id":"shop","title":"商城"},{"id":"game","title":"游戏中心"}]}""") as kotlinx.serialization.json.JsonObject
        assertEquals("""{"entries":[{"id":"game","title":"游戏中心"}]}""", ExampleRules.removeEntries(data, setOf("shop")).toString())
        assertNull(ExampleRules.removeEntries(data, emptySet()))
        assertNull(ExampleRules.removeEntries(data, setOf("live")))
    }
}
