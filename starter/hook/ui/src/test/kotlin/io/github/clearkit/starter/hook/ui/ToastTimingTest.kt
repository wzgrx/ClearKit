package io.github.clearkit.starter.hook.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ToastTimingTest {
    @Test
    fun `plain toasts keep the requested duration`() {
        assertEquals(4000L, ToastTiming.timeout(4000, hasAction = false, recommendedMs = null))
        assertEquals(2000L, ToastTiming.timeout(2000, hasAction = false, recommendedMs = 2000))
        assertEquals(0L, ToastTiming.timeout(-5, hasAction = false, recommendedMs = null))
    }

    @Test
    fun `toasts with a button stay long enough to tap it`() {
        assertEquals(ToastTiming.MIN_WITH_ACTION_MS, ToastTiming.timeout(4000, hasAction = true, recommendedMs = null))
        assertEquals(8000L, ToastTiming.timeout(8000, hasAction = true, recommendedMs = 8000))
    }

    @Test
    fun `accessibility timeout wins when longer`() {
        assertEquals(30000L, ToastTiming.timeout(4000, hasAction = true, recommendedMs = 30000))
        assertEquals(10000L, ToastTiming.timeout(4000, hasAction = false, recommendedMs = 10000))
    }

    @Test
    fun `releasing a pressed toast leaves at least a moment`() {
        assertEquals(ToastTiming.AFTER_RELEASE_MS, ToastTiming.afterRelease(0))
        assertEquals(5000L, ToastTiming.afterRelease(5000))
    }
}
