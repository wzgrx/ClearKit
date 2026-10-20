package io.github.clearkit.starter.hook.runtime

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * 功能被自动停用时在【宿主】里提醒一次：
 * - 连续出错熔断（本次运行停用）：熔断那一刻排进提醒；
 * - 上次导致【宿主】崩溃、这次启动没装（CRASH_DISABLED）：每条崩溃记录只提醒一次（记在 [file] 里，按功能和崩溃时间去重）。
 *
 * 提醒在下一次有页面回到前台时由 [presenter]（hook/features 里用 HostToast 实现）显示：
 * “【模块】：X 已暂停（原因），可在设置里重新启用”，几个功能同时停用时合成一条。
 */
class FeatureStopNotices(private val file: File) {
    data class Notice(val title: String, val reason: String)

    private val main = Handler(Looper.getMainLooper())
    private val pending = ArrayList<Notice>()

    /** 熔断（主线程或 Hook 所在线程都可能调用）。 */
    fun circuitTripped(title: String) = post(Notice(title, "连续出错"))

    /** 这次启动因为上次崩溃没装的功能：[crashedAt] 是那次崩溃的时间（同一次崩溃只提醒一次）。 */
    fun crashDisabled(id: String, title: String, crashedAt: Long) {
        val key = "$id@$crashedAt"
        val shown = runCatching { if (file.isFile) file.readLines().toSet() else emptySet() }.getOrDefault(emptySet())
        if (key in shown) return
        runCatching {
            file.parentFile?.mkdirs()
            // 只留最近的 50 条，文件不会越长越大。
            file.writeText((shown.toList().takeLast(49) + key).joinToString("\n"))
        }.onFailure { ModuleLog.w("stop notice record not saved", it) }
        post(Notice(title, "上次导致【宿主】崩溃"))
    }

    private fun post(n: Notice) = main.post {
        if (pending.none { it.title == n.title }) pending += n
        HostActivities.resumed()?.let(::flush)
    }

    /** 有页面在前台时显示攒下的提醒（主线程）。 */
    fun flush(activity: Activity) {
        if (pending.isEmpty() || activity.isFinishing) return
        val show = presenter ?: return
        val (message, detail) = text(pending.toList())
        pending.clear()
        runCatching { show(activity, message, detail) }.onFailure { ModuleLog.w("stop notice not shown", it) }
    }

    companion object {
        /** 显示提醒：页面、主文字、说明。由 hook/features 在加载时设置。 */
        @Volatile
        var presenter: ((Activity, String, String?) -> Unit)? = null

        /** 提醒文字（纯函数，可单测）。 */
        fun text(notices: List<Notice>): Pair<String, String?> {
            if (notices.size == 1) {
                val n = notices.single()
                return "【模块】：${n.title} 已暂停（${n.reason}）" to "可在设置里重新启用"
            }
            val names = notices.take(3).joinToString("、") { it.title } + if (notices.size > 3) " 等" else ""
            val reasons = notices.map { it.reason }.distinct().joinToString("、")
            return "【模块】：${notices.size} 个功能已暂停（$reasons）" to "$names · 可在设置里重新启用"
        }
    }
}
