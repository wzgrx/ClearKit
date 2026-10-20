package io.github.clearkit.starter.app.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.model.HostBuild
import io.github.clearkit.starter.model.bridge.Bridge

/** 只回应【宿主】进程的 `call()`，其它一律拒绝。 */
class BridgeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!callerIsHost()) throw SecurityException("bridge is only for the host")
        // 【宿主】可能在 App 进程刚被拉起、Application.onCreate 还没做完时就调过来（Provider 先于 onCreate 发布）。
        if (!ModuleApp.awaitReady()) return bridgeFail("【模块】正在启动")
        val handler = BridgeHandlers.registry[method] ?: return bridgeFail("unknown method $method")
        return try {
            handler(BridgeCall(ModuleApp.instance, method, arg, extras?.getString(Bridge.KEY_JSON)))
        } catch (e: IllegalArgumentException) {
            // 缺正文、JSON 解析失败：回错误，不抛给【宿主】（否则会被当成“看不到 App”去重新握手）。
            android.util.Log.w("BcBridge", "bad request $method", e)
            bridgeFail("bad request $method: ${e.message?.take(200)}")
        }
    }

    private fun callerIsHost(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return true
        val packages = context?.packageManager?.getPackagesForUid(uid).orEmpty()
        return packages.any { HostBuild.channelOf(it) != null }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
