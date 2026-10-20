package io.github.clearkit.starter.hook.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot

/**
 * 悬浮按钮上的触摸（按钮自己的 OnTouchListener）：按下缩小，超过 slop 变拖动（跟手、不出父容器），
 * 长按弹管理菜单，抬起按 [FloatingLogic.release] 决定展开、点按、吸边还是回原位。
 * 手势判定在 [FloatingGesture]；按钮的状态和动作由 [Host]（[FloatingButton]）提供。
 */
internal class FloatingTouch(private val activity: Activity, private val main: Handler, private val host: Host) {
    interface Host {
        val collapsed: Boolean
        val locked: Boolean
        /** 长按时轻振（设置里可以关）。 */
        val haptic: Boolean get() = true
        /** [v] 还是当前挂着的按钮（换页、重建后旧视图上的长按不再弹菜单）。 */
        fun isCurrent(v: View): Boolean
        /** 按下：停掉闲置收起的计时，滚动淡出中的恢复。 */
        fun onPress()
        /** 按压缩放（减少动效时直接复原）。 */
        fun scale(v: View, s: Float, ms: Long)
        /** 开始拖动（之后的位置变化不再算吸边动画的目标）。 */
        fun onDragStart()
        fun onLongPress()
        fun onRelease(v: View, action: FloatingLogic.Release)
        /** ACTION_CANCEL：回到该在的位置。 */
        fun onCancel()
        /** 一串手势结束后重新开始闲置计时。 */
        fun scheduleCollapse()
    }

    val gesture = FloatingGesture()

    /** 最近一次拖动松手时手指的速度（px/s，屏幕坐标），吸边动画用它做初速度和投射；不是拖动松手时为 0。 */
    var releaseVelocityX = 0f
        private set
    var releaseVelocityY = 0f
        private set
    private var tracker: android.view.VelocityTracker? = null

    private fun track(e: MotionEvent) {
        val t = tracker ?: android.view.VelocityTracker.obtain().also { tracker = it }
        // 按钮跟着手指走，事件坐标是相对按钮的；用屏幕坐标算速度
        val raw = MotionEvent.obtain(e)
        raw.setLocation(e.rawX, e.rawY)
        t.addMovement(raw)
        raw.recycle()
    }

    private fun endTracking(up: Boolean) {
        val t = tracker
        if (up && t != null) {
            t.computeCurrentVelocity(1000, MAX_FLING)
            releaseVelocityX = t.xVelocity
            releaseVelocityY = t.yVelocity
        } else {
            releaseVelocityX = 0f
            releaseVelocityY = 0f
        }
        t?.recycle()
        tracker = null
    }

    /** 当前按钮的长按计时（挂在 [main] 上，[abort] 时一并取消）。 */
    private var longPress: Runnable? = null

    /** 按钮被移除（换页、重建）：长按菜单不能再弹出来，这串手势也就此结束。 */
    fun abort() {
        longPress?.let(main::removeCallbacks)
        longPress = null
        gesture.finish()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun install(v: View) {
        val slop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var longPressed = false
        val longPressTask = Runnable {
            if (!host.isCurrent(v) || !v.isAttachedToWindow || !gesture.canLongPress()) return@Runnable
            longPressed = true
            gesture.cancel()
            if (host.haptic) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            host.scale(v, 1f, 80)
            host.onLongPress()
        }
        longPress = longPressTask
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    endTracking(up = false)
                    track(e)
                    host.onPress()
                    gesture.down(e.getPointerId(0))
                    downX = e.rawX; downY = e.rawY; startX = v.x; startY = v.y; longPressed = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    if (!host.collapsed) host.scale(v, 0.94f, 80)
                    // 收起成边缘小条时也能长按打开管理菜单（读屏描述写的就是“点击展开，长按管理”）
                    main.postDelayed(longPressTask, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    track(e)
                    val id = e.getPointerId(0)
                    if (host.collapsed || !gesture.canDrag(id)) return@setOnTouchListener true
                    val wasDragging = gesture.dragged
                    if (gesture.move(id, hypot(e.rawX - downX, e.rawY - downY), slop)) {
                        if (!wasDragging) { main.removeCallbacks(longPressTask); if (!host.locked) host.scale(v, 1.02f, 100); host.onDragStart() }
                        // 本次锁定了位置：拖不动，松手时原地不动（只是不算点击）。
                        if (host.locked) return@setOnTouchListener true
                        val parent = v.parent as View
                        v.x = FloatingLogic.dragTo(startX, e.rawX, downX, parent.width, v.width)
                        v.y = FloatingLogic.dragTo(startY, e.rawY, downY, parent.height, v.height)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    main.removeCallbacks(longPressTask)
                    gesture.cancel()
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPressTask)
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    val id = e.getPointerId(0)
                    val click = gesture.canClick(id) && !longPressed
                    val dragged = gesture.dragged
                    track(e)
                    endTracking(up = dragged)
                    gesture.finish()
                    host.scale(v, 1f, 120)
                    host.onRelease(v, FloatingLogic.release(host.collapsed, dragged, host.locked, click))
                    host.scheduleCollapse()
                }
                MotionEvent.ACTION_CANCEL -> {
                    endTracking(up = false)
                    main.removeCallbacks(longPressTask)
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    gesture.finish()
                    host.scale(v, 1f, 120)
                    host.onCancel()
                    host.scheduleCollapse()
                }
            }
            true
        }
    }

    private companion object {
        /** 速度上限（px/s）：防止一次很快的甩动把按钮投射到屏幕外再弹回来太猛。 */
        const val MAX_FLING = 8000f
    }
}
