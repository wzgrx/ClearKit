package io.github.clearkit.starter.hook.runtime

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * 重开【宿主】（适配卡片、设置窗的“立即重开”共用）：
 * 1. 有根权限：交给 App 强行停止再打开（App 收到请求先回复、再执行，因为会结束调用方本身）。
 * 2. 没有：定好马上打开自己的启动页（能用精确闹钟就用；Android 14 起声明允许从后台启动），再结束进程。
 * 第 2 步之后进程就没了，自动打开有没有成功只能下次看：记一个时间戳，下次启动时如果离得太远，[onFailed] 之类的提示由调用方决定。
 */
object HostRestart {
    private val main = Handler(Looper.getMainLooper())

    fun restart(activity: Activity, onFailed: (Activity) -> Unit = {}) {
        SettingsWriter.requestRootRestart(activity) { rooted ->
            if (rooted) {
                // App 那边几秒内会强行停止我们；要是迟迟没动静（App 被拦、命令失败），退回普通重开
                main.postDelayed({ if (!activity.isFinishing) selfRestart(activity, onFailed) }, 6000)
                return@requestRootRestart
            }
            selfRestart(activity, onFailed)
        }
    }

    // 精确闹钟用的是【宿主】自己的权限，调用前已用 canScheduleExactAlarms() 判断，没有就退回普通闹钟（lint 认不出这个组合判断）
    @android.annotation.SuppressLint("MissingPermission")
    private fun selfRestart(activity: Activity, onFailed: (Activity) -> Unit) {
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        if (launch == null) { onFailed(activity); return }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val options = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
        } else null
        val pending = PendingIntent.getActivity(activity, 0x6263, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT, options)
        val scheduled = runCatching {
            val alarms = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = System.currentTimeMillis() + 500
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC, at, pending)
        }.onFailure { ModuleLog.w("restart alarm not set", it) }.isSuccess
        if (!scheduled) { onFailed(activity); return }
        ModuleLog.i("restarting the host without root")
        activity.finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
