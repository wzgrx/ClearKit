package io.github.clearkit.starter.model.adapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptReportTest {
    private val ok = AdaptReport("9.16.0", found = 99, total = 99, active = 34, local = true)

    @Test
    fun tierPutsUnavailableFirstThenRestart() {
        assertEquals(AdaptReport.Tier.OK, ok.tier)
        assertEquals(AdaptReport.Tier.RESTART, ok.copy(restart = listOf("开屏广告")).tier)
        assertEquals(AdaptReport.Tier.UNAVAILABLE, ok.copy(restart = listOf("开屏广告"), unavailable = listOf(AdaptReport.Issue("首页推荐过滤", "找不到"))).tier)
    }

    @Test
    fun cardTextsNameTheProblem() {
        assertEquals("已适配【宿主】 9.16.0", AdaptCardText.resultTitle(ok))
        assertEquals("全部找到，34 项已生效", AdaptCardText.resultSubtitle(ok))
        val restart = ok.copy(restart = listOf("接口去广告", "开屏广告", "底栏与首页顶栏", "配置覆盖", "自选主题色", "界面缩放"))
        assertEquals("34 项已生效，重开一次后全部生效", AdaptCardText.resultSubtitle(restart))
        assertEquals("接口去广告 · 开屏广告 · 底栏与首页顶栏 · 配置覆盖 · 自选主题色 等", AdaptCardText.restartList(restart))
        val bad = ok.copy(unavailable = listOf(AdaptReport.Issue("首页推荐过滤", "找不到")))
        assertEquals("已适配 9.16.0 · 1 项用不了", AdaptCardText.resultTitle(bad))
        assertEquals("找到 99 / 99 处 · 本机适配", AdaptCardText.foundLine(ok))
    }

    @Test
    fun loadedLineAnnouncesOnceAndKeepsWarning() {
        assertEquals("已适配 9.16.0 · 34 项生效 · 全部正常" to false, AdaptCardText.loadedLine(ok, firstForVersion = true))
        assertEquals("34 项生效" to false, AdaptCardText.loadedLine(ok, firstForVersion = false))
        val (text, warn) = AdaptCardText.loadedLine(ok.copy(unavailable = listOf(AdaptReport.Issue("a", "b"))), firstForVersion = false)
        assertEquals("34 项生效 · 1 项这一版用不了", text)
        assertTrue(warn)
        assertFalse(AdaptCardText.loadedLine(ok, true).second)
    }
}
