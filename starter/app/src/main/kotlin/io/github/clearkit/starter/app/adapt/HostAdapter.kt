package io.github.clearkit.starter.app.adapt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.clearkit.starter.BuildConfig
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.data.ForegroundCompat
import io.github.clearkit.starter.app.data.ModuleService
import io.github.clearkit.starter.hook.features.AllFeatures
import io.github.clearkit.starter.hook.runtime.HostVersion
import io.github.clearkit.starter.model.HostBuild
import io.github.clearkit.starter.symbols.SymbolCatalog
import io.github.wzgrx.dexadapt.SymbolTable
import io.github.wzgrx.dexadapt.dexkit.RecyclableScan
import io.github.wzgrx.dexadapt.resolve.DexDigest
import io.github.wzgrx.dexadapt.resolve.SymbolResolver
import io.github.wzgrx.dexadapt.runtime.AdaptPolicy
import io.github.wzgrx.dexadapt.runtime.AdaptPolicy.CheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 全自动多版本适配，每个渠道各管各的。每个版本的私有代码位置（符号表）有两个来源：模块内置表（电脑上生成），
 * 和本机适配表（在手机上用 DexKit 解析【宿主】安装包、按 core/symbols 同一套查询现场生成）。这里判断要不要本机适配、
 * 执行解析，并经 LSPosed 远程文件交给【宿主】。
 *
 * - **自动扫描的时机**：App 启动；模块更新；每 6 小时后台检查一次；【宿主】启动时发现没有可用的表（桥接 METHOD_HOST_ADAPT）。
 * - **判断规则**（DexAdapt 的 AdaptPolicy）：有内置表时核对安装包的 dex 摘要，一致就用内置表，不一致（同版本号的灰度包、渠道包）
 *   就本机生成一份覆盖它；没有内置表时，本机表不存在、模块更新改了查询、安装包变了都要重扫。摘要按安装包身份缓存。
 * - **手动操作**：全量扫描、自检（内置版本：本机重新解析并逐条比对）、清除本机适配数据、扫描报告。
 */
class HostAdapter(private val context: Context) {
    enum class Phase { IDLE, RUNNING, DONE, FAILED }
    enum class Mode { CHECK, FORCE, SELF_CHECK }

    data class Status(
        val packageName: String,
        val phase: Phase = Phase.IDLE,
        val installed: Boolean = false,
        val versionCode: Long = 0,
        val versionName: String = "",
        val apkSize: Long = 0,
        /** 模块里有这个版本的内置符号表。 */
        val bundled: Boolean = false,
        /** 内置表和安装包核对过：true 一致，false 不一致（用本机表覆盖），null 还没核对。 */
        val bundledMatches: Boolean? = null,
        val local: SymbolTable? = null,
        /** 本机表是这一版模块、对着这个安装包生成的。 */
        val localCurrent: Boolean = false,
        val progress: Int = 0,
        val total: Int = 0,
        val message: String = "",
        val check: CheckResult? = null,
        val lastScan: ScanRecord? = null,
    ) {
        val channel: HostBuild.Channel? get() = HostBuild.channelOf(packageName)

        /** 【宿主】现在用哪张表。 */
        val source: String
            get() = when {
                !installed -> "没有安装"
                local != null && localCurrent && (!bundled || bundledMatches == false) -> "本机适配"
                bundled -> "模块内置"
                local != null -> "本机适配（模块更新前生成，等待重扫）"
                else -> "还没适配"
            }

        /** 已经有能直接用的表（内置或这一版模块生成的本机表）。 */
        val ready: Boolean get() = installed && ((bundled && bundledMatches != false) || (local != null && localCurrent))
    }

    @Serializable
    data class ScanRecord(
        val at: Long,
        val versionCode: Long,
        val reason: String,
        val ok: Boolean,
        val found: Int = 0,
        val total: Int = 0,
        val seconds: Long = 0,
        val error: String = "",
    )

    @Serializable
    private data class DigestCache(val identity: String, val digest: String)

    private val packages = HostBuild.Channel.entries.map { it.packageName }

    private val _statuses = MutableStateFlow(packages.associateWith { Status(it) })
    /** 包名 → 状态。 */
    val statuses: StateFlow<Map<String, Status>> = _statuses

