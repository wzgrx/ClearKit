package io.github.clearkit.starter.hook.ui

import android.graphics.Rect
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.Window
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * 只看不抢的单击观察者（阶段 6A6 点封面直达）：整串手势照常交给宿主，只有一次干净的单击（单指、没超过 slop、
 * 按住时间短于 [TapTracker.MAX_TAP_MS]）在抬起时才问它要不要接管。接管时宿主收到 ACTION_CANCEL 而不是 UP，
 * 所以宿主的点击、长按都不会触发；不接管时什么都不变。用 [TapInterceptor] 登记。
 */
interface TapObserver {
    /** 按下时（窗口坐标）：这一下是不是可能归它（例如落在登记过的封面上、列表没在滚动）。 */
    fun eligible(x: Float, y: Float): Boolean

    /** 单击抬起：返回要执行的动作就接管（动作在下一帧执行），null 交给宿主。 */
    fun onTap(x: Float, y: Float): (() -> Unit)?
}

/** 给 hook/features 用的入口：在页面窗口上登记 [TapObserver]，接管失败时把单击原样还给宿主。 */
object TapInterceptor {
    fun observe(window: Window, observer: TapObserver): Boolean = FloatingTouchGate.observe(window, observer) != null

    fun stop(window: Window, observer: TapObserver) {
        FloatingTouchGate.of(window)?.removeObserver(observer)
    }

    /** 在 (x, y)（窗口坐标）重放一次单击给宿主，不经过观察者；没有闸门时返回 false。 */
    fun replayTap(window: Window, x: Float, y: Float): Boolean {
        val gate = FloatingTouchGate.of(window) ?: return false
        gate.replayTap(x, y)
        return true
    }
}

/**
 * 页面窗口的触摸闸门：套在窗口的 [Window.Callback] 外面（DecorView 收到触摸后第一个调用它，
 * 早于 Activity.dispatchTouchEvent 和整棵 View 树）。
 *
 * 按下点落在登记过的目标（悬浮按钮、底部提示条、顶部进度胶囊）上时，整串手势直接交给目标，
 * 【宿主】的 Activity 与 View 树都收不到；其它手势原样交给原来的 Callback。这样不管【宿主】在页面里放了什么、
 * 放在哪一层（图片的长按手势检测、后加进 decor 的浮层、抬高了 Z 的容器、Activity 层的触摸统计），
 * 都抢不到按在我们界面上的手势，也不依赖任何宿主私有符号。【宿主】自己也用同样的方式包这个 Callback
 * （handedness 采样），所以包一层是宿主容忍的做法。
 *
 * 同一个窗口只包一层：记住包过的闸门，即使【宿主】后来在外面又包了一层，下次登记也复用原来那层，
 * 不会每弹一次提示就多套一层。
 */
internal class FloatingTouchGate private constructor(private val base: Window.Callback) : Window.Callback by base {
    /** 一个可以接管手势的界面。 */
    interface Target {
        /** 层级：高的先问（提示条盖在悬浮按钮上面）；同层后登记的先问。 */
        val layer: Int get() = 0

        /** 按下点（窗口坐标）是否归它；[eventTime] 是 MotionEvent.eventTime。 */
        fun claims(x: Float, y: Float, eventTime: Long): Boolean

        /** 把窗口坐标的事件交给它（自己换算成 View 坐标）。 */
        fun deliver(event: MotionEvent)
    }

