package io.github.clearkit.starter.hook.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import java.lang.ref.WeakReference

/**
 * 一个页面（Activity）上叠着的窗口：模块自己开的对话框（[HostSheet]、对话框承载的浮层）和【宿主】自己的弹层窗口
 * （视频页的评论弹层，由功能层用 [trackForeign] 登记）。提示、进度胶囊和浮层都要画在**最上面**那个窗口里，
 * 否则会被弹层盖住（例如评论弹层里看不到提示、进度没法取消）。
 *
 * 只在主线程用。
 */
object HostWindows {
    /** 一个可以往上加视图的窗口：[decor] 是根视图；[window] 拿不到时（宿主弹层）为 null，这时不登记触摸闸门。 */
    class Overlay(val decor: ViewGroup, val window: Window?, val ownedByPage: Boolean)

    private class Entry(activity: Activity, decor: ViewGroup, window: Window?) {
        val activity = WeakReference(activity)
        val decor = WeakReference(decor)
        val window = window?.let { WeakReference(it) }
    }

    private val stack = OverlayStack<Entry>()

    /** 模块自己开的对话框窗口显示了：之后的提示画在它上面。返回值在对话框关掉时调用。 */
    fun trackOwn(activity: Activity, window: Window): () -> Unit {
        val decor = window.decorView as? ViewGroup ?: return {}
        val e = Entry(activity, decor, window)
        stack.push(e)
        return { stack.remove(e) }
    }

    /**
     * 宿主自己的弹层窗口（根视图 [root] 不是页面窗口的 decor）：之后这个页面上的提示、浮层都放到它上面，
     * 直到它关掉（窗口移除）。同一个根视图只登记一次。
     */
    fun trackForeign(activity: Activity, root: View) {
        val decor = root as? ViewGroup ?: return
        if (decor === activity.window?.decorView) return
        if (stack.any { it.decor.get() === decor }) return
        stack.push(Entry(activity, decor, windowOf(decor)))
    }

    /** [activity] 上最上面的窗口；没有叠着的窗口时是页面自己的窗口。 */
    fun overlayFor(activity: Activity): Overlay? {
        stack.prune { it.activity.get() == null || it.decor.get() == null }
        val top = stack.top { it.activity.get() === activity && showing(it.decor.get()) }
        if (top != null) {
            val decor = top.decor.get()
            if (decor != null) return Overlay(decor, top.window?.get(), ownedByPage = false)
        }
        val window = activity.window ?: return null
        val decor = window.decorView as? ViewGroup ?: return null
        return Overlay(decor, window, ownedByPage = true)
    }

    /** [activity] 上是不是叠着别的窗口（这时浮层改用对话框承载，见 HostPanel）。 */
    fun covered(activity: Activity): Boolean = overlayFor(activity)?.ownedByPage == false

    private fun showing(decor: View?): Boolean =
        decor != null && decor.isAttachedToWindow && decor.windowVisibility == View.VISIBLE && decor.visibility == View.VISIBLE

    /** 对话框的 decor（DecorView）记着自己的窗口；拿不到（隐藏接口受限、PopupWindow）时 null。 */
    private fun windowOf(decor: ViewGroup): Window? = runCatching {
        var c: Class<*>? = decor.javaClass
        while (c != null && c != View::class.java) {
            val f = runCatching { c.getDeclaredField("mWindow") }.getOrNull()
            if (f != null) {
                f.isAccessible = true
                return@runCatching f.get(decor) as? Window
            }
            c = c.superclass
        }
        null
    }.getOrNull()
}

/** 后登记的在上面的窗口栈（纯逻辑，可单测）。 */
internal class OverlayStack<T : Any> {
    private val items = ArrayList<T>()

    fun push(item: T) {
        items.remove(item)
        items += item
    }

    fun remove(item: T) {
        items.remove(item)
    }

    fun any(predicate: (T) -> Boolean): Boolean = items.any(predicate)

    /** 去掉已经失效的项。 */
    fun prune(dead: (T) -> Boolean) {
        items.removeAll(dead)
    }

    /** 从上往下第一个满足 [usable] 的项。 */
    fun top(usable: (T) -> Boolean): T? = items.lastOrNull(usable)

    val size: Int get() = items.size
}
