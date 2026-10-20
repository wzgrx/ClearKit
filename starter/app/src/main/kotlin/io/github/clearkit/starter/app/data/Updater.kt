package io.github.clearkit.starter.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 模块更新（设计 035）：只在用户点“检查更新”时访问 GitHub 发布页。下载完先核对包名、版本号和签名证书
 * （必须和已安装的同一张长期证书），通过才交给系统安装器；覆盖安装不清数据。安装器打开不等于安装成功，回来后重新核对版本。
 */
class Updater(private val context: Context) {
    data class Release(val tag: String, val name: String, val notes: String, val prerelease: Boolean, val apkUrl: String?, val apkName: String?, val apkSize: Long, val publishedAt: String)

    data class Verified(val file: File, val versionName: String, val versionCode: Long)

    private val client by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }

    fun releases(includePre: Boolean): List<Release> {
        val req = Request.Builder().url(API).header("Accept", "application/vnd.github+json").header("User-Agent", "ClearStarter").build()
        val text = client.newCall(req).execute().use { r ->
            if (r.code == 403) error("GitHub 限制了访问频率，过一会儿再试")
            // 私有仓库对没登录的请求一律回 404
            if (r.code == 404) error("看不到发布页：仓库现在没有公开。拿到新安装包后，用下面的“从文件安装”核对签名再装")
            if (!r.isSuccessful) error("GitHub 返回 ${r.code}")
            r.body.string()
        }
        return parse(text).filter { includePre || !it.prerelease }
    }

    fun download(release: Release, onProgress: (Long, Long) -> Unit): File {
        val url = release.apkUrl ?: error("这个版本没有安装包")
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, (release.apkName ?: "update.apk").replace(Regex("[^A-Za-z0-9._-]"), "_"))
        client.newCall(Request.Builder().url(url).header("User-Agent", "ClearStarter").build()).execute().use { r ->
            if (!r.isSuccessful) error("下载失败（${r.code}）")
            val total = r.body.contentLength()
            var done = 0L
            r.body.byteStream().use { ins ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        if (done > MAX_APK) error("安装包太大")
                        onProgress(done, total)
                    }
                }
            }
            if (total > 0 && done != total) error("下载不完整（$done / $total）")
        }
        return out
    }

    /** 用户选的安装包（文件管理器、聊天软件里收到的）：复制到缓存再核对，和下载的走同一套检查。 */
    fun fromFile(uri: android.net.Uri): Verified {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "picked.apk")
        val ins = context.contentResolver.openInputStream(uri) ?: error("打不开这个文件")
        ins.use { i ->
            out.outputStream().use { os ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    done += n
                    if (done > MAX_APK) error("安装包太大")
                    os.write(buf, 0, n)
                }
            }
        }
        return verify(out)
    }

    /** 核对下载的安装包：包名、版本号比现在新、签名证书和已安装的一致。失败抛出原因。 */
    fun verify(file: File): Verified {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: error("不是有效的安装包")
        if (archive.packageName != context.packageName) error("包名不对（${archive.packageName}）")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val newCode = PackageInfoCompat.getLongVersionCode(archive)
        val oldCode = PackageInfoCompat.getLongVersionCode(installed)
        if (newCode <= oldCode) error("不比现在的新（$newCode ≤ $oldCode）")
        if (signer(archive) == null || signer(archive) != signer(installed)) error("签名证书和已安装的不一致，不能覆盖安装")
        return Verified(file, archive.versionName.orEmpty(), newCode)
    }

    private fun signer(info: PackageInfo): String? {
        val sig = info.signingInfo?.apkContentsSigners?.firstOrNull()
        return sig?.toByteArray()?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } }
    }

    /** 交给系统安装器；没有“安装未知应用”权限时先打开授权页，返回 false。 */
    fun install(v: Verified): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", v.file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    fun installedVersion(): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName.orEmpty() to PackageInfoCompat.getLongVersionCode(info)
    }

    companion object {
        const val REPO = "OWNER/ClearStarter"
        private const val API = "https://api.github.com/repos/$REPO/releases?per_page=10"
        private const val MAX_APK = 80L * 1024 * 1024

        fun parse(json: String): List<Release> {
            val arr = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonArray ?: return emptyList()
            return arr.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val apk = (o["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { a -> (a["name"] as? JsonPrimitive)?.contentOrNull.orEmpty().endsWith(".apk") }
                Release(
                    s("tag_name"), s("name").ifEmpty { s("tag_name") }, s("body"), (o["prerelease"] as? JsonPrimitive)?.booleanOrNull == true,
                    (apk?.get("browser_download_url") as? JsonPrimitive)?.contentOrNull, (apk?.get("name") as? JsonPrimitive)?.contentOrNull,
                    (apk?.get("size") as? JsonPrimitive)?.longOrNull ?: 0, s("published_at").take(10),
                )
            }
        }
    }
}
