package io.github.clearkit.starter.model.preview

import io.github.clearkit.starter.model.settings.SettingPages
import io.github.clearkit.starter.model.settings.Settings
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 实物预览覆盖（6.2）：用户能改的每一项设置（开关、单选、多选、数值、文字、名单）都要出现在示意图里，
 * 改了以后在示意图里看得到。只有内部设置（[SettingPages.INTERNAL]）和标着“即将支持”的不算。
 * 新加设置时这里会失败：把它画进对应页面的示意图。
 */
class PreviewCoverageTest {
    @Test
    fun everySettingIsInADiagram() {
        val drawn = PagePreviews.ALL.flatMap { p -> p.binds.flatMap { listOfNotNull(it.setting.key, it.requires?.key) } }.toSet()
        val missing = Settings.all
            .filter { it.key !in SettingPages.INTERNAL && !it.comingSoon && it.key !in drawn }
            .map { "${it.key}（${it.title}）→ ${SettingPages.routeOf(it.key)}" }
        assertTrue("这些设置没画进示意图：\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun valuesNeverShowSecrets() {
        // 凭据只显示“已设置 / 未设置”，网址只显示主机（真实设置里有这两类时，示意图照样不会露出来）
        val key = io.github.clearkit.starter.model.settings.StringSetting("test.key", "", "访问密钥", format = io.github.clearkit.starter.model.settings.TextFormat.SECRET)
        val text = PagePreviews.valueText(key, "abcdef123456", null, PagePreviews.Fmt.AUTO)
        assertTrue(text, !text.contains("abc"))
        val server = io.github.clearkit.starter.model.settings.StringSetting("test.url", "", "服务器", format = io.github.clearkit.starter.model.settings.TextFormat.URL)
        val url = PagePreviews.valueText(server, "http://token:secret@192.168.1.5:6800/jsonrpc", null, PagePreviews.Fmt.AUTO)
        assertTrue(url, !url.contains("secret") && url.contains("192.168.1.5"))
    }
}