    // 只在主线程用（触摸分发、attach / detach 都在主线程）。
    private val targets = ArrayList<Target>()
    private val gesture = GestureOwner<Target>()
    private val observers = ArrayList<TapObserver>()
    private var tapOwner: TapObserver? = null
    private var tracker: TapTracker? = null
    private var replaying = false

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || targets.isEmpty() && gesture.owner == null && observers.isEmpty()) return base.dispatchTouchEvent(event)
        val target = gesture.route(event.actionMasked) {
            GateOrder.pick(targets, { it.layer }) { it.claims(event.x, event.y, event.eventTime) }
        }
        if (target != null) {
            target.deliver(event)
            return true
        }
        if (!replaying && (observers.isNotEmpty() || tapOwner != null) && observe(event)) return true
        return base.dispatchTouchEvent(event)
    }

    /** 观察宿主拿着的手势；接管了这次单击时返回 true（宿主已收到 CANCEL）。 */
    private fun observe(event: MotionEvent): Boolean {
        val tr = tracker ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapOwner = observers.lastOrNull { runCatching { it.eligible(event.x, event.y) }.getOrDefault(false) }
                tr.down(event.x, event.y, event.eventTime, tapOwner != null)
            }
            MotionEvent.ACTION_MOVE -> tr.move(event.x, event.y)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> { tr.cancel(); if (event.actionMasked == MotionEvent.ACTION_CANCEL) tapOwner = null }
            MotionEvent.ACTION_UP -> {
                val owner = tapOwner
                tapOwner = null
                if (owner == null || !tr.up(event.x, event.y, event.eventTime)) return false
                val action = runCatching { owner.onTap(event.x, event.y) }.getOrNull() ?: return false
                val cancel = MotionEvent.obtain(event)
                try {
                    cancel.action = MotionEvent.ACTION_CANCEL
                    base.dispatchTouchEvent(cancel)
                } finally {
                    cancel.recycle()
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post { action() }
                return true
            }
        }
        return false
    }

    /** 把一次单击原样交给宿主（接管失败时回退到宿主自己的点击）：在 (x, y) 按下再抬起，不经过观察者。 */
    fun replayTap(x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
        down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        replaying = true
        try {
            base.dispatchTouchEvent(down)
            base.dispatchTouchEvent(up)
        } finally {
            replaying = false
            down.recycle(); up.recycle()
        }
    }

    fun removeObserver(observer: TapObserver) {
        observers.remove(observer)
        if (tapOwner === observer) tapOwner = null
    }

    // Java 接口里带默认实现的方法也转给原 Callback（Activity 有自己的实现）。
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int) =
        base.onProvideKeyboardShortcuts(data, menu, deviceId)

    override fun onPointerCaptureChanged(hasCapture: Boolean) = base.onPointerCaptureChanged(hasCapture)

    fun remove(target: Target) {
        targets.remove(target)
    }

    companion object {
        // 值用弱引用：闸门强引用着 Activity / Dialog，进而引用 Window，强引用会让键永远不被回收。
        private val gates = WeakHashMap<Window, WeakReference<FloatingTouchGate>>()

        /** 在 [window] 上登记 [target]，返回负责它的闸门（不用时用它 [remove]）。 */
        fun register(window: Window, target: Target): FloatingTouchGate? {
            val current = window.callback ?: return null
            val gate = current as? FloatingTouchGate
                ?: gates[window]?.get()
                ?: FloatingTouchGate(current).also { window.callback = it }
            gates[window] = WeakReference(gate)
            if (target !in gate.targets) gate.targets += target
            return gate
        }

        /** 在 [window] 上登记只看不抢的 [observer]（见 [TapObserver]）。 */
        fun observe(window: Window, observer: TapObserver): FloatingTouchGate? {
            val current = window.callback ?: return null
            val gate = current as? FloatingTouchGate
                ?: gates[window]?.get()
                ?: FloatingTouchGate(current).also { window.callback = it }
            gates[window] = WeakReference(gate)
            if (gate.tracker == null) {
                val vc = android.view.ViewConfiguration.get(window.context)
                gate.tracker = TapTracker(vc.scaledTouchSlop.toFloat(), minOf(android.view.ViewConfiguration.getLongPressTimeout().toLong(), TapTracker.MAX_TAP_MS))
            }
            if (observer !in gate.observers) gate.observers += observer
            return gate
        }

        /** [window] 上已有的闸门（没有时 null）。 */
        fun of(window: Window): FloatingTouchGate? = window.callback as? FloatingTouchGate ?: gates[window]?.get()

        private val location = IntArray(2)

        /** 把窗口坐标的 [event] 换成 [view] 自己的坐标交给它（只有位移，没有缩放旋转的 View）。 */
        fun dispatchInWindowCoords(view: View, event: MotionEvent): Boolean {
            view.getLocationInWindow(location)
            val local = MotionEvent.obtain(event)
            return try {
                local.offsetLocation(-location[0].toFloat(), -location[1].toFloat())
                view.dispatchTouchEvent(local)
            } finally {
                local.recycle()
            }
        }
    }
}

/**
 * 浮在页面上的一块界面（提示条、进度胶囊）：显示期间，按在它可见范围里的手势整串交给它，
 * 其它地方照常交给页面。[onPress] 在按下（true）和抬起 / 取消（false）时回调，用来按住时暂停自动消失。
 */
internal class OverlayTouchTarget(
    private val view: View,
    override val layer: Int,
    private val onPress: ((Boolean) -> Unit)? = null,
) : FloatingTouchGate.Target {
    private var gate: FloatingTouchGate? = null
    private val rect = Rect()

    /** 正在退场：仍然挡住按在它上面的手势（不漏给页面），但不再交给它的按钮。 */
    var closing = false

    fun attach(window: Window) {
        gate = FloatingTouchGate.register(window, this)
    }

    fun detach() {
        gate?.remove(this)
        gate = null
    }

    override fun claims(x: Float, y: Float, eventTime: Long): Boolean {
        if (!view.isAttachedToWindow || !view.isShown || !view.getGlobalVisibleRect(rect)) return false
        return TouchBox(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()).contains(x, y)
    }

    override fun deliver(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onPress?.invoke(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onPress?.invoke(false)
        }
        // 中途被移除或正在退场：剩下的事件吞掉，不转给没收到过 DOWN 的页面。
        if (closing || !view.isAttachedToWindow) return
        FloatingTouchGate.dispatchInWindowCoords(view, event)
    }
}
