package io.github.clearkit.starter.hook.runtime

import android.app.Activity
import android.app.Application
import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.SettingsReader
import io.github.clearkit.starter.model.settings.StringSetSetting
import io.github.wzgrx.dexadapt.Symbol

/**
 * One host-side feature. A feature lives in its own package under
 * `io.github.clearkit.starter.hook.features.<id>` so crash frames can be traced back to it.
 */
abstract class Feature(val id: String, val title: String) {
    /** The feature installs when any of these is on (and stays installed; hooks check [isOn]). */
    abstract val switches: List<BoolSetting>

    /** Non-boolean settings that also turn the feature on when not at their default (e.g. a hidden-items set). */
    open val listSwitches: List<StringSetSetting> = emptyList()

    /** Symbols the feature cannot work without. Any missing one makes it UNAVAILABLE. */
    open val requires: List<Symbol> = emptyList()

    /** Whether the feature still runs in safe mode (ad and splash cleanup do). */
    open val keepInSafeMode: Boolean = false

    /** Installs hooks. Called once, on the main thread of the host, before Application.onCreate. */
    abstract fun FeatureScope.install()

    /**
     * Whether the feature should run. Defaults to "any of [switches] is on"; features controlled by
     * a choice or numeric setting (e.g. 搜索自动播放 开/仅 Wi-Fi/关) override it.
     */
    open fun isOn(settings: SettingsReader): Boolean = switches.any { settings[it] } || listSwitches.any { settings[it].isNotEmpty() }
}

/**
 * 功能安装时发现这一版【宿主】没有它要的东西（类不存在、某个客户端没有这个功能）：抛它，状态记成“不支持”而不是“失败”。
 * 只用在“确定这一版没有”的地方；找到了但对不上号的照常抛别的异常。
 */
class NotInThisHost(reason: String) : RuntimeException(reason) {
    override fun fillInStackTrace(): Throwable = this
}

/** 读【宿主】的类；这一版没有时抛 [NotInThisHost]。 */
fun FeatureScope.hostClass(name: String): Class<*> =
    runCatching { hostClassLoader.loadClass(name) }.getOrElse { throw NotInThisHost("这一版【宿主】没有 ${name.substringAfterLast('.')}") }

/** Thrown by feature code to say "not this call" without counting as a failure. */
class SkipHook : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

/** What a feature gets while installing. */
interface FeatureScope {
    val feature: Feature
    val settings: HostSettings
    val symbols: HostSymbols
    val hostClassLoader: ClassLoader

    /** True while the feature's switches are on and the circuit is closed. */
    val active: Boolean

    /**
     * Intercepts [target]. When the feature is inactive the original runs untouched. If [body]
     * throws, the failure is recorded and the original result is used (calling the original if
     * [body] had not yet done so); host exceptions from [HookCall.proceed] pass through unchanged.
     */
    fun hook(target: java.lang.reflect.Executable, body: (HookCall) -> Any?)

    /**
     * Like [hook], but [body] also runs while the feature's switches are off (never while the
     * circuit is open). For hooks that must stay consistent across a toggle, e.g. a list filter
     * whose host diffs consecutive results; [body] decides itself whether to act.
     */
    fun hookAlways(target: java.lang.reflect.Executable, body: (HookCall) -> Any?)

    /**
     * 让 ART 不再内联、优化 [target]（libxposed 的 deoptimize）。很短的方法常被内联进调用它的地方，
     * 钩子挂上了却从来不触发时，对它（或调用它的方法）调一次。有性能开销，只在确认被内联时用。返回是否成功。
     * 默认不做（测试里的假 scope 不用实现）。
     */
    fun deoptimize(target: java.lang.reflect.Executable): Boolean = false

    /** Runs once the host Application has been created (immediately if it already was). */
    fun onApplication(block: (Application) -> Unit)

    /**
     * 页面生命周期（[HostActivities]，主线程）：[block] 抛出的异常记为本功能的失败。
     * 在 install 里订阅；安装失败时自动撤销。每个页面一份的状态用 [PerActivity]，销毁时自动清理。
     */
    fun onActivityCreated(block: (Activity) -> Unit)

    fun onActivityResumed(block: (Activity) -> Unit)

    /** 页面进入后台（被别的页面盖住、切到别的应用）。默认不订阅（测试里的假 scope 不用实现）。 */
    fun onActivityPaused(block: (Activity) -> Unit) {}

    fun onActivityDestroyed(block: (Activity) -> Unit)

    /** Records a failure outside a hook body (posted runnables, listeners). */
    fun fail(where: String, error: Throwable)
}

/** A single intercepted call. */
interface HookCall {
    val thisObject: Any?
    val args: List<Any?>
    fun arg(index: Int): Any? = args.getOrNull(index)

    /** Calls the original with the current arguments. */
    fun proceed(): Any?

    /** Calls the original with replaced arguments. */
    fun proceed(vararg newArgs: Any?): Any?
}
