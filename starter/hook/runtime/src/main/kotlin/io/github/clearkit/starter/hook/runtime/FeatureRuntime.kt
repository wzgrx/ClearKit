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
    private val dataDir: File,
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
    // 免 Root 加载没有 LSPosed 远程文件：本机表经桥接从 App 取来后存在【宿主】自己的目录里（[cachedTable]），下次启动直接用。
    private val choice = hostVersionCode?.let { vc ->
        io.github.wzgrx.dexadapt.runtime.AdaptPolicy.choose(HostSymbols.loadRemote(xposed, vc) ?: cachedTable(vc), HostSymbols.loadTable(vc), SymbolCatalog.fingerprint, hostApkIdentity)
    }
    override val symbols = HostSymbols(choice?.table, hostClassLoader).also {
        choice?.table?.let { t ->
            val what = if (choice.source == io.github.wzgrx.dexadapt.runtime.AdaptPolicy.Source.BUNDLED) "bundled" else "locally adapted (${choice.source.name.lowercase()})"
            ModuleLog.i("using $what symbol table for ${t.versionCode}: ${t.entries.size} found, ${t.missing.size} missing")
        }
    }

    /** 这个渠道本来就没有的符号（只有另一个渠道才有的入口）：找不到不算缺失，用到它们的功能算“不支持”而不是“用不了”。 */
    private val channelAbsent: Set<String> = SymbolCatalog.channelAbsentFor(hostPackage)

    private fun expectedAbsent(): Set<String> = channelAbsent + symbols.table?.absent.orEmpty()

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
        // 新版本第一次打开：先请 App 适配、最多等几秒，等到了就一次装全（不用热适配，也不用重开）
        if (needsAdaptation && hostVersionCode != null && settings.readable && settings.masterEnabled) earlyAdaptation(hostVersionCode)
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
     * 然后看 LSPosed 远程文件（免 Root 时经桥接问 App），新表到了就当场换上：之前装不上的装上，装上了但缺入口的撤下重装
     * （热适配）。只在启动时起作用的功能（开屏、底栏……）列进“重开后生效”，适配卡片上可以一键重开。
     */
    /** 这次运行里要热适配（结果卡片会说清楚，启动提示就不再说“已适配”，留到下次冷启动）。 */
    @Volatile
    private var adaptingThisRun = false

    private fun requestAdaptation(app: Application, versionCode: Long) {
        adaptingThisRun = true
        if (settings[Settings.Module.adaptCard]) main.postDelayed({ AdaptationEvents.listener?.onAdapting(hostVersionName) }, 1200)
        main.postDelayed({
            BridgeClient.post(app, Bridge.METHOD_HOST_ADAPT, arg = "$hostPackage:$versionCode") { reply ->
                ModuleLog.i("adaptation requested for $versionCode: ${reply?.getString(Bridge.KEY_JSON) ?: "app unreachable"}")
            }
            Thread({ waitForAdaptation(app, versionCode) }, "bc-adapt-wait").apply { isDaemon = true }.start()
        }, 600)
    }

    /**
     * 已经有可用的表（内置表、这一版模块生成的本机表）：每个“模块版本 × 【宿主】版本”第一次启动时在后台逐个核对符号
     * （不显示进度，不打扰）；只有核对出问题（有功能用不了）时才弹结果卡片。以后不再核对。
     * 核对在主线程分批做（找类时可能触发【宿主】的类初始化，不放到后台线程）。
     */
    private fun verifyOnce(app: Application, versionCode: Long) {
        val flag = verifiedFlag(app, versionCode)
        if (flag.exists()) return
        val all = SymbolCatalog.all
        if (all.isEmpty()) return
        var index = 0
        fun step() {
            val end = minOf(index + 8, all.size)
            while (index < end) { runCatching { symbols.available(all[index]) }; index++ }
            if (index < all.size) { main.postDelayed(::step, 30); return }
            runCatching { flag.parentFile?.mkdirs(); flag.writeText("1") }
            val report = adaptReport()
            ModuleLog.i("verified ${report.found} / ${report.total} symbols; ${report.unavailable.size} features unavailable")
            if (report.tier != io.github.clearkit.starter.model.adapt.AdaptReport.Tier.OK) AdaptationEvents.listener?.onResult(report, settings[Settings.Module.adaptCard])
        }
        main.postDelayed(::step, 2500)
    }

    private fun verifiedFlag(app: Application, versionCode: Long) =
        File(app.filesDir, "module/adapt_verified_" + "${moduleVersion}_$versionCode".replace(Regex("[^A-Za-z0-9._-]"), "_"))

    private fun waitForAdaptation(app: Application, versionCode: Long) {
        val deadline = android.os.SystemClock.elapsedRealtime() + ADAPT_WAIT_MS
        val card = settings[Settings.Module.adaptCard]
        var tick = 0
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            // 卡片显示时每半秒问一次 App 的解析进度；新表每 1.5 秒看一次
            Thread.sleep(if (card) 500 else 1500)
            if (card) adaptProgress(app)?.let { p -> main.post { AdaptationEvents.listener?.onProgress(p.done, p.total) } }
            if (card && ++tick % 3 != 0) continue
            val t = HostSymbols.loadRemote(xposed, versionCode) ?: if (!settings.remote) fetchTable(app, versionCode) else null
            if (t == null || !fresh(t) || t == symbols.table) continue
            main.post {
                val report = applyHotTable(t)
                runCatching { verifiedFlag(app, versionCode).let { it.parentFile?.mkdirs(); it.writeText("1") } }
                scheduleReport()
                val listener = AdaptationEvents.listener
                if (listener != null) listener.onResult(report, card)
                else toast(app, "【模块】已自动适配【宿主】 $hostVersionName，${report.active} 项已生效")
            }
            return
        }
        ModuleLog.w("adaptation for $versionCode did not finish in time")
        val detail = timeoutDetail()
        main.post { AdaptationEvents.listener?.onTimeout(hostVersionName, detail) }
    }

    /** 等不到新表时卡片上的说明：现在哪些照常、哪些暂停，为什么没好。 */
    private fun timeoutDetail(): String {
        val on = entries.values.filter { it.feature.isOn(settings) }
        val active = on.count { it.installed }
        val paused = on.size - active
        val why = if (android.os.Process.myUid() / 100_000 != 0) "这是分身 / 工作资料里的【宿主】，要在同一个空间里装【模块】才能适配"
        else "【模块】没能在后台启动（可能被省电或自启动限制拦住了）"
        return "$why。现在 $active 项照常生效，$paused 项等适配好后生效。"
    }

    /**
     * 新版本第一次打开时先等一会儿（最多 [EARLY_WAIT_MS]）：请 App 本机适配，表到了就在装功能之前换上，一次装全、不用重开。
     * 这时还没有 Application，借系统 Context 建一个【宿主】自己的 Context 调桥接。每个版本最多等两次（App 起不来时不每次都慢）。
     */
    private fun earlyAdaptation(versionCode: Long) {
        val flag = File(dataDir, "files/module/adapt_early_$versionCode")
        val tries = runCatching { flag.readText().trim().toInt() }.getOrDefault(0)
        if (tries >= 2) return
        runCatching { flag.parentFile?.mkdirs(); flag.writeText("${tries + 1}") }
        val context = packageContext() ?: return
        val started = android.os.SystemClock.elapsedRealtime()
        // 【宿主】或模块刚升级、被强行停止过时，系统收回了“【宿主】能看见模块 App”的授权，这时还没有页面可以握手：
        // 请求不通就马上放弃（不白等），交给热适配；通了才等表
        val reply = java.util.concurrent.atomic.AtomicReference<android.os.Bundle?>()
        val answered = java.util.concurrent.CountDownLatch(1)
        Thread({
            reply.set(runCatching { BridgeClient.callDirect(context, Bridge.METHOD_HOST_ADAPT, arg = "$hostPackage:$versionCode") }.getOrNull())
            answered.countDown()
        }, "bc-adapt-early").apply { isDaemon = true }.start()
        answered.await(EARLY_REQUEST_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        val ok = reply.get()?.getBoolean(Bridge.KEY_OK, false) == true
        ModuleLog.i("early adaptation requested for $versionCode: ${if (ok) "accepted" else "app unreachable"} after ${android.os.SystemClock.elapsedRealtime() - started} ms")
        if (!ok) return
        while (android.os.SystemClock.elapsedRealtime() - started < EARLY_WAIT_MS) {
            Thread.sleep(150)
            val t = HostSymbols.loadRemote(xposed, versionCode) ?: if (!settings.remote) fetchTable(context, versionCode, direct = true) else null
            if (t != null && fresh(t)) {
                symbols.replace(t)
                contracts = monitorFor(t)
                ModuleLog.i("early adaptation: table for $versionCode ready after ${android.os.SystemClock.elapsedRealtime() - started} ms (${t.entries.size} found, ${t.missing.size} missing)")
                runCatching { flag.delete() }
                return
            }
        }
        ModuleLog.i("early adaptation: no table for $versionCode within $EARLY_WAIT_MS ms; continuing with hot adaptation")
    }

    /** 【宿主】自己的 Context（Application 建好之前用）：系统 Context 派生，调用方身份就是【宿主】。 */
    @android.annotation.SuppressLint("PrivateApi")
    private fun packageContext(): android.content.Context? = runCatching {
        val at = Class.forName("android.app.ActivityThread")
        val system = at.getMethod("getSystemContext").invoke(at.getMethod("currentActivityThread").invoke(null)) as android.content.Context
        system.createPackageContext(hostPackage, 0)
    }.onFailure { ModuleLog.w("early adaptation: no context", it) }.getOrNull()

    /** 免 Root 加载：经桥接问 App 要这一版的本机表，拿到后存进【宿主】自己的目录（下次启动直接用）。 */
    private fun fetchTable(context: android.content.Context, versionCode: Long, direct: Boolean = false): io.github.wzgrx.dexadapt.SymbolTable? = runCatching {
        val arg = "$hostPackage:$versionCode"
        val reply = if (direct) BridgeClient.callDirect(context, Bridge.METHOD_SYMBOL_TABLE, arg = arg) else BridgeClient.call(context, Bridge.METHOD_SYMBOL_TABLE, arg = arg)
        val json = reply?.getString(Bridge.KEY_JSON) ?: return@runCatching null
        val t = io.github.wzgrx.dexadapt.SymbolTable.fromJson(json).takeIf { it.versionCode == versionCode } ?: return@runCatching null
        File(tableCacheDir, "$versionCode.json").let { f -> f.parentFile?.mkdirs(); f.writeText(json) }
        t
    }.onFailure { ModuleLog.w("table $versionCode not fetched from the app", it) }.getOrNull()

    private val tableCacheDir get() = File(dataDir, "files/module/symbols")

    /** 免 Root 时上次从 App 取来的表。 */
    private fun cachedTable(versionCode: Long): io.github.wzgrx.dexadapt.SymbolTable? = runCatching {
        File(File(dataDir, "files/module/symbols"), "$versionCode.json").takeIf { it.isFile }
            ?.readText()?.let(io.github.wzgrx.dexadapt.SymbolTable::fromJson)?.takeIf { it.versionCode == versionCode }
    }.getOrNull()

    /**
     * 热适配：换表，装上之前装不上的，撤下重装装上了但缺入口、现在找得到了的；只在启动时起作用的（或它依赖的功能换表后
     * 才装好的）记成“重开后生效”。返回这次的报告。
     */
    private fun applyHotTable(t: io.github.wzgrx.dexadapt.SymbolTable): io.github.clearkit.starter.model.adapt.AdaptReport {
        symbols.replace(t)
        contracts = monitorFor(t)
        val late = LinkedHashSet<Feature>()
        entries.values.filter { it.installed && it.missesNowFound() }.forEach { e ->
            if (e.reinstall()) late += e.feature
        }
        val before = entries.values.filter { it.installed }.map { it.feature }.toSet()
        entries.values.forEach { it.tryInstall(FeatureInstall.Trigger.SYMBOLS) }
        late += entries.values.filter { it.installed && it.feature !in before }.map { it.feature }
        entries.values.forEach { e ->
            if (e.installed && e.feature.startupOnly && (e.feature in late || e.feature.dependsOn in late)) e.restartPending = true
        }
        refreshNeeded = late.any { f -> entries.values.any { it.feature.dependsOn === f } }
        val report = adaptReport()
        ModuleLog.i(
            "hot adaptation: table for ${t.versionCode} applied, ${late.size} features (re)installed: ${late.joinToString { it.id }}; " +
                "restart needed for ${report.restart.size}, unavailable ${report.unavailable.size}",
        )
        return report
    }

    /** 这次运行的适配报告（卡片、启动提示、App 都用它）。 */
    fun adaptReport(): io.github.clearkit.starter.model.adapt.AdaptReport {
        val t = symbols.table
        val expected = expectedAbsent()
        val total = t?.let { it.entries.size + it.missing.keys.count { id -> id !in expected } } ?: SymbolCatalog.all.count { it.id !in expected }
        val statuses = entries.values.map { it to it.status() }
        val unavailable = statuses.mapNotNull { (e, st) -> e.issue(st)?.let { io.github.clearkit.starter.model.adapt.AdaptReport.Issue(e.feature.title, it) } }
        return io.github.clearkit.starter.model.adapt.AdaptReport(
            hostVersionName = hostVersionName,
            found = t?.entries?.size ?: 0,
            total = total,
            active = statuses.count { it.second.state == State.ACTIVE },
            restart = statuses.filter { it.second.state == State.RESTART_PENDING }.map { it.first.feature.title },
            unavailable = unavailable,
            observing = statuses.count { it.second.state == State.OBSERVING },
            local = t?.generator?.isNotEmpty() == true,
            refreshNeeded = refreshNeeded,
        )
    }

    /** 改接口数据的功能是换表后才装好的（这次运行里已经显示的内容要刷新）。 */
    @Volatile
    private var refreshNeeded = false

    /** 卡片上的“重试”：再请 App 适配一次、再等。 */
    fun retryAdaptation() {
        val app = application ?: return
        val vc = hostVersionCode ?: return
        if (!needsAdaptation) return
        requestAdaptation(app, vc)
    }

    /** 重开后生效的功能还有几项（“稍后”收成的小胶囊上的数字）。 */
    val restartPendingCount: Int get() = entries.values.count { it.restartPending && it.installed && it.feature.isOn(settings) }

    /**
     * 这个【宿主】版本第一次冷启动（提示里说“已适配 x.y.z · 全部正常”）：每个“模块版本 × 【宿主】版本”只返回一次 true。
     * 热适配那次运行里已经弹过结果卡片，不算。
     */
    fun consumeFirstForVersion(): Boolean {
        if (adaptingThisRun) return false
        val app = application ?: return false
        val vc = hostVersionCode ?: return false
        val flag = File(app.filesDir, "module/announced_" + "${moduleVersion}_$vc".replace(Regex("[^A-Za-z0-9._-]"), "_"))
        if (flag.exists()) return false
        runCatching { flag.parentFile?.mkdirs(); flag.writeText("1") }
        return true
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
        report = runCatching { adaptReport() }.getOrNull(),
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

        /** 新版本第一次打开时，装功能之前最多等表这么久（这次 9.16.0 的本机适配用了 2 秒）。 */
        private const val EARLY_WAIT_MS = 3_000L

        /** 请 App 适配这一步最多等这么久回话（App 冷启动一般几百毫秒）；不通就不等表了。 */
        private const val EARLY_REQUEST_MS = 1_200L

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

        /** 换表后才装好、又只在启动时起作用：重开【宿主】后生效。 */
        @Volatile
        var restartPending = false

        /** 装好时缺的可选入口，换了表以后有找得到的（该撤下重装了）。 */
        fun missesNowFound(): Boolean {
            val expected = expectedAbsent()
            return lifecycle.misses.any { id -> id !in expected && SymbolCatalog.all.firstOrNull { it.id == id }?.let { s -> symbols.problem(s) == null } == true }
        }

        /** 撤下重装（换表后）；返回是否装上了。 */
        fun reinstall(): Boolean {
            lifecycle.uninstall()
            tryInstall(FeatureInstall.Trigger.SYMBOLS)
            return installed
        }

        /** 用户开着、这一版却用不了（或只有部分效果）的原因，给卡片和 App 看；没问题、本来就没有、没开的返回 null。 */
        fun issue(st: FeatureStatus): String? {
            if (!feature.isOn(settings) || maintenanceOff) return null
            return when (st.state) {
                State.UNAVAILABLE -> if (lifecycle.expected || (installed && violation == null)) null
                    else if (violation != null) "这一版的调用方式变了，已自动停用"
                    else "这一版找不到它要用的地方"
                State.FAILED -> if (circuit.open) "反复出错，这次运行里已自动停用" else "装不上（${lifecycle.detail?.substringBefore(':')?.take(40) ?: "未知原因"}）"
                State.CRASH_DISABLED -> "上次导致【宿主】崩溃，已自动停用"
                State.ACTIVE, State.OBSERVING -> {
                    val expected = expectedAbsent()
                    val missing = lifecycle.misses.count { id -> id !in expected && SymbolCatalog.all.firstOrNull { it.id == id }?.let { s -> symbols.problem(s) != null } == true }
                    if (missing > 0) "部分效果没有：这一版找不到 $missing 处" else null
                }
                else -> null
            }
        }

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
                installed && restartPending -> State.RESTART_PENDING
                installed -> State.ACTIVE
                else -> state
            }
            val why = when (shown) {
                State.FAILED -> if (circuit.open) circuit.lastError?.let { "${it.javaClass.simpleName}: ${it.message}" } else lifecycle.detail
                State.UNAVAILABLE -> bad ?: lifecycle.detail
                State.OBSERVING -> verdicts().let { v -> "新版本先观察：${v.count { it.second == ContractMonitor.Verdict.PASSED }} / ${v.size} 个 Hook 点已验证" }
                State.RESTART_PENDING -> "启动时还没有适配好，重开【宿主】后生效"
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
            override fun missingIsExpected(): Boolean {
                val expected = expectedAbsent()
                return feature.requires.filter { symbols.problem(it) != null }.let { it.isNotEmpty() && it.all { s -> s.id in expected } }
            }
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

        override fun deoptimize(target: Executable): Boolean =
            runCatching { xposed.deoptimize(target) }.onFailure { ModuleLog.w("deoptimize failed: $target", it) }.getOrDefault(false)

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
