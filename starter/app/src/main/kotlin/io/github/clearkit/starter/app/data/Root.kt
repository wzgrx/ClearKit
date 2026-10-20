package io.github.clearkit.starter.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 根权限：申请、记住结果、执行命令。
 * - 自动获取：打开【模块】的界面时在后台申请一次（只在还没被拒绝过时），见 [autoRequest]；
 * - 被拒绝或授权窗口没人点之后不再自动问，在“模块设置”里手动“重新获取”；
 * - KernelSU、APatch 不弹授权窗口：没在管理器里允许的应用连 su 都看不见（算作“没拿到”）。这种情况每次打开界面都悄悄再试一次，
 *   在管理器里允许以后不用手动重试；
 * - 结果记在本机（`root` 偏好），重开 App 后状态照旧显示，真正用的时候命令失败会改回“没有”。
 * 命令里不带任何账号信息、令牌，输出不写日志。
 */
object Root {
    enum class State(val label: String) {
        UNKNOWN("还没申请"),
        CHECKING("正在申请…"),
        GRANTED("已获取"),
        DENIED("被拒绝"),
        TIMEOUT("授权窗口没人点"),
        MISSING("没拿到"),
    }

    private val _state = MutableStateFlow(State.UNKNOWN)
    val state: StateFlow<State> = _state

    @Volatile private var prefs: android.content.SharedPreferences? = null

    @Volatile private var autoTried = false

    /** App 启动时读出上次的结果。 */
    fun init(context: Context) {
        val p = context.getSharedPreferences("root", Context.MODE_PRIVATE)
        prefs = p
        _state.value = runCatching { State.valueOf(p.getString("state", null) ?: "UNKNOWN") }.getOrDefault(State.UNKNOWN)
            .let { if (it == State.CHECKING) State.UNKNOWN else it }
    }

    val granted: Boolean get() = _state.value == State.GRANTED

    /**
     * 打开 App 界面时调用：[enabled]（自动获取开着）且还没被拒绝过时，在后台申请一次；每个进程只试一次。
     * 已经拿到过的也重新确认一次（用户可能在 Magisk / KernelSU 里收回了）。
     */
    fun autoRequest(enabled: Boolean) {
        if (!enabled) return
        val st = _state.value
        val now = android.os.SystemClock.elapsedRealtime()
        // “没拿到”（看不见 su）重试不弹窗：每次回到界面都试，至少隔 5 秒；在管理器里刚允许了，回来就能拿到
        if (st == State.MISSING) {
            if (now - lastQuietTry < 5_000) return
            lastQuietTry = now
        } else {
            if (autoTried) return
            autoTried = true
            if (st != State.UNKNOWN && st != State.GRANTED) return
        }
        Thread({ request() }, "bc-root").apply { isDaemon = true }.start()
    }

    @Volatile private var lastQuietTry = 0L

    /** 申请根权限（会弹授权窗口，最多等 [PROMPT_SECONDS] 秒）。不要在主线程调用。返回是否拿到了。 */
    fun request(): Boolean {
        _state.value = State.CHECKING
        val next = try {
            val p = ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
            if (!p.waitFor(PROMPT_SECONDS, TimeUnit.SECONDS)) {
                p.destroy()
                State.TIMEOUT
            } else {
                val out = p.inputStream.bufferedReader().readText().trim()
                if (p.exitValue() == 0 && out.lines().lastOrNull()?.trim() == "0") State.GRANTED else State.DENIED
            }
        } catch (_: IOException) {
            // 找不到 su：没有 root，或者 root 管理器只给授权过的应用看得见 su
            State.MISSING
        } catch (_: SecurityException) {
            State.MISSING
        }
        save(next)
        return next == State.GRANTED
    }

    /**
     * 要用根权限时调用：已经有了直接返回；还没问过的问一次；被拒绝过的不再弹窗（返回 false，调用方退回到不用 root 的做法）。
     */
    fun ensure(): Boolean = when (_state.value) {
        State.GRANTED -> true
        // 看不见 su 时重试不会弹窗，顺便看看是不是刚在管理器里允许了
        State.UNKNOWN, State.MISSING -> request()
        else -> false
    }

    /**
     * 用根权限执行 [command]，返回 (退出码, 输出)；没有根权限、超时返回 null。
     * 拿到过权限但这次执行失败（被收回）时状态改回“被拒绝”。
     */
    fun exec(command: String, seconds: Long = COMMAND_SECONDS): Pair<Int, String>? {
        if (!ensure()) return null
        return try {
            val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
                p.destroy()
                return null
            }
            val r = p.exitValue() to p.inputStream.bufferedReader().readText()
            // su 自己拒绝时退出码一般是 1 且没有执行命令；用一次 id 确认是不是权限没了
            if (r.first != 0 && !stillGranted()) { save(State.DENIED); null } else r
        } catch (_: IOException) {
            save(State.MISSING)
            null
        }
    }

    private fun stillGranted(): Boolean = runCatching {
        val p = ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
        p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0 && p.inputStream.bufferedReader().readText().trim().endsWith("0")
    }.getOrDefault(false)

    private fun save(s: State) {
        _state.value = s
        prefs?.edit { putString("state", s.name) }
    }

    /**
     * 用根权限放行【模块】的后台限制：加进系统省电白名单、允许后台运行；小米 / 红米系统再打开“自启动”。
     * 返回做成了几项（共 [BACKGROUND_STEPS] 项，不支持的系统上个别项会失败，属于正常）。
     */
    fun allowBackground(packageName: String): Int {
        val steps = listOf(
            "cmd deviceidle whitelist +$packageName",
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $packageName RUN_IN_BACKGROUND allow",
            // 小米系统的“自启动”（AUTO_START，编号 10008）；别的系统没有这个编号，会失败
            "cmd appops set $packageName 10008 allow",
        )
        return steps.count { exec("$it >/dev/null 2>&1", 10)?.first == 0 }
    }

    const val BACKGROUND_STEPS = 4
    private const val PROMPT_SECONDS = 20L
    private const val COMMAND_SECONDS = 30L
}
