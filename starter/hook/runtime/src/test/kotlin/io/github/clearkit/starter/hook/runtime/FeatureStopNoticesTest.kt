package io.github.clearkit.starter.hook.runtime

import io.github.clearkit.starter.hook.runtime.FeatureStopNotices.Notice
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureStopNoticesTest {
    @Test
    fun `one stopped feature names it and the reason`() {
        assertEquals(
            "【模块】：评论导出 已暂停（连续出错）" to "可在设置里重新启用",
            FeatureStopNotices.text(listOf(Notice("评论导出", "连续出错"))),
        )
    }

    @Test
    fun `several stopped features are merged into one notice`() {
        val n = listOf(Notice("A", "连续出错"), Notice("B", "上次导致【宿主】崩溃"), Notice("C", "连续出错"), Notice("D", "连续出错"))
        assertEquals(
            "【模块】：4 个功能已暂停（连续出错、上次导致【宿主】崩溃）" to "A、B、C 等 · 可在设置里重新启用",
            FeatureStopNotices.text(n),
        )
    }
}
