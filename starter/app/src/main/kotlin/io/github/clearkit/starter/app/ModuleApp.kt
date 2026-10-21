package io.github.clearkit.starter.app

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import io.github.clearkit.starter.app.adapt.HostAdapter
import io.github.clearkit.starter.app.data.HostControl
import io.github.clearkit.starter.app.data.ModuleService
import io.github.clearkit.starter.app.data.Root
import io.github.clearkit.starter.app.data.SettingsStore
import io.github.clearkit.starter.app.data.StatusRepository
import io.github.clearkit.starter.model.HostBuild
import io.github.clearkit.starter.model.bridge.Bridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ModuleApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var status: StatusRepository
        private set
    /** 【宿主】报来的计数（这周挡下几条、开关起作用几次）。 */
    val stats: io.github.clearkit.starter.app.data.StatsRepository by lazy { io.github.clearkit.starter.app.data.StatsRepository(this) }
    /** 最近改动（首页显示、可撤销）。 */
    val journal: io.github.clearkit.starter.app.data.ChangeJournal by lazy { io.github.clearkit.starter.app.data.ChangeJournal(this) }
    val configHistory: io.github.clearkit.starter.app.data.ConfigHistory by lazy { io.github.clearkit.starter.app.data.ConfigHistory(this) }

    /** 新版本【宿主】的本机适配：装着的版本没有内置符号表时，用 DexKit 现场生成。 */
    val hostAdapter: HostAdapter by lazy { HostAdapter(this) }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        ModuleService.start()
        settings = SettingsStore(this)
        Root.init(this)
        registerActivityLifecycleCallbacks(Foreground)
        settings.onWrite = { key, before, after -> journal.record(key, before, after) }
        status = StatusRepository(this)
        grantHostVisibility()
        // 电脑上的界面截图测试（Robolectric）只要界面，不起后台任务
        if (android.os.Build.FINGERPRINT == "robolectric") { ready.countDown(); return }
        // 全自动多版本适配：每次启动在后台判断一次，需要时用 DexKit 本机扫描；另外每 6 小时定时检查
        hostAdapter.check("【模块】启动")
        hostAdapter.schedulePeriodic()
        // App 正好在运行时【宿主】升级了：马上在后台适配，用户第一次打开新版时表已经在了（不在运行时由定时检查和首次打开时的请求兜底）
        runCatching {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addDataScheme("package")
            }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context, intent: Intent) {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    if (HostBuild.channelOf(pkg) != null) hostAdapter.checkNow("【宿主】升级到新版本", pkg)
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
        }
        Thread { runCatching { hostAdapter.refresh() } }.start()
        // 【宿主】重新启动后（报告里带启动时间），待重启的改动已经生效，清掉提示
        scope.launch { status.status.collect { s -> if (s != null && s.startedAt > 0) settings.hostStarted(s.startedAt) } }
        ready.countDown()
    }

    /**
     * 界面进出：第一次打开界面时按设置自动申请根权限；所有界面都切走后，如果有待重开的改动且打开了“离开时自动重开”，
     * 等一会儿（防止只是切到系统授权窗口、相册这类）再用根权限重启正在运行的【宿主】。
     */
    private object Foreground : ActivityLifecycleCallbacks {
        private var started = 0
        private var leaveJob: kotlinx.coroutines.Job? = null

        override fun onActivityStarted(activity: android.app.Activity) {
            started++
            leaveJob?.cancel()
            val app = instance
            if (android.os.Build.FINGERPRINT == "robolectric") return
            Root.autoRequest(app.settings[io.github.clearkit.starter.model.settings.Settings.Module.autoRoot])
        }

        override fun onActivityStopped(activity: android.app.Activity) {
            started = (started - 1).coerceAtLeast(0)
            if (started > 0 || activity.isChangingConfigurations) return
            val app = instance
            val m = io.github.clearkit.starter.model.settings.Settings.Module
            if (!app.settings[m.autoRestartHost] || app.settings.pendingRestart.value.isEmpty() || !Root.granted) return
            leaveJob = app.scope.launch {
                kotlinx.coroutines.delay(LEAVE_DELAY_MS)
                val done = HostControl.restartRunning(app) ?: return@launch
                app.settings.clearPendingRestart()
                if (done.isNotEmpty()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(app, "已重开【宿主】，刚才的改动生效了", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
        override fun onActivityResumed(activity: android.app.Activity) = Unit
        override fun onActivityPaused(activity: android.app.Activity) = Unit
        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
        override fun onActivityDestroyed(activity: android.app.Activity) = Unit

        private const val LEAVE_DELAY_MS = 1500L
    }

    /** 让【宿主】的各个渠道看得见桥接 Provider（Android 11+ 的包可见性）。 */
    fun grantHostVisibility() {
        HostBuild.Channel.entries.forEach { c ->
            runCatching { grantUriPermission(c.packageName, Bridge.URI.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    companion object {
        lateinit var instance: ModuleApp
            private set

        /** onCreate 做完后放行：桥接 Provider 和 WorkManager 的任务可能在 Application.onCreate 之前或之中就被调用。 */
        private val ready = java.util.concurrent.CountDownLatch(1)

        /** 等 App 初始化完，最多 [millis] 毫秒；主线程上不等。返回是否已就绪。 */
        fun awaitReady(millis: Long = 10_000L): Boolean {
            if (ready.count == 0L) return true
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return false
            return ready.await(millis, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }
}
