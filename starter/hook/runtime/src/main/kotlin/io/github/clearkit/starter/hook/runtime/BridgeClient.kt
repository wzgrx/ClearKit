package io.github.clearkit.starter.hook.runtime

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import io.github.clearkit.starter.model.bridge.Bridge
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 【宿主】进程调用模块 App 的桥接 Provider。Android 11+ 在模块和【宿主】打过交道前互相看不见；
 * 调用因此失败时，从【宿主】当前页面启动一次无界面的握手页（给【宿主】授予 URI 权限），再重试。
 */
object BridgeClient {
    /** 模块 App 的包名（沿用旧版，覆盖安装、保留数据）。 */
    const val MODULE_PACKAGE = "io.github.clearkit.starter"
    private val uri: Uri = Uri.parse(Bridge.URI)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "bc-bridge").apply { isDaemon = true } }

    @Volatile
    private var handshakeAt = 0L

    /** Fire-and-forget call on the bridge thread. */
    fun post(context: Context, method: String, json: String? = null, arg: String? = null, onResult: (Bundle?) -> Unit = {}) {
        io.execute { onResult(call(context, method, json, arg)) }
    }

    /** Blocking call; never on the main thread. Returns null when the app is unreachable. */
    fun call(context: Context, method: String, json: String? = null, arg: String? = null): Bundle? {
        val extras = Bundle().apply { if (json != null) putString(Bridge.KEY_JSON, json) }
        attempt(context, method, extras, arg)?.let { return it }
        if (!handshake(context)) return null
        return attempt(context, method, extras, arg)
    }

    private fun attempt(context: Context, method: String, extras: Bundle, arg: String? = null): Bundle? = try {
        context.contentResolver.call(uri, method, arg, extras)
    } catch (e: IllegalArgumentException) {
        null // provider not visible (yet) or app not installed
    } catch (e: SecurityException) {
        ModuleLog.w("bridge refused $method", e)
        null
    } catch (e: Exception) {
        ModuleLog.w("bridge call $method failed", e)
        null
    }

    /**
     * 【宿主】当前真正在前台的页面（页面暂停时就清掉）；握手页要从它启动，留在【宿主】自己的任务栈里。
     * 6.9.2：以前用的是“最近进过前台的页面”，【宿主】切到后台后还会拿到它，于是在后台拉起握手页，
     * 握手页结束时把【宿主】带回前台——用户在用别的应用时突然跳回【宿主】。后台时不握手，等下次回到【宿主】再连。
     */
    private fun foreground(): Activity? = HostActivities.foreground()?.takeIf { !it.isFinishing }

    /**
     * 【宿主】被强行停止或手机重启后，它会看不到模块。这时从【宿主】当前页面启动一次无界面的
     * 握手页（不开新任务，结束后直接回到原页面）。冷启动时还没有页面，就等页面出现。
     */
    private fun handshake(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - handshakeAt < 30_000) return false
        handshakeAt = now
        var activity = foreground()
        var waited = 0
        while (activity == null && waited < 8_000) {
            SystemClock.sleep(200)
            waited += 200
            activity = foreground()
        }
        if (activity == null) {
            ModuleLog.w("bridge handshake skipped: no foreground page")
            return false
        }
        val intent = Intent().setComponent(ComponentName(MODULE_PACKAGE, Bridge.HANDSHAKE_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        val done = CountDownLatch(1)
        var started = false
        activity.runOnUiThread {
            started = runCatching { activity.startActivity(intent) }.onFailure { ModuleLog.w("bridge handshake not started", it) }.isSuccess
            done.countDown()
        }
        done.await(2, TimeUnit.SECONDS)
        if (!started) return false
        repeat(20) {
            SystemClock.sleep(100)
            if (attempt(context, Bridge.METHOD_PING, Bundle()) != null) return true
        }
        ModuleLog.w("bridge handshake timed out")
        return false
    }
}
