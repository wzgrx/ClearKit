package io.github.clearkit.starter.model.preview

import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.ChoiceSetting
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.model.settings.StringSetSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePreviewsTest {
    @Test
    fun everyBindPointsAtARealSettingAndOption() {
        PagePreviews.ALL.forEach { page ->
            page.allBlocks.filter { it.bind != null }.forEach { block ->
                val b = block.bind!!
                assertTrue("${page.route} ${b.setting.key}", Settings.byKey(b.setting.key) != null)
                // 数值块写当前值，什么设置都能绑，不带选项
                if (block.effect == PagePreviews.Effect.VALUE) { assertEquals(null, b.option); return@forEach }
                when (val s = b.setting) {
                    is BoolSetting -> assertEquals(null, b.option)
                    is StringSetSetting -> assertTrue("${s.key}#${b.option}", b.option != null && (s.choices.isEmpty() || s.choices.any { it.id == b.option }))
                    is ChoiceSetting -> assertTrue("${s.key}#${b.option}", s.options.any { it.id == b.option })
                    else -> error("只能绑开关和多选：${s.key}")
                }
            }
            // 同一页里一个绑定只出现一次，免得点一处两处一起变还看不出为什么
            val ids = page.binds.map { it.setting.key + "#" + it.option }
            assertEquals(page.route, ids.size, ids.toSet().size)
        }
        // 一个页面只有一张示意图
        assertEquals(PagePreviews.ALL.size, PagePreviews.ALL.map { it.route }.toSet().size)
    }

    @Test
    fun toggleFlipsSwitchesAndOptions() {
        val c = Settings.Cleanup
        val values = mapOf<String, Any?>(c.exampleHide.key to setOf("shop"), c.exampleAds.key to false)
        val (s, v) = PagePreviews.toggle(PagePreviews.Bind(c.exampleHide, "game"), values).single()
        assertEquals(c.exampleHide, s)
        assertEquals(setOf("shop", "game"), v)
        assertFalse(PagePreviews.active(PagePreviews.Bind(c.exampleAds), values))
        assertEquals(listOf(c.exampleAds to true), PagePreviews.toggle(PagePreviews.Bind(c.exampleAds), values))
    }

    @Test
    fun requiresTurnsTheMasterSwitchOnTogether() {
        // 依赖总开关的多选：总开关关着时选上了也不算生效，点一下把总开关一起打开
        val c = Settings.Cleanup
        val bind = PagePreviews.Bind(c.exampleHide, "live", c.exampleAds)
        val off = mapOf<String, Any?>(c.exampleAds.key to false, c.exampleHide.key to setOf("live"))
        assertFalse(PagePreviews.active(bind, off))
        assertEquals(listOf(c.exampleAds to true), PagePreviews.toggle(bind, off))
        assertEquals(listOf(c.exampleAds to true, c.exampleHide to setOf("live")), PagePreviews.toggle(bind, mapOf<String, Any?>(c.exampleAds.key to false)))
        assertEquals(listOf(c.exampleHide to emptySet<String>()), PagePreviews.toggle(bind, off + (c.exampleAds.key to true)))
    }

    @Test
    fun choiceBindSelectsItsOption() {
        val a = Settings.Appearance
        val dark = PagePreviews.Bind(a.themeMode, "dark")
        assertFalse(PagePreviews.active(dark, emptyMap()))
        assertEquals(listOf(a.themeMode to "dark"), PagePreviews.toggle(dark, emptyMap()))
        // 已经选上的再点回到默认；默认那一项再点不变
        assertEquals(listOf(a.themeMode to "system"), PagePreviews.toggle(dark, mapOf(a.themeMode.key to "dark")))
        assertEquals(emptyList<Any>(), PagePreviews.toggle(PagePreviews.Bind(a.themeMode, "system"), emptyMap()))
    }

    @Test
    fun countOnlyCountsChangesFromDefaults() {
        val page = PagePreviews.byRoute("cleanup")!!
        // 默认：去示例广告开着，不算改了
        assertEquals(0, PagePreviews.count(page, emptyMap()).first)
        assertEquals(2, PagePreviews.count(page, mapOf(Settings.Cleanup.exampleAds.key to false, Settings.Cleanup.exampleHide.key to setOf("shop"))).first)
    }

    @Test
    fun everyFeaturePageHasADiagram() {
        // 功能目录里的每一页都要有同名的示意图（实物预览设计：一页一图）
        io.github.clearkit.starter.model.settings.FeatureCatalog.pages.forEach { p ->
            assertTrue("功能页 ${p.route} 没有示意图：在 PagePreviews.PAGES 里加一页", PagePreviews.byRoute(p.route) != null)
        }
    }
}
