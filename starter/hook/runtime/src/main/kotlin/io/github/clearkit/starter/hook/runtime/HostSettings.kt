package io.github.clearkit.starter.hook.runtime

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.ChoiceSetting
import io.github.clearkit.starter.model.settings.FloatSetting
import io.github.clearkit.starter.model.settings.IntSetting
import io.github.clearkit.starter.model.settings.MapSettingsReader
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.model.settings.SettingsReader
import io.github.clearkit.starter.model.settings.StringSetSetting
import io.github.clearkit.starter.model.settings.StringSetting
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Host-side settings: an immutable snapshot of the module's RemotePreferences, replaced on
 * every change notification. Hooks read [current] on hot paths, so reads never touch IPC.
 */
class HostSettings(private val prefs: SharedPreferences?) : SettingsReader {
    @Volatile
    private var snapshot: Map<String, Any?> = read()

    /** Bumped whenever the snapshot is replaced; lets features cache values derived from settings. */
    @Volatile
    var version: Int = 0
        private set
    private val listeners = CopyOnWriteArrayList<(changedKey: String?) -> Unit>()

    // Kept as a field: SharedPreferences holds listeners weakly.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> refresh(key) }

    init {
        runCatching { prefs?.registerOnSharedPreferenceChangeListener(prefListener) }
            .onFailure { ModuleLog.w("settings change listener unavailable; changes apply after restart", it) }
    }

    /** 免 Root 加载时没有远程设置：从 App 拉到（或从缓存读到）一次之后也算读得到。 */
    @Volatile private var pulled = false

    val readable: Boolean get() = prefs != null || pulled

    /** 读得到 LSPosed 的远程设置。 */
    val remote: Boolean get() = prefs != null

    /** 分层配置开着时：账号覆盖（`u<UID>|键`）→ 客户端覆盖（`cn|键` / `play|键`）→ 全局值（见 ConfigScope）。 */
    private val reader = MapSettingsReader { key ->
        val snap = snapshot
        if (snap[io.github.clearkit.starter.model.settings.Settings.Scope.enabled.key] == true) {
            io.github.clearkit.starter.model.settings.ConfigScope.lookup(snap, key, client, account)
        } else snap[key]
    }

    companion object {
        /** 当前客户端的配置层（cn / play），运行时启动时设置。 */
        @Volatile var client: String? = null

        /** 当前登录账号的配置层（u<UID>），没登录为 null；账号切换时由 ScopeAccountFeature 更新。 */
        @Volatile var account: String? = null
    }

    /** 在功能 install 里登记时，安装失败会自动撤销（[InstallRecorder]）。 */
    fun onChange(listener: (changedKey: String?) -> Unit) {
        watch(listener)
    }

    /** 和 [onChange] 一样，返回值用于注销（界面关闭时）。 */
    fun watch(listener: (changedKey: String?) -> Unit): () -> Unit {
        listeners += listener
        val remove = { listeners.remove(listener); Unit }
        InstallRecorder.record(remove)
        return remove
    }

    fun refresh(changedKey: String? = null) {
        // 免 Root 加载时没有远程设置可读：保留拉来的快照，只通知一遍（例如换账号后重新按账号层取值）
        if (prefs != null) snapshot = read()
        version++
        ModuleLog.d("settings pushed by framework: ${changedKey ?: "*"}")
        notifyListeners(changedKey)
    }

    /** Applies the settings pulled from the app (authoritative while the app is reachable). */
    fun replace(values: Map<String, Any?>) {
        pulled = true
        if (values == snapshot) return
        val changed = (values.keys + snapshot.keys).filter { values[it] != snapshot[it] }
        snapshot = values
        version++
        notifyListeners(changed.singleOrNull())
        ModuleLog.i("settings pulled from app: ${changed.joinToString()}")
    }

    /**
     * 【宿主】里刚写入、App 已确认的改动立刻生效，不等下次从 App 拉取。[values] 里值为 null 的键
     * 表示恢复默认（从快照里去掉）。
     */
    fun applyLocal(values: Map<String, Any?>) {
        if (values.isEmpty()) return
        val next = HashMap(snapshot)
        values.forEach { (k, v) -> if (v == null) next.remove(k) else next[k] = v }
        if (next == snapshot) return
        snapshot = next
        version++
        notifyListeners(values.keys.singleOrNull())
        ModuleLog.i("settings applied from host write: ${values.keys.joinToString()}")
    }

    /**
     * 监听者都在主线程收到通知：框架推送的变化（[refresh]）来自 binder 线程，而监听者大多直接改界面、
     * 读写只在主线程用的状态。
     */
    private fun notifyListeners(changedKey: String?) {
        val main = Looper.getMainLooper()
        if (main == null || Looper.myLooper() == main) dispatch(changedKey) else Handler(main).post { dispatch(changedKey) }
    }

    private fun dispatch(changedKey: String?) {
        listeners.forEach { runCatching { it(changedKey) }.onFailure { e -> ModuleLog.w("settings listener failed", e) } }
    }

    private fun read(): Map<String, Any?> = runCatching { prefs?.all.orEmpty() }
        .onFailure { ModuleLog.w("settings unreadable", it) }
        .getOrDefault(emptyMap())

    override fun get(setting: BoolSetting) = reader[setting]
    override fun get(setting: IntSetting) = reader[setting]
    override fun get(setting: FloatSetting) = reader[setting]
    override fun get(setting: StringSetting) = reader[setting]
    override fun get(setting: StringSetSetting) = reader[setting]
    override fun get(setting: ChoiceSetting) = reader[setting]

    val masterEnabled: Boolean get() = this[Settings.Module.enabled]
    val safeMode: Boolean get() = this[Settings.Module.safeMode]
}
