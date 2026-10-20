package io.github.clearkit.starter.hook.runtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 【宿主】进程里的页面生命周期：Application 创建后只注册一次回调（[FeatureRuntime] 负责），
 * 功能按需订阅创建 / 前台 / 后台 / 销毁，取“当前在前台的页面”也走这里，不再各自注册、各自记一份。
 *
 * 订阅在功能 `install()` 期间登记到 [InstallRecorder]，安装失败时随之撤销；订阅者抛出的异常
 * 只记日志，不影响别的订阅者（功能里用 [FeatureScope.onActivityResumed] 等，异常会记到功能头上）。
 * 回调都在主线程。
 */
object HostActivities {
    private val created = CopyOnWriteArrayList<(Activity) -> Unit>()
    private val resumedListeners = CopyOnWriteArrayList<(Activity) -> Unit>()
    private val pausedListeners = CopyOnWriteArrayList<(Activity) -> Unit>()
    private val destroyed = CopyOnWriteArrayList<(Activity) -> Unit>()
    private val perActivity = CopyOnWriteArrayList<PerActivity<*>>()

    @Volatile
    private var registered = false

    @Volatile
    private var last: WeakReference<Activity>? = null

    /** 现在处于前台（resumed 之后、paused 之前）的页面。 */
    @Volatile
    private var front: WeakReference<Activity>? = null

    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = dispatchCreated(activity)
        override fun onActivityResumed(activity: Activity) = dispatchResumed(activity)
        override fun onActivityDestroyed(activity: Activity) = dispatchDestroyed(activity)
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {
            if (front?.get() === activity) front = null
            notify(pausedListeners, activity, "paused")
        }
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }

    /** 由运行时在 Application.onCreate 之后调用一次。 */
    fun install(app: Application) {
        synchronized(this) {
            if (registered) return
            registered = true
        }
        app.registerActivityLifecycleCallbacks(callbacks)
    }

    /** 最近进入前台、还没销毁的页面（可能正在 finish，需要时调用方自己判断）。 */
    fun resumed(): Activity? = last?.get()?.takeIf { !it.isDestroyed }

    /**
     * [view] 所在的页面：先看它挂在哪个页面的窗口上，再退回它的 Context。宿主可能用首页的 Context 预先
     * 创建别的页面的视图（异步预加载），只看 Context 会把那些视图认成首页的。
     */
    fun of(view: android.view.View): Activity? {
        val root = view.rootView
        val byContext = unwrap(view.context)
        if (byContext?.window?.decorView === root) return byContext
        for (candidate in listOfNotNull(foreground(), resumed())) {
            if (candidate.window?.decorView === root) return candidate
        }
        return byContext
    }

    private fun unwrap(context: android.content.Context?): Activity? {
        var c = context
        repeat(16) {
            if (c is Activity) return c
            c = (c as? android.content.ContextWrapper)?.baseContext ?: return null
        }
        return null
    }

    /** 现在真正在前台的页面（【宿主】退到后台、被别的应用盖住时为 null）。 */
    fun foreground(): Activity? = front?.get()?.takeIf { !it.isDestroyed }

    fun onCreated(block: (Activity) -> Unit): () -> Unit = subscribe(created, block)

    fun onResumed(block: (Activity) -> Unit): () -> Unit = subscribe(resumedListeners, block)

    fun onPaused(block: (Activity) -> Unit): () -> Unit = subscribe(pausedListeners, block)

    fun onDestroyed(block: (Activity) -> Unit): () -> Unit = subscribe(destroyed, block)

    private fun subscribe(list: MutableList<(Activity) -> Unit>, block: (Activity) -> Unit): () -> Unit {
        list += block
        val undo = { list.remove(block); Unit }
        InstallRecorder.record(undo)
        return undo
    }

    /** [PerActivity] 第一次存值时登记，页面销毁时由这里清掉它的值。不随安装撤销（对象本身是长期的）。 */
    internal fun track(holder: PerActivity<*>) {
        perActivity.addIfAbsent(holder)
    }

    internal fun dispatchCreated(activity: Activity) = notify(created, activity, "created")

    internal fun dispatchResumed(activity: Activity) {
        last = WeakReference(activity)
        front = last
        notify(resumedListeners, activity, "resumed")
    }

    internal fun dispatchDestroyed(activity: Activity) {
        notify(destroyed, activity, "destroyed")
        perActivity.forEach { runCatching { it.destroyed(activity) }.onFailure { e -> ModuleLog.w("per-activity cleanup failed", e) } }
        if (last?.get() === activity) last = null
    }

    private fun notify(list: List<(Activity) -> Unit>, activity: Activity, what: String) {
        list.forEach { runCatching { it(activity) }.onFailure { e -> ModuleLog.w("activity $what listener failed", e) } }
    }
}

/**
 * 每个页面一份的值（悬浮按钮、视图监听……）：页面销毁时自动移除，并对值调用 [dispose]。
 * 值可以强引用页面本身（不会因此泄漏，销毁时就清掉了）。只在主线程用。
 */
class PerActivity<T : Any>(private val dispose: (T) -> Unit = {}) {
    private val values = WeakHashMap<Activity, T>()

    operator fun get(activity: Activity): T? = values[activity]

    operator fun set(activity: Activity, value: T) {
        HostActivities.track(this)
        values[activity] = value
    }

    fun getOrPut(activity: Activity, create: () -> T): T = values[activity] ?: create().also { set(activity, it) }

    /** 移除并返回（不调用 [dispose]，由调用方处理）。 */
    fun remove(activity: Activity): T? = values.remove(activity)

    /** 移除并调用 [dispose]。 */
    fun discard(activity: Activity) {
        values.remove(activity)?.let(dispose)
    }

    val activities: List<Activity> get() = values.keys.toList()
    val all: List<T> get() = values.values.toList()

    internal fun destroyed(activity: Activity) = discard(activity)
}

/**
 * 功能 `install()` 期间登记的撤销动作（钩子、页面订阅、设置监听、待执行的 onApplication）。
 * 运行时在调用 install 时开始登记，安装抛异常就按相反顺序全部撤销，避免半装的功能留下钩子，
 * 之后再装时重复挂上。不在安装期间（没有登记者）时什么都不做。按线程登记，安装都在主线程。
 */
object InstallRecorder {
    private val sink = ThreadLocal<MutableList<() -> Unit>?>()

    fun record(undo: () -> Unit) {
        sink.get()?.add(undo)
    }

    internal fun <T> recording(into: MutableList<() -> Unit>, block: () -> T): T {
        val previous = sink.get()
        sink.set(into)
        try {
            return block()
        } finally {
            sink.set(previous)
        }
    }

    /** 按登记的相反顺序撤销；单个撤销失败不影响其它。 */
    internal fun rollback(undo: List<() -> Unit>) {
        undo.asReversed().forEach { runCatching(it).onFailure { e -> ModuleLog.w("install rollback step failed", e) } }
    }
}
