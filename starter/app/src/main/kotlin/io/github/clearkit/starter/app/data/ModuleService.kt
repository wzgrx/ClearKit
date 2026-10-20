package io.github.clearkit.starter.app.data

import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.clearkit.starter.model.HostBuild
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 和 LSPosed 服务的连接；只有模块在 LSPosed 里启用后才有。 */
object ModuleService {
    sealed interface State {
        /** 刚启动，还在等 LSPosed 送来服务。冷启动时先是它，不会先闪“模块未启用”。 */
        data object Connecting : State

        /** 没连上：模块没在 LSPosed 里启用，或者没有框架。 */
        data object NotActive : State

        /** [scope]：作用域里有的【宿主】包名。 */
        data class Active(val framework: String, val version: String, val api: Int, val scope: Set<String>) : State
    }

    private const val CONNECT_TIMEOUT_MS = 2_000L

    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state

    @Volatile
    var service: XposedService? = null
        private set

    private val listeners = mutableListOf<(XposedService) -> Unit>()

    fun start() {
        Handler(Looper.getMainLooper()).postDelayed({ _state.compareAndSet(State.Connecting, State.NotActive) }, CONNECT_TIMEOUT_MS)
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(s: XposedService) {
                service = s
                refresh()
                synchronized(listeners) { listeners.toList() }.forEach { runCatching { it(s) } }
            }

            override fun onServiceDied(s: XposedService) {
                if (service === s) service = null
                _state.value = State.NotActive
            }
        })
    }

    fun onBind(listener: (XposedService) -> Unit) {
        synchronized(listeners) { listeners += listener }
        service?.let(listener)
    }

    fun refresh() {
        val s = service ?: return
        _state.value = runCatching {
            val hosts = HostBuild.Channel.entries.map { it.packageName }.toSet()
            State.Active(s.frameworkName, s.frameworkVersion, s.apiVersion, s.scope.filter { it in hosts }.toSet())
        }.getOrDefault(State.NotActive)
    }

    /** 请 LSPosed 把 [packageName] 加进作用域（会弹出管理器的确认通知）。 */
    fun requestScope(packageName: String, onDone: (Boolean) -> Unit) {
        val s = service ?: return onDone(false)
        s.requestScope(listOf(packageName), object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) { refresh(); onDone(packageName in approved) }
            override fun onScopeRequestFailed(message: String) { refresh(); onDone(false) }
        })
    }
}
