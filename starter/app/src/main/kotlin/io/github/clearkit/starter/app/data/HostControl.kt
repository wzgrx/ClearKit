package io.github.clearkit.starter.app.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import io.github.clearkit.starter.model.HostBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 重启 / 打开【宿主】。 */
object HostControl {
    /** 装着的【宿主】。 */
    fun installed(context: Context): List<HostBuild.Channel> = HostBuild.Channel.entries.filter { c ->
        runCatching { io.github.clearkit.starter.hook.runtime.HostVersion.packageInfo(context.packageManager, c.packageName) }.isSuccess
    }

    /**
     * 有根权限时重启【宿主】：[packageName] 强行停止后重新打开；另一个客户端正在运行的话也一起强行停止（下次打开就是新设置）。
     * 没有根权限时打开应用信息页让用户强行停止。返回是否在这里重启了。
     */
    suspend fun restart(context: Context, packageName: String): Boolean {
        val rooted = withContext(Dispatchers.IO) {
            val others = HostBuild.Channel.entries.map { it.packageName }.filter { it != packageName }
            val script = buildString {
                append(stopIfRunning(others))
                append("am force-stop $packageName")
                launchComponent(context, packageName)?.let { append(" && am start -n $it") }
            }
            Root.exec(script)?.first == 0
        }
        if (!rooted) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return rooted
    }

    /**
     * 离开【模块】后自动重开（要根权限，不弹任何页面）：在前台的【宿主】强行停止后重新打开，在后台运行的只强行停止，
     * 没运行的不动。返回 null = 没有根权限；否则是动过的客户端（可能为空）。
     */
    fun restartRunning(context: Context): List<String>? {
        if (!Root.granted) return null
        val packages = HostBuild.Channel.entries.map { it.packageName }
        val running = Root.exec(packages.joinToString("; ") { "pidof $it >/dev/null && echo $it" }, 10)?.second
            ?.lines()?.map { it.trim() }?.filter { it in packages } ?: return null
        if (running.isEmpty()) return emptyList()
        val top = foreground()
        val script = running.joinToString("; ") { p ->
            val launch = if (p == top) launchComponent(context, p) else null
            if (launch != null) "am force-stop $p && am start -n $launch" else "am force-stop $p"
        }
        return if (Root.exec(script)?.first == 0) running else null
    }

    /** 现在在前台的应用包名（读 dumpsys，要根权限）；读不到返回 null。 */
    private fun foreground(): String? {
        val out = Root.exec("dumpsys activity activities | grep -m 1 -E 'topResumedActivity|mResumedActivity'", 10)?.second ?: return null
        return Regex("""\s([a-zA-Z0-9_.]+)/""").find(out)?.groupValues?.get(1)
    }

    private fun stopIfRunning(packages: List<String>) = packages.joinToString("") { "pidof $it >/dev/null && am force-stop $it; " }

    private fun launchComponent(context: Context, packageName: String): String? =
        context.packageManager.getLaunchIntentForPackage(packageName)?.component?.flattenToShortString()

    /** 有没有可用的根权限（还没问过的问一次，被拒绝过的直接返回 false）。不要在主线程调用。 */
    fun rootAvailable(): Boolean = Root.ensure()

    fun open(context: Context, packageName: String) {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