    private val _active = MutableStateFlow(Status(packages.first()))
    /** 正在扫描（或最近扫描过）的客户端，【宿主】里的适配卡片按它显示进度。 */
    val active: StateFlow<Status> = _active

    private fun update(pkg: String, transform: (Status) -> Status) {
        val next = transform(_statuses.value.getValue(pkg))
        _statuses.value = _statuses.value + (pkg to next)
        if (next.phase == Phase.RUNNING || _active.value.packageName == pkg) _active.value = next
    }

    private val dir get() = File(context.filesDir, "symbols").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    fun localTable(versionCode: Long): File = File(dir, "$versionCode.json")

    /** 先写临时文件再改名：扫描中途被杀也不会留下半截 JSON。 */
    private fun File.writeAtomically(text: String) {
        val tmp = File(parentFile, "$name.${android.os.Process.myPid()}.${android.os.Process.myTid()}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(this)) { tmp.delete(); error("写不进 $name") }
    }

    private fun lastScanFile(pkg: String) = File(dir, "last-scan-$pkg.json")
    private fun digestFile(pkg: String) = File(dir, "dex-digest-$pkg.json")

    private class Host(val packageName: String, val versionCode: Long, val versionName: String, val apk: File, val identity: String, val splits: List<File>) {
        /** 带代码（classes*.dex）的拆分包：和 base.apk 一起扫（现在的【宿主】只有 ABI / 屏幕密度拆分包）。 */
        val codeSplits: List<File> by lazy { splits.filter(::hasDex) }
    }

    private fun host(pkg: String): Host? = runCatching {
        val info = HostVersion.packageInfo(context.packageManager, pkg)
        val app = info.applicationInfo ?: return null
        val apk = File(app.sourceDir ?: return null)
        Host(pkg, info.longVersionCode, info.versionName.orEmpty(), apk, SymbolTable.apkIdentity(apk.path, apk.length(), info.lastUpdateTime), app.splitSourceDirs.orEmpty().map(::File))
    }.getOrNull()

    private fun readLocal(vc: Long): SymbolTable? =
        localTable(vc).takeIf { it.isFile }?.let { f -> runCatching { SymbolTable.fromJson(f.readText()) }.getOrNull() }?.takeIf { it.versionCode == vc }

    private fun readBundled(vc: Long): SymbolTable? = runCatching {
        HostAdapter::class.java.classLoader?.getResourceAsStream(SymbolTable.resourcePath(vc))?.bufferedReader()?.use { SymbolTable.fromJson(it.readText()) }
    }.getOrNull()

    /** 安装包的 dex 摘要（按安装包身份缓存）；[compute] 为假且没有缓存时返回 null。 */
    private fun digest(host: Host, compute: Boolean): String? {
        val file = digestFile(host.packageName)
        val cached = runCatching { json.decodeFromString(DigestCache.serializer(), file.readText()) }.getOrNull()
        if (cached?.identity == host.identity) return cached.digest
        if (!compute) return null
        val d = DexDigest.of(host.apk)
        runCatching { file.writeAtomically(json.encodeToString(DigestCache.serializer(), DigestCache(host.identity, d))) }
        return d
    }

    /** 读全部客户端的当前状态（不解析，也不读整个安装包）。 */
    fun refresh() = packages.forEach(::refresh)

    private fun refresh(pkg: String) {
        val h = host(pkg)
        if (h == null) {
            update(pkg) { Status(pkg, phase = it.phase, message = "没有安装") }
            return
        }
        val bundled = readBundled(h.versionCode)
        val local = readLocal(h.versionCode)
        val d = digest(h, compute = false)
        update(pkg) {
            it.copy(
                installed = true, versionCode = h.versionCode, versionName = h.versionName, apkSize = h.apk.length(),
                bundled = bundled != null,
                bundledMatches = if (bundled != null && d != null) bundled.dexSha256 == d else null,
                local = local, localCurrent = local?.catalog == SymbolCatalog.fingerprint && local.apk == h.identity,
                lastScan = runCatching { json.decodeFromString(ScanRecord.serializer(), lastScanFile(pkg).readText()) }.getOrNull(),
            )
        }
        syncRemote(h, local)
    }

    /**
     * 远程文件和本机表对齐（模块服务每次连上都做一次）：本机表是当前安装包、这一版查询生成的，远程文件丢了就补推；
     * 本机表没有或已过期，残留的远程表删掉，免得【宿主】继续用旧表。
     */
    private fun syncRemote(h: Host, local: SymbolTable?) {
        pendingSync[h.packageName] = h to local
        if (!syncRegistered) {
            syncRegistered = true
            ModuleService.onBind { s -> pendingSync.values.forEach { (host, table) -> syncRemote(s, host, table) }; cleanRemote(s) }
        } else {
            ModuleService.service?.let { s -> syncRemote(s, h, local) }
        }
    }

    private fun syncRemote(s: io.github.libxposed.service.XposedService, h: Host, local: SymbolTable?) {
        val name = SymbolTable.remoteFileName(h.versionCode)
        val present = runCatching { s.listRemoteFiles().toList() }.getOrDefault(emptyList())
        val current = local != null && local.catalog == SymbolCatalog.fingerprint && local.apk == h.identity
        when {
            current && name !in present -> publish(h.versionCode, localTable(h.versionCode).readText())
            !current && name in present && (local == null || local.apk != h.identity) -> runCatching { s.deleteRemoteFile(name) }
        }
    }

    /** 不属于任何装着的客户端当前版本的远程表（升级前的版本）清掉。 */
    private fun cleanRemote(s: io.github.libxposed.service.XposedService) {
        val keep = pendingSync.values.map { SymbolTable.remoteFileName(it.first.versionCode) }.toSet()
        runCatching { s.listRemoteFiles().filter { it.startsWith("symbols-") && it !in keep }.forEach { s.deleteRemoteFile(it) } }
    }

    private val pendingSync = java.util.concurrent.ConcurrentHashMap<String, Pair<Host, SymbolTable?>>()
    @Volatile private var syncRegistered = false

    /** 要不要本机扫描 [h]：返回原因，null 表示不用。会读安装包算摘要（有缓存），在后台线程调用。 */
    private fun evaluate(h: Host): String? {
        val bundled = readBundled(h.versionCode)
        val local = readLocal(h.versionCode)
        val label = HostBuild.channelOf(h.packageName)?.label.orEmpty()
        val name = if (bundled != null) "【宿主】$label ${h.versionName}" else "【宿主】$label ${h.versionName}（${h.versionCode}）"
        return when (val d = AdaptPolicy.decide(name, bundled, local, SymbolCatalog.fingerprint, h.identity) { digest(h, compute = true) }) {
            is AdaptPolicy.Decision.Scan -> d.reason
            is AdaptPolicy.Decision.Skip -> {
                if (d.clearLocal) clearLocal(h.versionCode)
                null
            }
        }
    }

    /** 本机表只留装着的客户端当前版本的（升级前的版本用不上了）。 */
    private fun pruneLocal() {
        val keep = packages.mapNotNull { host(it)?.versionCode }.toSet()
        dir.listFiles().orEmpty().filter { f -> f.name.removeSuffix(".json").toLongOrNull()?.let { it !in keep } == true }.forEach { it.delete() }
    }

    /** 自动检查：在后台判断，需要时扫描。[packageName] 为空时检查全部装着的客户端。 */
    fun check(trigger: String, packageName: String? = null) = enqueue(Mode.CHECK, trigger, packageName)

    /** 手动：全量扫描（不管判断结果）或自检。 */
    fun start(packageName: String, selfCheck: Boolean = false) =
        enqueue(if (selfCheck) Mode.SELF_CHECK else Mode.FORCE, if (selfCheck) "手动自检" else "手动全量扫描", packageName)

    private fun enqueue(mode: Mode, trigger: String, packageName: String?) {
        val request = OneTimeWorkRequestBuilder<Worker>()
            .setInputData(Data.Builder().putString(KEY_MODE, mode.name).putString(KEY_TRIGGER, trigger).putString(KEY_PACKAGE, packageName).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // 两个客户端共用一个唯一任务（不会同时开两个 DexKit）。手动操作替换排队中的自动检查；自动检查排在后面
        val policy = if (mode == Mode.CHECK) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, policy, request)
        if (mode != Mode.CHECK && packageName != null) update(packageName) { it.copy(phase = Phase.RUNNING, progress = 0, total = 0, message = "准备解析【宿主】…", check = null) }
    }

    /** 每 6 小时后台检查一次（电量低时不跑）；只把一次检查排进上面的唯一任务。 */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<Worker>(6, TimeUnit.HOURS)
            .setInputData(Data.Builder().putString(KEY_MODE, Mode.CHECK.name).putString(KEY_TRIGGER, "定时检查").putBoolean(KEY_DISPATCH, true).build())
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** 清除本机适配数据：[versionCode] 的本机表和远程文件；为空时全部清掉（连摘要缓存）。 */
    fun clearLocal(versionCode: Long? = null) {
        val targets = versionCode?.let { listOf(it) } ?: dir.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toLongOrNull() }
        targets.forEach { vc ->
            localTable(vc).delete()
            runCatching { ModuleService.service?.deleteRemoteFile(SymbolTable.remoteFileName(vc)) }
        }
        if (versionCode == null) packages.forEach { digestFile(it).delete() }
        refresh()
    }

