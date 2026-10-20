package io.github.clearkit.starter.app.data

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * 后台任务申请前台（WorkManager setForeground）。Android 12 起 App 在后台时系统会拒绝
 * （ForegroundServiceStartNotAllowedException），以前被 runCatching 悄悄吞掉，任务按普通后台任务被 10 分钟停掉、没有进度通知，
 * 也查不到原因。现在：记日志、每类每 6 小时在“错误记录”里记一条（说明是系统后台限制），返回 false；
 * 任务照常以普通后台任务继续跑——被停掉时 WorkManager 会重排，下载、扫描从断点接着做。
 */
object ForegroundCompat {
    private const val TAG = "BcForeground"
    private const val RECORD_EVERY_MS = 6 * 3_600_000L
    private val lastRecorded = ConcurrentHashMap<String, Long>()

    suspend fun promote(worker: CoroutineWorker, info: ForegroundInfo, what: String): Boolean = try {
        worker.setForeground(info)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        denied(what, e)
        false
    }

    /** 进度回调里更新前台通知（非挂起）：失败同样记下，不抛出。 */
    fun promoteAsync(worker: CoroutineWorker, info: ForegroundInfo, what: String) {
        val future = runCatching { worker.setForegroundAsync(info) }.getOrElse { denied(what, it); return }
        future.addListener({ runCatching { future.get() }.onFailure { denied(what, (it as? java.util.concurrent.ExecutionException)?.cause ?: it) } }, Runnable::run)
    }

    private fun denied(what: String, e: Throwable) {
        Log.w(TAG, "$what: cannot run in foreground, continuing as a background job", e)
        val now = System.currentTimeMillis()
        val last = lastRecorded[what] ?: 0L
        if (shouldRecord(last, now)) {
            lastRecorded[what] = now
        }
    }

    internal fun shouldRecord(last: Long, now: Long): Boolean = last == 0L || now - last >= RECORD_EVERY_MS
}
