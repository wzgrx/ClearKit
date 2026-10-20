package io.github.clearkit.starter.hook.runtime

import io.github.clearkit.starter.model.bridge.FeatureStatus.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureInstallTest {
    private class Conditions(
        override var pausedBySafeMode: Boolean = false,
        override var on: Boolean = true,
        var crash: String? = null,
        var missingSymbol: String? = null,
    ) : FeatureInstall.Conditions {
        override fun crashBlocked() = crash
        override fun missing() = missingSymbol
    }

    /** 模拟一次 install：先登记 [hooks] 个钩子（撤销时计数），[fail] 时随后抛异常。 */
    private class FakeInstall(private val hooks: Int, var fail: Boolean) {
        var live = 0
        var calls = 0
        fun run() {
            calls++
            repeat(hooks) {
                live++
                InstallRecorder.record { live-- }
            }
            if (fail) error("boom")
        }
    }

    @Test
    fun successInstallsOnceAndKeepsHooks() {
        val f = FeatureInstall()
        val fake = FakeInstall(hooks = 2, fail = false)
        assertTrue(f.attempt(FeatureInstall.Trigger.START, Conditions(), fake::run))
        assertTrue(f.installed)
        assertEquals(State.ACTIVE, f.state)
        assertFalse(f.attempt(FeatureInstall.Trigger.SETTINGS, Conditions(), fake::run))
        assertEquals(1, fake.calls)
        assertEquals(2, fake.live)
    }

    @Test
    fun failureRollsBackEverythingRegisteredDuringInstall() {
        val f = FeatureInstall()
        val fake = FakeInstall(hooks = 3, fail = true)
        var reported: Throwable? = null
        assertFalse(f.attempt(FeatureInstall.Trigger.START, Conditions(), fake::run) { reported = it })
        assertEquals(0, fake.live)
        assertEquals(State.FAILED, f.state)
        assertEquals("IllegalStateException: boom", f.detail)
        assertEquals("boom", reported?.message)
        assertFalse(f.installed)
    }

    @Test
    fun failedFeatureIsNotRetriedOnSettingsChangesOnlyAfterNewSymbols() {
        val f = FeatureInstall()
        val fake = FakeInstall(hooks = 2, fail = true)
        f.attempt(FeatureInstall.Trigger.START, Conditions(), fake::run)
        repeat(3) { f.attempt(FeatureInstall.Trigger.SETTINGS, Conditions(), fake::run) }
        assertEquals(1, fake.calls)
        fake.fail = false
        assertTrue(f.attempt(FeatureInstall.Trigger.SYMBOLS, Conditions(), fake::run))
        assertEquals(2, fake.calls)
        // 只剩成功那次的钩子，没有失败那次留下的。
        assertEquals(2, fake.live)
    }

    @Test
    fun recordingOutsideInstallIsIgnored() {
        var undone = false
        InstallRecorder.record { undone = true }
        val f = FeatureInstall()
        f.attempt(FeatureInstall.Trigger.START, Conditions(), { error("x") })
        assertFalse(undone)
    }

    @Test
    fun conditionsAreCheckedInOrder() {
        val f = FeatureInstall()
        val fake = FakeInstall(hooks = 1, fail = false)
        val c = Conditions(pausedBySafeMode = true, on = false, crash = "NPE", missingSymbol = "a.b: missing")
        f.attempt(FeatureInstall.Trigger.START, c, fake::run)
        assertEquals(State.SAFE_MODE_OFF, f.state)
        c.pausedBySafeMode = false
        f.attempt(FeatureInstall.Trigger.SETTINGS, c, fake::run)
        assertEquals(State.OFF, f.state)
        c.on = true
        f.attempt(FeatureInstall.Trigger.SETTINGS, c, fake::run)
        assertEquals(State.CRASH_DISABLED, f.state)
        assertEquals("上次导致【宿主】崩溃（NPE）", f.detail)
        c.crash = null
        f.attempt(FeatureInstall.Trigger.SETTINGS, c, fake::run)
        assertEquals(State.UNAVAILABLE, f.state)
        assertEquals(0, fake.calls)
        c.missingSymbol = null
        assertTrue(f.attempt(FeatureInstall.Trigger.SETTINGS, c, fake::run))
        assertEquals(State.ACTIVE, f.state)
    }

    @Test
    fun rollbackRunsInReverseAndSurvivesFailingSteps() {
        val order = ArrayList<Int>()
        InstallRecorder.rollback(listOf({ order += 1 }, { error("step") }, { order += 3 }))
        assertEquals(listOf(3, 1), order)
    }

    @Test
    fun notInThisHostIsUnavailableNotFailed() {
        val f = FeatureInstall()
        var live = 0
        var errors = 0
        assertFalse(f.attempt(FeatureInstall.Trigger.START, Conditions(), {
            live++
            InstallRecorder.record { live-- }
            throw NotInThisHost("这一版【宿主】没有 X")
        }) { errors++ })
        assertEquals(State.UNAVAILABLE, f.state)
        assertEquals("这一版【宿主】没有 X", f.detail)
        assertEquals(0, live)
        assertEquals(0, errors)
    }

    @Test
    fun absentSymbolIsUnavailableButUnadaptedFails() {
        val absent = FeatureInstall()
        assertFalse(absent.attempt(FeatureInstall.Trigger.START, Conditions(), {
            throw io.github.wzgrx.dexadapt.runtime.SymbolUnavailable("x", "这个版本没有", absent = true)
        }))
        assertEquals(State.UNAVAILABLE, absent.state)
        val unadapted = FeatureInstall()
        var errors = 0
        assertFalse(unadapted.attempt(FeatureInstall.Trigger.START, Conditions(), {
            throw io.github.wzgrx.dexadapt.runtime.SymbolUnavailable("x", "未找到")
        }) { errors++ })
        assertEquals(State.FAILED, unadapted.state)
        assertEquals(1, errors)
    }
}