    /** 把表写进 LSPosed 的远程文件（【宿主】进程启动最早时、运行中都能读到）。 */
    private fun publish(versionCode: Long, json: String): Boolean = runCatching {
        val service = ModuleService.service ?: return false
        service.openRemoteFile(SymbolTable.remoteFileName(versionCode)).use { pfd ->
            java.io.FileOutputStream(pfd.fileDescriptor).channel.use { ch ->
                ch.truncate(0)
                ch.write(java.nio.ByteBuffer.wrap(json.toByteArray()))
            }
        }
        true
    }.onFailure { Log.w(TAG, "publish $versionCode failed", it) }.getOrDefault(false)

    /** 扫描报告（文字）：设备、DexKit、每个客户端用的哪张表、缺失的符号和受影响的功能。 */
    fun report(): String {
        val fw = (ModuleService.state.value as? ModuleService.State.Active)?.let { "${it.framework} ${it.version}（API ${it.api}）" } ?: "未连接"
        val sb = StringBuilder("【模块】版本适配报告\n")
        sb.append("模块：").append(BuildConfig.VERSION_NAME).append("（").append(BuildConfig.VERSION_CODE).append("），符号定义 ")
            .append(SymbolCatalog.fingerprint).append("，共 ").append(SymbolCatalog.all.size).append(" 个符号\n")
        sb.append("框架：").append(fw).append("；DexKit ").append(DEXKIT_VERSION).append('\n')
        sb.append("设备：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("，Android ").append(Build.VERSION.RELEASE)
            .append("（SDK ").append(Build.VERSION.SDK_INT).append("）\n")
        for (st in _statuses.value.values) {
            sb.append('\n').append("【宿主】").append(st.channel?.label.orEmpty()).append("：")
            if (!st.installed) { sb.append("没有安装\n"); continue }
            sb.append(st.versionName).append("（").append(st.versionCode).append("），安装包 ").append(st.apkSize / 1048576).append(" MB\n")
            sb.append("  使用的适配：").append(st.source).append('\n')
            st.lastScan?.let { r ->
                sb.append("  上次扫描：").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(r.at)))
                    .append("，").append(r.reason).append("，").append(if (r.ok) "找到 ${r.found}/${r.total}，用时 ${r.seconds} 秒" else "失败：${r.error}").append('\n')
            }
            st.local?.takeIf { it.missing.isNotEmpty() }?.let { t ->
                sb.append("  缺失符号 ").append(t.missing.size).append(" 个：\n")
                t.missing.forEach { (id, why) -> sb.append("    ").append(id).append("：").append(why).append('\n') }
                sb.append("  受影响的功能：").append(affectedFeatures(t.missing.keys).joinToString("、").ifEmpty { "无" }).append('\n')
            }
        }
        return sb.toString()
    }

    /** 用到这些符号的功能名（缺了这些符号，功能就不可用）。 */
    fun affectedFeatures(missing: Set<String>): List<String> =
        AllFeatures.list.filter { f -> f.requires.any { it.id in missing } }.map { it.title }

    /** 同一时间只跑一次解析。 */
    private val scanLock = Any()

    private fun run(mode: Mode, trigger: String, packageName: String?, onProgress: (String, Int, Int) -> Unit) = synchronized(scanLock) {
        pruneLocal()
        val targets = packageName?.let { listOf(it) } ?: packages
        targets.forEach { pkg -> runOne(mode, trigger, pkg) { done, total -> onProgress(pkg, done, total) } }
    }

    private fun runOne(mode: Mode, trigger: String, pkg: String, onProgress: (Int, Int) -> Unit) {
        val h = host(pkg) ?: run {
            update(pkg) { Status(pkg, message = "没有安装") }
            return
        }
        if (!h.apk.canRead()) error("读不了【宿主】的安装包（${h.apk.path}）")
        val reason = when (mode) {
            Mode.CHECK -> evaluate(h) ?: run {
                refresh(pkg)
                update(pkg) { it.copy(phase = Phase.IDLE) }
                return
            }
            Mode.FORCE, Mode.SELF_CHECK -> trigger
        }
        val build = HostBuild.of(pkg, h.versionCode)
        val bundled = readBundled(h.versionCode)
        if (mode == Mode.SELF_CHECK && (build == null || bundled == null)) error("这个版本没有内置符号表，不能自检")
        update(pkg) { it.copy(phase = Phase.RUNNING, progress = 0, total = 0, message = "$reason：正在解析…", check = null) }
        val started = System.currentTimeMillis()
        System.loadLibrary("dexkit")
        val digest = digest(h, compute = true)!!
        if (h.codeSplits.isNotEmpty()) Log.i(TAG, "scanning ${h.codeSplits.size} code split(s) too: ${h.codeSplits.joinToString { it.name }}")
        val table = RecyclableScan.run("clearstarter-$pkg", listOf(h.apk) + h.codeSplits, digest, { msg, e -> Log.w(TAG, msg, e) }) { source ->
            val resolver = if (mode == Mode.SELF_CHECK && build != null) {
                SymbolResolver(source, h.versionCode, pinFor = { it.pins[build.name] }, skip = { build.name in it.absentIn }, log = { Log.i(TAG, it) })
            } else {
                // 本机适配：按版本钉死的位置只对已知版本有效，全部按查询找；“某版本没有”的标注也不适用
                SymbolResolver(source, h.versionCode, log = { Log.i(TAG, it) })
            }
            val resolved = resolver.table(SymbolCatalog.all, onProgress)
            // 分级回退：解析不到的符号推测位置，只给有契约的 Hook 点观察后使用
            if (mode == Mode.SELF_CHECK) resolved else resolver.withGuesses(resolved, SymbolCatalog.all) { id -> knownLocations(id) }
        }
        val seconds = (System.currentTimeMillis() - started) / 1000
        Log.i(TAG, "resolved $pkg ${h.versionCode} ($reason): ${table.entries.size} found, ${table.missing.size} missing in ${seconds}s")
        table.missing.forEach { (id, why) -> Log.i(TAG, "missing $id: $why") }
        val total = table.entries.size + table.missing.size
        if (mode == Mode.SELF_CHECK) {
            val result = AdaptPolicy.compare(bundled!!, table, seconds)
            result.different.forEach { Log.w(TAG, "check different $it: bundled=${bundled.entries[it]} local=${table.entries[it]}") }
            record(pkg, ScanRecord(System.currentTimeMillis(), h.versionCode, reason, true, table.entries.size, total, seconds))
            refresh(pkg)
            update(pkg) { it.copy(phase = Phase.DONE, check = result, message = "自检完成：${result.same} 个一致") }
            return
        }
        val text = AdaptPolicy.stamp(table, SymbolCatalog.fingerprint, BuildConfig.VERSION_NAME, h.identity, seconds, reason).toJson()
        localTable(h.versionCode).writeAtomically(text)
        val pushed = publish(h.versionCode, text)
        record(pkg, ScanRecord(System.currentTimeMillis(), h.versionCode, reason, true, table.entries.size, total, seconds))
        refresh(pkg)
        val lost = affectedFeatures(table.missing.keys)
        update(pkg) {
            it.copy(
                phase = Phase.DONE,
                message = "找到 ${table.entries.size} / $total 处，用时 $seconds 秒" +
                    (if (lost.isNotEmpty()) "；暂不可用：${lost.joinToString("、")}" else "") +
                    if (pushed) "。【宿主】开着时会自动启用，没开的下次打开生效" else "。模块服务没连上，在 LSPosed 管理器里确认模块已启用后再试",
            )
        }
    }

    /** 符号在各内置版本里的位置（分级回退的候选）。 */
    private val knownTables by lazy { HostBuild.entries.mapNotNull { readBundled(it.versionCode) } }

    private fun knownLocations(id: String): List<io.github.wzgrx.dexadapt.Resolved> = knownTables.mapNotNull { it.entries[id] }

    private fun record(pkg: String, r: ScanRecord) {
        runCatching { lastScanFile(pkg).writeAtomically(json.encodeToString(ScanRecord.serializer(), r)) }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val adapter = ModuleApp.instance.hostAdapter
            if (inputData.getBoolean(KEY_DISPATCH, false)) {
                adapter.check(inputData.getString(KEY_TRIGGER) ?: "定时检查")
                return Result.success()
            }
            val mode = runCatching { Mode.valueOf(inputData.getString(KEY_MODE).orEmpty()) }.getOrDefault(Mode.CHECK)
            val trigger = inputData.getString(KEY_TRIGGER).orEmpty()
            val pkg = inputData.getString(KEY_PACKAGE)
            // 自动检查多数时候不用扫描：真要解析时再进前台（显示通知）
            if (mode != Mode.CHECK) ForegroundCompat.promote(this@Worker, info(0, 0), "适配扫描")
            return runCatching {
                var foreground = mode != Mode.CHECK
                adapter.run(mode, trigger, pkg) { p, done, total ->
                    if (isStopped) throw kotlinx.coroutines.CancellationException("扫描已取消")
                    if (!foreground) { foreground = true; ForegroundCompat.promoteAsync(this@Worker, info(done, total), "适配扫描") }
                    adapter.update(p) { it.copy(progress = done, total = total, message = "正在解析 $done / $total") }
                    if (done % 40 == 0) ForegroundCompat.promoteAsync(this@Worker, info(done, total), "适配扫描")
                }
                Result.success()
            }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "adapt failed ($trigger)", e)
                val failed = pkg ?: adapter._active.value.packageName
                adapter.record(failed, ScanRecord(System.currentTimeMillis(), adapter._statuses.value[failed]?.versionCode ?: 0, trigger, false, error = e.message ?: e.javaClass.simpleName))
                adapter.update(failed) { it.copy(phase = Phase.FAILED, message = "失败：${e.message ?: e.javaClass.simpleName}") }
                Result.failure()
            }
        }

        override suspend fun getForegroundInfo(): ForegroundInfo = info(0, 0)

        @android.annotation.SuppressLint("InlinedApi")
        private fun info(done: Int, total: Int): ForegroundInfo {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "适配进度", NotificationManager.IMPORTANCE_LOW))
            val n = Notification.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在适配这个版本的【宿主】")
                .setContentText(if (total > 0) "解析 $done / $total" else "正在读取【宿主】安装包…")
                .setProgress(total.coerceAtLeast(1), done, total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            return ForegroundInfo(NOTICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
    }

    /** 模块（本 App）更新后系统发 MY_PACKAGE_REPLACED：唤醒 App，检查一次（查询变了时本机表要重扫）。 */
    class UpdateReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            if (intent.action == android.content.Intent.ACTION_MY_PACKAGE_REPLACED) ModuleApp.instance.hostAdapter.check("模块更新")
        }
    }

    companion object {
        private const val TAG = "BcAdapt"
        private const val WORK = "host-adapt"
        private const val PERIODIC = "host-adapt-periodic"
        private const val KEY_MODE = "mode"
        private const val KEY_TRIGGER = "trigger"
        private const val KEY_PACKAGE = "package"
        private const val KEY_DISPATCH = "dispatch"
        private const val CHANNEL = "adapt"
        private const val NOTICE_ID = 1031
        val DEXKIT_VERSION: String = BuildConfig.DEXKIT_VERSION

        /** [apk] 里有没有 classes*.dex（只读 zip 目录）。 */
        fun hasDex(apk: File): Boolean = runCatching {
            java.util.zip.ZipFile(apk).use { zip -> zip.entries().asSequence().any { it.name.matches(Regex("classes\\d*\\.dex")) } }
        }.getOrDefault(false)
    }
}
