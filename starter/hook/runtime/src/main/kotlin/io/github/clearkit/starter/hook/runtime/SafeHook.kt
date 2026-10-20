package io.github.clearkit.starter.hook.runtime

import io.github.libxposed.api.XposedInterface

/** Wraps a framework chain so host exceptions can be told apart from feature bugs. */
internal class ChainCall(private val chain: XposedInterface.Chain) : HookCall {
    var proceeded = false
        private set
    var result: Any? = null
        private set
    var hostError: Throwable? = null
        private set

    override val thisObject: Any? get() = chain.thisObject
    override val args: List<Any?> get() = chain.args

    override fun proceed(): Any? = run { chain.proceed() }

    override fun proceed(vararg newArgs: Any?): Any? = run { chain.proceed(arrayOf(*newArgs)) }

    private inline fun run(block: () -> Any?): Any? {
        proceeded = true
        try {
            return block().also { result = it }
        } catch (t: Throwable) {
            hostError = t
            throw HostThrew(t)
        }
    }

    /** Marks an exception that came from the original method, so it is rethrown as-is. */
    class HostThrew(val original: Throwable) : RuntimeException(original) {
        override fun fillInStackTrace(): Throwable = this
    }
}

/**
 * Failure accounting for one feature: after [limit] failures within one process the feature is
 * switched off for the rest of the session (its hooks pass through).
 */
internal class Circuit(private val limit: Int = 5) {
    @Volatile
    var failures = 0
        private set

    @Volatile
    var lastError: Throwable? = null
        private set

    val open: Boolean get() = failures >= limit

    fun record(error: Throwable): Boolean {
        lastError = error
        failures++
        return failures == limit
    }
}

internal fun runHook(chain: XposedInterface.Chain, active: () -> Boolean, onFailure: (Throwable) -> Unit, body: (HookCall) -> Any?): Any? {
    if (!active()) return chain.proceed()
    val call = ChainCall(chain)
    return try {
        body(call)
    } catch (t: ChainCall.HostThrew) {
        throw t.original
    } catch (_: SkipHook) {
        if (call.proceeded) call.result else chain.proceed()
    } catch (t: Throwable) {
        onFailure(t)
        call.hostError?.let { throw it }
        if (call.proceeded) call.result else chain.proceed()
    }
}
