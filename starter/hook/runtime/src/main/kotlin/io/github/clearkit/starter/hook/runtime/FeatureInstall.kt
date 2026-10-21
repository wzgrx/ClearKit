package io.github.clearkit.starter.hook.runtime

import io.github.clearkit.starter.model.bridge.FeatureStatus.State
import io.github.wzgrx.dexadapt.runtime.SymbolUnavailable

/**
 * 一个功能的安装状态机（不碰框架，可单测）。
 *
 * - 已装好的不再装；
 * - 安装抛异常：撤销 install 期间登记的一切（[InstallRecorder]：钩子、页面订阅、设置监听），状态 FAILED；
 *   这一版本来就没有的（[NotInThisHost]、表里标了 absent 的符号）是 UNAVAILABLE；
 * - FAILED 的功能只在符号表换新（热适配，[Trigger.SYMBOLS]）后重试，普通的设置变化不重试——
 *   否则每改一次设置就重装一次，失败前挂上的钩子越叠越多，之后一旦装成功同一调用会执行多遍。
 */
internal class FeatureInstall {
    /** 什么事引起的这次尝试。 */
    enum class Trigger { START, SETTINGS, SYMBOLS }

    /** 安装前的条件（按顺序检查，前面不满足就不看后面）。 */
    interface Conditions {
        /** 安全模式下暂停（功能不保留在安全模式）。 */
        val pausedBySafeMode: Boolean
        val on: Boolean
        /** 上次崩溃被自动停用的原因，没有为 null。 */
        fun crashBlocked(): String?
        /** 缺的符号，没有为 null。 */
        fun missing(): String?

        /** [missing] 缺的都是“这个版本 / 渠道本来就没有”的：算正常的“不支持”，不提示用户。 */
        fun missingIsExpected(): Boolean = false
    }

    @Volatile
    var installed = false
        private set

    @Volatile
    var state = State.OFF
        private set

    @Volatile
    var detail: String? = null
        private set

    /** UNAVAILABLE 是因为这个版本本来就没有（不是适配失败）：不算问题，不提示。 */
    @Volatile
    var expected = false
        private set

    /** 装好时它想用、却没找到的可选符号（[MissRecorder]）。 */
    @Volatile
    var misses: Set<String> = emptySet()
        private set

    /** 装好时登记的撤销步骤，[uninstall] 用。 */
    private var undo: List<() -> Unit> = emptyList()

    /** 撤下（换表后重装用）：撤掉钩子、订阅、监听，回到没装的状态。 */
    @Synchronized
    fun uninstall() {
        if (!installed) return
        InstallRecorder.rollback(undo)
        undo = emptyList()
        misses = emptySet()
        installed = false
        set(State.OFF, null)
    }

    /** 尝试安装；返回这次是否真的装上了。[install] 抛出的异常已在这里处理（[onError] 记日志）。 */
    @Synchronized
    fun attempt(trigger: Trigger, conditions: Conditions, install: () -> Unit, onError: (Throwable) -> Unit = {}): Boolean {
        if (installed) return false
        if (state == State.FAILED && trigger != Trigger.SYMBOLS) return false
        if (conditions.pausedBySafeMode) return set(State.SAFE_MODE_OFF, "安全模式下暂停")
        if (!conditions.on) return set(State.OFF, null)
        conditions.crashBlocked()?.let { return set(State.CRASH_DISABLED, "上次导致【宿主】崩溃（$it）") }
        conditions.missing()?.let { return set(State.UNAVAILABLE, it, expected = conditions.missingIsExpected()) }
        val undo = ArrayList<() -> Unit>()
        val missed = HashSet<String>()
        return try {
            InstallRecorder.recording(undo) { MissRecorder.recording(missed) { install() } }
            installed = true
            this.undo = undo
            misses = missed
            set(State.ACTIVE, null)
            true
        } catch (t: NotInThisHost) {
            // 这一版【宿主】本来就没有这块：算“不支持”，不算失败
            InstallRecorder.rollback(undo)
            set(State.UNAVAILABLE, t.message, expected = true)
        } catch (t: SymbolUnavailable) {
            // 安装中途用到的符号：表里标了“这个版本没有”（DexAdapt 0.2 的 absent）同样算不支持；没适配上的照旧算失败
            InstallRecorder.rollback(undo)
            if (t.absent) return set(State.UNAVAILABLE, t.message, expected = true)
            set(State.FAILED, "${t.javaClass.simpleName}: ${t.message}")
            onError(t)
            false
        } catch (t: Throwable) {
            InstallRecorder.rollback(undo)
            set(State.FAILED, "${t.javaClass.simpleName}: ${t.message}")
            onError(t)
            false
        }
    }

    private fun set(state: State, detail: String?, expected: Boolean = false): Boolean {
        this.state = state
        this.detail = detail
        this.expected = expected
        return false
    }
}
