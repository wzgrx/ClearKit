package io.github.clearkit.starter.hook.runtime

import android.util.Log
import io.github.libxposed.api.XposedInterface

/** Logs through the framework (visible in LSPosed's log page) once bound, logcat before that. */
object ModuleLog {
    private const val TAG = "ClearStarter"

    @Volatile
    private var framework: XposedInterface? = null

    @Volatile
    var verbose: Boolean = false

    fun bind(framework: XposedInterface) {
        this.framework = framework
    }

    fun d(message: String) {
        if (verbose) write(Log.DEBUG, message, null)
    }

    fun i(message: String) = write(Log.INFO, message, null)

    fun w(message: String, error: Throwable? = null) = write(Log.WARN, message, error)

    fun e(message: String, error: Throwable? = null) = write(Log.ERROR, message, error)

    private fun write(priority: Int, message: String, error: Throwable?) {
        val target = framework
        if (target == null) {
            if (error == null) Log.println(priority, TAG, message) else Log.println(priority, TAG, message + '\n' + Log.getStackTraceString(error))
            return
        }
        if (error == null) target.log(priority, TAG, message) else target.log(priority, TAG, message, error)
    }
}
