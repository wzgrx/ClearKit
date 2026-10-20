package io.github.clearkit.starter.hook.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

object HostVersion {
    /**
     * Reads the host's versionCode before its Application exists, through the system context of
     * the current ActivityThread (available as soon as the process is bound).
     *
     * 宿主 Application 创建前没有公开 API 能拿到 Context，只能反射 ActivityThread；失败时返回 null，
     * 按“未知版本”处理（lint PrivateApi）。
     */
    @SuppressLint("PrivateApi")
    fun read(packageName: String): Long? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("currentActivityThread").invoke(null)
        val context = activityThread.getMethod("getSystemContext").invoke(thread) as Context
        packageInfo(context.packageManager, packageName).longVersionCode
    }.onFailure { ModuleLog.w("host version unreadable", it) }.getOrNull()

    /** 宿主的版本名（“9.50.0”）；没内置适配的新版本也能在提示和 App 首页显示可读的版本，读不到时 null。 */
    @SuppressLint("PrivateApi")
    fun readName(packageName: String): String? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("currentActivityThread").invoke(null)
        val context = activityThread.getMethod("getSystemContext").invoke(thread) as Context
        packageInfo(context.packageManager, packageName).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** 宿主安装包身份（和 App 生成本机符号表时记下的同一格式，见 SymbolTable.apkIdentity）；读不到时 null。 */
    @SuppressLint("PrivateApi")
    fun apkIdentity(packageName: String): String? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("currentActivityThread").invoke(null)
        val context = activityThread.getMethod("getSystemContext").invoke(thread) as Context
        val info = packageInfo(context.packageManager, packageName)
        val apk = java.io.File(info.applicationInfo?.sourceDir ?: return@runCatching null)
        io.github.wzgrx.dexadapt.SymbolTable.apkIdentity(apk.path, apk.length(), info.lastUpdateTime)
    }.onFailure { ModuleLog.w("host apk identity unreadable", it) }.getOrNull()

    /** PackageInfoFlags 重载是 Android 13 才有的；之前的系统用 int 版本（minSdk 28），否则会 NoSuchMethodError，模块认不出宿主。 */
    fun packageInfo(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(packageName, 0)

    fun archiveInfo(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(path, 0)
}
