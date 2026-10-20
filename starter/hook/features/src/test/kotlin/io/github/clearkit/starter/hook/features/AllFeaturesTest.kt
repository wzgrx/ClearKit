package io.github.clearkit.starter.hook.features

import io.github.clearkit.starter.model.settings.FeatureCatalog
import io.github.clearkit.starter.model.settings.SettingPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 防漏检查（常见错误：写了功能忘了登记、加了设置没有代码读）。失败信息直接说该怎么改。
 * 读的是源码文件（hook/features/src/main/kotlin），所以不用真的启动【宿主】。
 */
class AllFeaturesTest {
    private val src = File("src/main/kotlin")
    private val sources: List<File> = src.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun everyFeatureObjectIsRegistered() {
        // 源码里每个 `object Xxx : Feature(...)` / `: NetRuleFeature(...)` 都要在 AllFeatures 里
        val declared = sources.flatMap { f ->
            Regex("""object (\w+)\s*:\s*(Feature|NetRuleFeature)\(""").findAll(f.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        val registered = AllFeatures.list.map { it.javaClass.simpleName }.toSet()
        val missing = declared - registered
        assertTrue("这些功能写了但没在 AllFeatures 里登记，不会运行：$missing（改接口数据的加进 netRules，其它加进 list）", missing.isEmpty())
        val ids = AllFeatures.list.map { it.id }
        assertEquals("功能 id 重复：${ids.groupBy { it }.filterValues { it.size > 1 }.keys}", ids.size, ids.toSet().size)
    }

    @Test
    fun everyFeatureSettingIsReadByCode() {
        // 功能页里的每个设置都要有代码读它，否则就是“空开关”（用户打开了什么也不发生）
        val code = sources.joinToString("\n") { it.readText() }
        val unused = FeatureCatalog.pages.flatMap { it.group.all }
            .filter { it.key !in SettingPages.INTERNAL }
            .filter { s -> !Regex("""\.${Regex.escape(s.key.substringAfter('.'))}\b""").containsMatchIn(code) }
            .map { "${it.key}（${it.title}）" }
        assertTrue("这些设置没有功能代码读它（hook/features 里搜不到）：$unused。写功能读它，或者删掉这个设置", unused.isEmpty())
    }
}
