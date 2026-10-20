package io.github.clearkit.starter.hook.runtime

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.os.Handler
import android.os.Looper
import io.github.clearkit.starter.model.bridge.Bridge
import io.github.clearkit.starter.model.bridge.FeatureStatus
import io.github.clearkit.starter.model.bridge.FeatureStatus.State
import io.github.clearkit.starter.model.bridge.RuntimeStatus
import io.github.clearkit.starter.model.bridge.SettingsSnapshot
import io.github.clearkit.starter.model.settings.SETTINGS_GROUP
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.symbols.SymbolCatalog
import io.github.wzgrx.dexadapt.runtime.ContractMonitor
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.lang.reflect.Executable
import java.util.concurrent.CopyOnWriteArrayList

/** 在【宿主】主进程里运行全部功能，每个进程一个实例。 */
class FeatureRuntime(
    private val xposed: XposedInterface,
    private val features: List<Feature>,
    override val hostClassLoader: ClassLoader,
    dataDir: File,
    private val hostPackage: String,
    private val hostVersionCode: Long?,
    private val hostVersionName: String,
    private val moduleVersion: String,
    /** 宿主安装包身份；本机表必须是对着它生成的（同版本号换了灰度包 / 正式包时旧表不能用）。读不到时不核对。 */
    private val hostApkIdentity: String? = null,
) : RuntimeContext {
    init { HostSettings.client = io.github.clearkit.starter.model.settings.ConfigScope.clientOf(hostPackage) }

    override val settings = HostSettings(runCatching { xposed.getRemotePreferences(SETTINGS_GROUP) }.onFailure { ModuleLog.e("remote settings unavailable", it) }.getOrNull())

    /** 免 Root 加载（LSPatch / NPatch）时设置的缓存：上次从 App 拉到的快照，下次启动先用它。只在没有远程设置时读写。 */
    private val settingsCache = File(dataDir, "files/module/settings_cache.json")

    init {
        if (!settings.remote) runCatching {
            if (settingsCache.isFile) settings.replace(Bridge.json.decodeFromString(SettingsSnapshot.serializer(), settingsCache.readText()).toMap())
            ModuleLog.i("no remote settings (patched app?): ${if (settings.readable) "using cached settings" else "waiting for the app"}")
        }.onFailure { ModuleLog.w("settings cache unreadable", it) }
    }
    // 选表交给 DexAdapt 的 AdaptPolicy：这一版模块对着这个安装包生成的本机表 → 内置表 → 对着这个安装包、但模块更新前生成的
    // 本机表（查询旧了、位置还对，同时请 App 重扫）→ 都没有。安装包对不上的本机表（同版本号换了灰度包 / 正式包）不用。
    private val choice = hostVersionCode?.let { vc ->
        io.github.wzgrx.dexadapt.runtime.AdaptPolicy.choose(HostSymbols.loadRemote(xposed, vc), HostSymbols.loadTable(vc), SymbolCatalog.fingerprint, hostApkIdentity)
    }
    override val symbols = HostSymbols(choice?.table, hostClassLoader).also {
        choice?.table?.let { t ->
            val what = if (choice.source == io.github.wzgrx.dexadapt.runtime.AdaptPolicy.Source.BUNDLED) "bundled" else "locally adapted (${choice.source.name.lowercase()})"
            ModuleLog.i("using $what symbol table for ${t.versionCode}: ${t.entries.size} found, ${t.missing.size} missing")
        }
    }

    /** 需要 App 本机适配：没有可用的表，或只有模块更新前生成的本机表。热适配换表后按新表重新判断。 */
    private val needsAdaptation: Boolean
        get() = symbols.table.let { t -> t == null || (t.generator.isNotEmpty() && !fresh(t)) }

    /** 本机表可以直接用：这一版模块的查询生成，且对着当前安装包（读不到安装包身份、或旧表没记时只看查询）。 */
    private fun fresh(t: io.github.wzgrx.dexadapt.SymbolTable): Boolean =
        io.github.wzgrx.dexadapt.runtime.AdaptPolicy.choose(t, null, SymbolCatalog.fingerprint, hostApkIdentity).source == io.github.wzgrx.dexadapt.runtime.AdaptPolicy.Source.LOCAL

    /**
     * 观察模式（DexAdapt 的 ContractMonitor）：本机适配出的表没在电脑上验证过，带契约的 Hook 点先只记录调用，
     * 满足契约后才放开；内置表直接用（null）。判定按表存在【宿主】自己的目录里，同一张表下次启动不用再观察。
     */
    @Volatile
    private var contracts: ContractMonitor? = choice?.takeIf { it.observe }?.table?.let(::monitorFor)

    private val contractFile = File(dataDir, "files/module/contracts.json")

    private fun monitorFor(table: io.github.wzgrx.dexadapt.SymbolTable): ContractMonitor? {
        if (SymbolCatalog.catalog.contracts.isEmpty()) return null
        val store = object : ContractMonitor.Store {
            override fun load(tableKey: String): Map<String, String> = runCatching {
                val all = Bridge.json.decodeFromString(VERDICTS, contractFile.readText())
                all[tableKey].orEmpty()
            }.getOrDefault(emptyMap())

            // 只留当前这张表的判定（换表后旧的没用了）
            override fun save(tableKey: String, verdicts: Map<String, String>) {
                contractFile.parentFile?.mkdirs()
                val tmp = File(contractFile.path + ".tmp")
                tmp.writeText(Bridge.json.encodeToString(VERDICTS, mapOf(tableKey to verdicts)))
                tmp.renameTo(contractFile)
            }
        }
        return ContractMonitor(SymbolCatalog.catalog.contracts, ContractMonitor.key(table), symbols::classOf, store) { ModuleLog.i(it) }
            .also { ModuleLog.i("observing ${SymbolCatalog.catalog.contracts.size} contracted hook points on this locally adapted table") }
    }

    private val crashGuard = CrashGuard(File(dataDir, "files/module/crash_guard.json")) {
        features.associate { it.javaClass.name to it.id }
    }
    private val entries = features.associateWith { FeatureEntry(it) }
    /** 功能被自动停用（熔断、崩溃保护）时在【宿主】里提醒一次。 */
    private val stopNotices = FeatureStopNotices(File(dataDir, "files/module/stop_notices.txt"))
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    override var application: Application? = null
        private set
    private val appCallbacks = CopyOnWriteArrayList<(Application) -> Unit>()

    fun start() {
        current = this
        ModuleLog.verbose = settings[Settings.Module.verboseLog]
        crashGuard.syncResetToken(settings[Settings.Module.crashResetToken])
        crashGuard.install()
        hookApplicationCreate()
        // LSPosed 不会把 RemotePreferences 的变化推给运行中的宿主：每次页面回到前台（例如从 App 切回来）
        // 从 App 拉一次设置。
        HostActivities.onResumed { pullSettings() }
        // 免 Root 加载：没有远程设置，Application 一建好就从 App 拉一次
        if (!settings.remote) whenApplication { pullSettings() }
        if (!settings.readable) ModuleLog.e("settings unreadable: every feature stays off this launch")
        if (settings.readable && settings.masterEnabled) entries.values.forEach { it.tryInstall(FeatureInstall.Trigger.START) }
        HostActivities.onResumed { stopNotices.flush(it) }
        entries.values.filter { it.state == State.CRASH_DISABLED }.forEach { e ->
            crashGuard.blocked(e.feature.id)?.let { stopNotices.crashDisabled(e.feature.id, e.feature.title, it.lastAt) }
        }
        settings.onChange { onSettingsChanged() }
        // 只有真的要重新解析时才请 App 适配、才显示适配卡片；已经适配好（内置表、这一版模块生成的本机表）就直接用，不打扰
        if (needsAdaptation && hostVersionCode != null) whenApplication { app -> requestAdaptation(app, hostVersionCode) }
        else if (hostVersionCode != null && settings[Settings.Module.adaptCard]) whenApplication { app -> verifyOnce(app, hostVersionCode) }
        ModuleLog.i("runtime started: " + entries.values.groupingBy { it.state }.eachCount())
    }

    /**
     * 【宿主】升级到没有可用符号表的版本（或模块更新后本机表过期）：请 App 在后台本机适配（DexKit，几秒），
     * 然后每 3 秒看一次 LSPosed 远程文件，新表到了就当场换上、把之前不可用的功能装上（热适配，不用重启）。
     * 少数在启动最早期才生效的功能（如开屏）下次打开【宿主】生效。App 起不来时每个版本提醒一次。
     */
    private fun requestAdaptation(app: Application, versionCode: Long) {
        if (settings[Settings.Module.adaptCard]) main.postDelayed({ AdaptationEvents.listener?.onAdapting(hostVersionName) }, 1200)
        main.postDelayed({
            BridgeClient.post(app, Bridge.METHOD_HOST_ADAPT, arg = "$hostPackage:$versionCode") { reply ->
                ModuleLog.i("adaptation requested for $versionCode: ${reply?.getString(Bridge.KEY_JSON) ?: "app unreachable"}")
            }
            Thread({ waitForAdaptation(app, versionCode) }, "bc-adapt-wait").apply { isDaemon = true }.start()
        }, 2000)
    }

    /**
     * 已经有可用的表（内置表、这一版模块生成的本机表）：每个“模块版本 × 【宿主】版本”第一次启动时也显示适配卡片，
     * 逐个核对符号能不能在这个安装包里找到（进度条走一遍），最后报找到几个、几个功能生效；以后不再显示。
     * 核对在主线程分批做（找类时可能触发【宿主】的类初始化，不放到后台线程）。
     */
    private fun verifyOnce(app: Application, versionCode: Long) {
        val flag = File(app.filesDir, "module/adapt_verified_" + "${moduleVersion}_$versionCode".replace(Regex("[^A-Za-z0-9._-]"), "_"))
        if (flag.exists()) return
        val all = SymbolCatalog.all
        if (all.isEmpty()) return
        var index = 0
        var found = 0
        fun step() {
            val end = minOf(index + 4, all.size)
            while (index < end) { if (runCatching { symbols.available(all[index]) }.getOrDefault(false)) found++; index++ }
            AdaptationEvents.listener?.onProgress(index, all.size)
            if (index < all.size) { main.postDelayed(::step, 45); return }
            // 分母按这一版的表应有的数量：标了“这个版本没有”（absentIn）的符号不算没找到
            val expected = symbols.table?.let { t -> t.entries.size + t.missing.size }?.coerceAtMost(all.size) ?: all.size
            AdaptationEvents.listener?.onAdapted(hostVersionName, minOf(found, expected), expected, entries.values.count { it.installed })
            runCatching { flag.parentFile?.mkdirs(); flag.writeText("1") }
        }
        main.postDelayed({ AdaptationEvents.listener?.onVerifying(hostVersionName) }, 1200)
        main.postDelayed(::step, 1800)
    }

    private fun waitForAdaptation(app: Application, versionCode: Long) {
        val deadline = android.os.SystemClock.elapsedRealtime() + ADAPT_WAIT_MS
        val card = settings[Settings.Module.adaptCard]
        var tick = 0
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            // 卡片显示时每半秒问一次 App 的解析进度；新表每 1.5 秒看一次
            Thread.sleep(if (card) 500 else 3000)
            if (card) adaptProgress(app)?.let { p -> main.post { AdaptationEvents.listener?.onProgress(p.done, p.total) } }
            if (card && ++tick % 3 != 0) continue
            val t = HostSymbols.loadRemote(xposed, versionCode) ?: continue
            if (!fresh(t) || t == symbols.table) continue
            main.post {
                symbols.replace(t)
                contracts = monitorFor(t)
                val before = entries.values.count { it.installed }
                // 换了表才重试之前安装失败的功能（失败时挂上的钩子已撤销）。
                entries.values.forEach { it.tryInstall(FeatureInstall.Trigger.SYMBOLS) }
                val added = entries.values.count { it.installed } - before
                ModuleLog.i("hot adaptation: table for $versionCode applied, $added features installed")
                scheduleReport()
                val listener = AdaptationEvents.listener
                if (card && listener != null) {
                    listener.onAdapted(hostVersionName, t.entries.size, t.entries.size + t.missing.size, entries.values.count { it.installed })
                } else {
                    toast(app, "【模块】已自动适配【宿主】 $hostVersionName，$added 个功能已生效")
                }
            }
            return
        }
        ModuleLog.w("adaptation for $versionCode did not finish in time")
        if (card) main.post { AdaptationEvents.listener?.onTimeout(hostVersionName) }
        val flag = File(app.filesDir, "module/adapt_hint_$versionCode")
        if (flag.exists()) return
        runCatching { flag.parentFile?.mkdirs(); flag.writeText("1") }
        main.post { toast(app, "【模块】：这个版本的【宿主】（$hostVersionName）还没适配好。打开【模块】会自动在本机适配，之后重启【宿主】") }
    }

    /** App 本机适配的当前进度（旧版 App 不认识这个接口时 null）。在后台线程调用。 */
    private fun adaptProgress(app: Application): io.github.clearkit.starter.model.adapt.AdaptProgress? = runCatching {
        val reply = BridgeClient.call(app, Bridge.METHOD_HOST_ADAPT_STATUS, null, null) ?: return null
        if (!reply.getBoolean(Bridge.KEY_OK, false)) return null
        reply.getString(Bridge.KEY_JSON)?.let { Bridge.json.decodeFromString(io.github.clearkit.starter.model.adapt.AdaptProgress.serializer(), it) }
    }.getOrNull()

    private fun toast(app: Application, text: String) {
        runCatching { android.widget.Toast.makeText(app, text, android.widget.Toast.LENGTH_LONG).show() }
    }

    private fun onSettingsChanged() {
        ModuleLog.verbose = settings[Settings.Module.verboseLog]
        if (crashGuard.syncResetToken(settings[Settings.Module.crashResetToken])) ModuleLog.i("crash records cleared from the app")
        if (settings.masterEnabled) entries.values.forEach { it.tryInstall(FeatureInstall.Trigger.SETTINGS) }
        scheduleReport()
    }

    // ---- Application ----

    private fun hookApplicationCreate() {
        val target = Instrumentation::class.java.getDeclaredMethod("callApplicationOnCreate", Application::class.java)
        xposed.hook(target).intercept { chain ->
            val result = chain.proceed()
            val app = chain.getArg(0) as? Application
            if (application == null && app != null) {
                application = app
                HostActivities.install(app)
                appCallbacks.forEach { cb -> runCatching { cb(app) }.onFailure { ModuleLog.e("application callback failed", it) } }
                appCallbacks.clear()
                scheduleReport()
            }
            result
        }
    }

    @Volatile
    private var lastPull = 0L

    private fun pullSettings() {
        val app = application ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPull < 2000) return
        lastPull = now
        BridgeClient.post(app, Bridge.METHOD_SETTINGS) { reply ->
            val json = reply?.getString(Bridge.KEY_JSON) ?: return@post
            runCatching { Bridge.json.decodeFromString(SettingsSnapshot.serializer(), json).toMap() }
                .onSuccess { values ->
                    main.post { settings.replace(values) }
                    if (!settings.remote) runCatching { settingsCache.parentFile?.mkdirs(); settingsCache.writeText(json) }
                }
                .onFailure { ModuleLog.w("settings snapshot unreadable", it) }
        }
    }

    override fun whenApplication(block: (Application) -> Unit) {
        application?.let { block(it); return }
        appCallbacks += block
        // 安装失败时撤掉还没执行的回调（否则 Application 创建后它仍会替失败的功能注册东西）。
        InstallRecorder.record { appCallbacks.remove(block) }
    }

    // ---- Status ----

    private val reportTask = Runnable { report() }

    private fun scheduleReport() {
        main.removeCallbacks(reportTask)
        main.postDelayed(reportTask, 1500)
    }

    private fun report() {
        val app = application ?: return
        val status = status()
        BridgeClient.post(app, Bridge.METHOD_STATUS, Bridge.json.encodeToString(RuntimeStatus.serializer(), status)) { reply ->
            if (reply == null) ModuleLog.d("status not delivered (app unreachable)")
        }
    }

    fun status() = RuntimeStatus(
        moduleVersion = moduleVersion,
        hostPackage = hostPackage,
        hostVersionCode = hostVersionCode ?: 0,
        hostVersionName = hostVersionName,
        adapted = symbols.supported,
        safeMode = settings.safeMode,
        masterEnabled = settings.masterEnabled,
        features = entries.values.map(FeatureEntry::status),
        reportedAt = System.currentTimeMillis(),
        // 这次【宿主】进程开始运行的时间：App 据此判断“需重启”的改动是不是已经被新进程读到（没有 root 时也能清掉提示）
        startedAt = startedAt,
        remoteSettings = settings.remote,
    )

    private val startedAt = System.currentTimeMillis()

    /** 受设置键 [key] 控制的功能 id（设置说明里列出“对应的功能”）。 */
    fun featuresControlledBy(key: String): List<String> =
        features.filter { f -> f.switches.any { it.key == key } || f.listSwitches.any { it.key == key } }.map { it.id }

    val versionName: String get() = hostVersionName
    val moduleVersionName: String get() = moduleVersion

    companion object {
        /** 【宿主】启动时请 App 本机适配后，最多等这么久新表（App 冷启动 + 解析一般十秒内）。 */
        private const val ADAPT_WAIT_MS = 150_000L

        /** contracts.json：表的身份 → 符号 id → 判定。 */
        private val VERDICTS = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

        /** 本进程的运行时（【宿主】内设置浮层读功能状态用）。 */
        @Volatile
        var current: FeatureRuntime? = null
            private set
    }

    // ---- Features ----

    private inner class FeatureEntry(override val feature: Feature) : FeatureScope {
        private val lifecycle = FeatureInstall()
        val installed: Boolean get() = lifecycle.installed
        val state: State get() = lifecycle.state
        val circuit = Circuit()

        override val settings: HostSettings get() = this@FeatureRuntime.settings
        override val symbols: HostSymbols get() = this@FeatureRuntime.symbols
        override val hostClassLoader: ClassLoader get() = this@FeatureRuntime.hostClassLoader

        /** 维护模式里被停用（设计 086）。 */
        private val maintenanceOff: Boolean get() = feature.id in settings[Settings.Module.disabledFeatures]

        override val active: Boolean get() = installed && !circuit.open && feature.isOn(settings) && !maintenanceOff && !heldByContract

        /** 本功能 Hook 的、带契约的符号（观察模式下它们都通过了功能才生效）。 */
        private val contracted = java.util.concurrent.CopyOnWriteArraySet<String>()

        private fun verdicts(): List<Pair<String, ContractMonitor.Verdict>> = contracts?.let { m -> contracted.map { it to m.verdict(it) } }.orEmpty()

        /** 契约不符的第一个 Hook 点和原因。 */
        private val violation: String?
            get() = contracts?.let { m -> verdicts().firstOrNull { it.second == ContractMonitor.Verdict.VIOLATED }?.let { (id, _) -> "$id 契约不符：${m.reason(id)}" } }

        /** 还在观察，或有 Hook 点契约不符：功能的钩子只放行原方法。 */
        private val heldByContract: Boolean get() = verdicts().any { it.second != ContractMonitor.Verdict.PASSED }

        fun status(): FeatureStatus {
            val bad = violation
            if (maintenanceOff) return FeatureStatus(feature.id, feature.title, State.OFF, "维护模式里停用")
            val shown = when {
                installed && circuit.open -> State.FAILED
                installed && bad != null -> State.UNAVAILABLE
                installed && !feature.isOn(settings) -> State.OFF
                installed && heldByContract -> State.OBSERVING
                installed -> State.ACTIVE
                else -> state
            }
            val why = when (shown) {
                State.FAILED -> if (circuit.open) circuit.lastError?.let { "${it.javaClass.simpleName}: ${it.message}" } else lifecycle.detail
                State.UNAVAILABLE -> bad ?: lifecycle.detail
                State.OBSERVING -> verdicts().let { v -> "新版本先观察：${v.count { it.second == ContractMonitor.Verdict.PASSED }} / ${v.size} 个 Hook 点已验证" }
                else -> lifecycle.detail
            }
            return FeatureStatus(feature.id, feature.title, shown, why)
        }

        /**
         * 带契约的 Hook 点在观察中：调用原方法、按契约检查参数和返回值，不跑功能的代码。判定变了（通过或不符）时上报状态。
         * 返回 null 表示不用观察（没有契约、已通过），交给功能。
         */
        private fun observe(chain: XposedInterface.Chain, id: String?): Any? {
            val m = contracts ?: return NOT_OBSERVED
            if (id == null || id !in contracted) return NOT_OBSERVED
            return when (m.verdict(id)) {
                ContractMonitor.Verdict.PASSED -> NOT_OBSERVED
                ContractMonitor.Verdict.VIOLATED -> chain.proceed()
                ContractMonitor.Verdict.OBSERVING -> {
                    val result = chain.proceed()
                    val now = m.record(id, chain.thisObject, chain.args, result, Looper.myLooper() == Looper.getMainLooper())
                    if (now != ContractMonitor.Verdict.OBSERVING) {
                        if (now == ContractMonitor.Verdict.VIOLATED) ModuleLog.w("feature ${feature.id}: $id 契约不符（${m.reason(id)}），本版本不启用")
                        else if (!heldByContract) ModuleLog.i("feature ${feature.id}: all contracted hook points verified, now active")
                        scheduleReport()
                    }
                    result
                }
            }
        }

        private fun contractedId(target: Executable): String? =
            symbols.idOf(target)?.takeIf { it in SymbolCatalog.catalog.contracts }?.also { contracted += it }

        private val conditions = object : FeatureInstall.Conditions {
            override val pausedBySafeMode get() = settings.safeMode && !feature.keepInSafeMode
            override val on get() = feature.isOn(settings) && !maintenanceOff
            override fun crashBlocked() = crashGuard.blocked(feature.id)?.takeIf { settings[Settings.Module.autoDisableCrashing] }?.message
            override fun missing() = feature.requires.firstNotNullOfOrNull { s -> symbols.problem(s)?.let { "${s.id}: $it" } }
        }

        fun tryInstall(trigger: FeatureInstall.Trigger) {
            val ok = lifecycle.attempt(trigger, conditions, { with(feature) { install() } }) { t ->
                ModuleLog.e("feature ${feature.id} failed to install; hooks registered so far were removed", t)
            }
            if (ok) ModuleLog.i("feature ${feature.id} installed")
        }

        override fun hook(target: Executable, body: (HookCall) -> Any?) {
            val id = contractedId(target)
            val handle = xposed.hook(target).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                val observed = if (installed && !circuit.open && feature.isOn(settings)) observe(chain, id) else NOT_OBSERVED
                if (observed !== NOT_OBSERVED) observed else runHook(chain, { active }, ::onFailure, body)
            }
            InstallRecorder.record(handle::unhook)
        }

        override fun hookAlways(target: Executable, body: (HookCall) -> Any?) {
            val id = contractedId(target)
            val handle = xposed.hook(target).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                val observed = if (installed && !circuit.open) observe(chain, id) else NOT_OBSERVED
                if (observed !== NOT_OBSERVED) observed else runHook(chain, { installed && !circuit.open && !heldByContract }, ::onFailure, body)
            }
            InstallRecorder.record(handle::unhook)
        }

        override fun onApplication(block: (Application) -> Unit) = whenApplication { app ->
            try {
                block(app)
            } catch (t: Throwable) {
                fail("onApplication", t)
            }
        }

        override fun onActivityCreated(block: (Activity) -> Unit) {
            HostActivities.onCreated { a -> guarded("activity created") { block(a) } }
        }

        override fun onActivityResumed(block: (Activity) -> Unit) {
            HostActivities.onResumed { a -> guarded("activity resumed") { block(a) } }
        }

        override fun onActivityPaused(block: (Activity) -> Unit) {
            HostActivities.onPaused { a -> guarded("activity paused") { block(a) } }
        }

        override fun onActivityDestroyed(block: (Activity) -> Unit) {
            HostActivities.onDestroyed { a -> guarded("activity destroyed") { block(a) } }
        }

        private inline fun guarded(where: String, block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                fail(where, t)
            }
        }

        override fun fail(where: String, error: Throwable) = onFailure(error.also { ModuleLog.w("${feature.id} failed in $where", it) })

        private fun onFailure(error: Throwable) {
            ModuleLog.w("feature ${feature.id} hook failed", error)
            if (circuit.record(error)) {
                ModuleLog.e("feature ${feature.id} switched off for this session after repeated failures")
                scheduleReport()
                stopNotices.circuitTripped(feature.title)
            }
        }
    }
}

/** [FeatureRuntime] 里“这次调用不用观察”的标记（原方法可能返回 null，不能用 null 表示）。 */
private val NOT_OBSERVED = Any()

/** Process-wide services visible to features. */
interface RuntimeContext {
    val settings: HostSettings
    val symbols: HostSymbols
    val hostClassLoader: ClassLoader
    val application: Application?
    fun whenApplication(block: (Application) -> Unit)
}
