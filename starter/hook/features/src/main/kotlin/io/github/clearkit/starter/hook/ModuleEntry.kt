package io.github.clearkit.starter.hook

import android.content.Context
import io.github.clearkit.starter.hook.features.AllFeatures
import io.github.clearkit.starter.hook.features.entries.ModuleLauncher
import io.github.clearkit.starter.hook.runtime.AdaptationEvents
import io.github.clearkit.starter.hook.runtime.FeatureRuntime
import io.github.clearkit.starter.hook.runtime.FeatureStopNotices
import io.github.clearkit.starter.hook.runtime.HostActivities
import io.github.clearkit.starter.hook.runtime.HostRestart
import io.github.clearkit.starter.hook.runtime.HostVersion
import io.github.clearkit.starter.hook.runtime.ModuleLog
import io.github.clearkit.starter.hook.ui.AdaptCard
import io.github.clearkit.starter.hook.ui.HostToast
import io.github.clearkit.starter.model.HostBuild
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.io.File

/** libxposed 入口，登记在 META-INF/xposed/java_init.list。 */
class ModuleEntry : XposedModule() {
    private var processName = ""
    private var runtime: FeatureRuntime? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        ModuleLog.bind(this)
        processName = param.processName
    }

    override fun onPackageReady(param: PackageReadyParam) {
        val pkg = param.packageName
        // 只在主进程：推送、下载、WebView 等子进程没有要改的界面。
        if (HostBuild.channelOf(pkg) == null || processName != pkg || runtime != null) return
        val versionCode = HostVersion.read(pkg)
        val build = versionCode?.let { HostBuild.of(pkg, it) }
        val moduleVersion = moduleVersionName()
        ModuleLog.i(
            "loaded $moduleVersion in $processName api=$apiVersion framework=$frameworkName $frameworkVersion " +
                "host=$pkg ${versionCode ?: "?"} ${build?.label ?: "未内置的版本"}",
        )
        // 功能被自动停用时的提醒（熔断、崩溃保护）：同一张顶部卡片，正忙时排队；“查看”打开 App 的功能状态。
        FeatureStopNotices.presenter = { activity, message, detail ->
            AdaptCard.notice(activity, message, detail, "查看") { ModuleLauncher.open(activity, "status") }
        }
        // 适配卡片：要重新适配时出现在当前页面顶部，换页面时跟过去；结果按报告分三档，要重开的可以一键重开。
        AdaptCard.actions = object : AdaptCard.Actions {
            override fun restart(activity: android.app.Activity) = HostRestart.restart(activity) { AdaptCard.restartFailed(it) }
            override fun open(activity: android.app.Activity, route: String) = ModuleLauncher.open(activity, route)
            override fun retry() { runtime?.retryAdaptation() }
            override fun restartPending(): Int = runtime?.restartPendingCount ?: 0
        }
        AdaptationEvents.listener = object : AdaptationEvents.Listener {
            private fun page() = HostActivities.foreground() ?: HostActivities.resumed()
            override fun onAdapting(versionName: String) = AdaptCard.adapting(page(), versionName)
            override fun onProgress(done: Int, total: Int) = AdaptCard.progress(done, total)
            override fun onResult(report: io.github.clearkit.starter.model.adapt.AdaptReport, card: Boolean) = AdaptCard.result(page(), report, card)
            override fun onTimeout(versionName: String, detail: String) = AdaptCard.timeout(page(), versionName, detail)
        }
        HostActivities.onResumed { AdaptCard.moveTo(it) }
        runCatching {
            FeatureRuntime(
                xposed = this,
                features = AllFeatures.list,
                hostClassLoader = param.classLoader,
                dataDir = File(param.applicationInfo.dataDir),
                hostPackage = pkg,
                hostVersionCode = versionCode,
                hostVersionName = build?.versionName ?: HostVersion.readName(pkg) ?: versionCode?.toString().orEmpty(),
                moduleVersion = moduleVersion,
                hostApkIdentity = HostVersion.apkIdentity(pkg),
            ).also { runtime = it }.start()
        }.onFailure { ModuleLog.e("runtime failed to start; the host runs unmodified", it) }
    }

    // 模块在【宿主】进程里加载时还没有自己的 Context，只能借 ActivityThread 的系统 Context 读模块 APK 的版本名；
    // 反射失败时显示“?”，不影响功能（lint PrivateApi）。
    @android.annotation.SuppressLint("PrivateApi")
    private fun moduleVersionName(): String = runCatching {
        val context = Class.forName("android.app.ActivityThread").let { at ->
            at.getMethod("getSystemContext").invoke(at.getMethod("currentActivityThread").invoke(null)) as Context
        }
        HostVersion.archiveInfo(context.packageManager, moduleApplicationInfo.sourceDir)?.versionName
    }.getOrNull() ?: "?"
}
