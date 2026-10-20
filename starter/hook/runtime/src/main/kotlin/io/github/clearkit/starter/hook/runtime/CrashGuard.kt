package io.github.clearkit.starter.hook.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Remembers which feature's code was on the stack when the host crashed, so that feature is
 * skipped on the next launch. Lives in the host's own files dir; the app clears it by bumping
 * the `module.crashResetToken` setting.
 */
class CrashGuard(
    private val file: File,
    /** 功能对象的类名（`...features.note.NoteTracker`）→ 功能 id。 */
    private val featureClasses: () -> Map<String, String>,
) {
    @Serializable
    data class Record(val count: Int, val lastAt: Long, val message: String)

    @Serializable
    data class State(val resetToken: Int = 0, val features: Map<String, Record> = emptyMap())

    @Volatile
    var state: State = read()
        private set

    /** Applies a reset requested from the app; returns true when records were dropped. */
    fun syncResetToken(token: Int): Boolean {
        if (token == state.resetToken) return false
        state = State(resetToken = token)
        write()
        return true
    }

    fun blocked(featureId: String): Record? = state.features[featureId]

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordCrash(error) }
            previous?.uncaughtException(thread, error)
        }
    }

    internal fun recordCrash(error: Throwable) {
        val featureId = culprit(error) ?: return
        val old = state.features[featureId]
        val message = "${error.javaClass.simpleName}: ${error.message.orEmpty()}".take(300)
        state = state.copy(features = state.features + (featureId to Record((old?.count ?: 0) + 1, System.currentTimeMillis(), message)))
        write()
        ModuleLog.e("host crashed inside feature $featureId; it will be skipped next launch", error)
    }

    /**
     * 崩溃该算在哪个功能头上（只按包名不行：多个功能共用的工具出错时，会把共用工具所在包的功能停掉，
     * 用到它的其它功能跟着全部失灵）：
     *
     * 1. 栈上（从抛出点往外）第一个属于功能对象本身的帧：功能类或它里面的 Hook 体、监听器、lambda（`Feature$...`）。
     *    出错的是这个功能注册的 Hook 或监听器，不管它调了哪个共用工具。
     * 2. 没有这样的帧（例如面板按钮自己的点击监听）时，按包名找**最外层**（离入口最近）的那一帧：
     *    入口在哪个功能的包里就算谁的，深处调用的共用工具不背锅。同一个包里有多个功能时不按包名归因。
     *
     * Hook 体里 `call.proceed()` 调宿主原方法时宿主自己抛的异常，栈上也有功能的 Hook 体（它在等原方法返回），
     * 但这不是功能的错：栈里 [ChainCall]（proceed）那一帧往后（调用方一侧）的帧都不算，只看原方法内部的帧。
     * 原方法里又回调了功能的代码（例如功能设置的监听器）并在那里出错，那一帧在 proceed 之前，仍算功能的。
     */
    internal fun culprit(error: Throwable): String? {
        val classes = featureClasses()
        val byPackage = classes.entries.groupBy({ it.key.substringBeforeLast('.', "") }, { it.value })
            .filterValues { it.size == 1 }.mapValues { it.value.single() }
        var t: Throwable? = error
        val seen = HashSet<Throwable>()
        var fallback: String? = null
        while (t != null && seen.add(t)) {
            var outermost: String? = null
            for (frame in t.stackTrace) {
                if (frame.className == HOST_CALL_BOUNDARY) break
                val owner = frame.className.substringBefore('$')
                classes[owner]?.let { return it }
                val pkg = frame.className.substringBeforeLast('.', "")
                byPackage.entries.firstOrNull { pkg == it.key || pkg.startsWith(it.key + ".") }?.let { outermost = it.value }
            }
            if (fallback == null) fallback = outermost
            t = t.cause
        }
        return fallback
    }

    private fun read(): State = runCatching { if (file.isFile) json.decodeFromString(State.serializer(), file.readText()) else State() }
        .getOrDefault(State())

    private fun write() {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(State.serializer(), state))
            tmp.renameTo(file)
        }.onFailure { ModuleLog.w("crash guard not saved", it) }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** Hook 体调宿主原方法经过的类（[HookCall.proceed] 的实现）。R8 规则保留了它和方法，不内联。 */
        val HOST_CALL_BOUNDARY: String = ChainCall::class.java.name
    }
}
