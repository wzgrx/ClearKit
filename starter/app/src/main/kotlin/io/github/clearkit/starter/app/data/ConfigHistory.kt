package io.github.clearkit.starter.app.data

import android.content.Context
import androidx.core.content.edit
import io.github.clearkit.starter.model.config.ConfigDocument
import io.github.clearkit.starter.model.settings.Setting
import java.io.File

/**
 * 配置版本历史（设计 030）：每个版本是一份 [ConfigDocument]，存在 App 私有目录 `files/config-history/`。
 * 手动保存、恢复前、应用方案前、导入前都自动存一份。默认保留最近 [DEFAULT_KEEP] 个，可以改成不限（只管本模块的归档，
 * 磁盘空间照样受系统限制）。写完读回核对，读回不一致算保存失败。
 */
class ConfigHistory(private val context: Context) {
    data class Entry(val id: String, val file: File, val meta: ConfigDocument.Meta, val count: Int, val bytes: Long)

    private val dir: File get() = File(context.filesDir, "config-history").apply { mkdirs() }
    private val prefs get() = context.getSharedPreferences("config_history", Context.MODE_PRIVATE)

    var keepUnlimited: Boolean
        get() = prefs.getBoolean("unlimited", false)
        set(v) { prefs.edit { putBoolean("unlimited", v) }; if (!v) prune() }

    fun appVersion(): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** 把当前存储里的配置存成一个版本。返回新版本；读回核对不一致时抛错。 */
    fun save(stored: Map<String, Any?>, source: String, label: String = ""): Entry {
        val now = System.currentTimeMillis()
        val text = ConfigDocument.export(stored, ConfigDocument.Meta(now, source, appVersion(), label))
        return write(text, now)
    }

    /** 存一份已经生成好的文档（例如远程下载的、导入的文件）。 */
    fun saveDocument(doc: ConfigDocument.Document): Entry = write(ConfigDocument.encode(doc), System.currentTimeMillis())

    private fun write(text: String, now: Long): Entry {
        var f = File(dir, "$now.json")
        var n = 1
        while (f.exists()) f = File(dir, "$now-${n++}.json")
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(text)
        if (tmp.readText() != text) { tmp.delete(); error("保存后读回的内容不一致") }
        if (!tmp.renameTo(f)) { tmp.delete(); error("保存失败（改名）") }
        prune()
        return entry(f) ?: error("保存后读不出来")
    }

    fun list(): List<Entry> = (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
        .mapNotNull(::entry)
        .sortedByDescending { it.meta.createdAt }

    fun read(id: String): ConfigDocument.Document? {
        val f = File(dir, "$id.json").takeIf { it.isFile } ?: return null
        return (ConfigDocument.parse(f.readText()) as? ConfigDocument.Parsed.Ok)?.document
    }

    fun text(id: String): String? = File(dir, "$id.json").takeIf { it.isFile }?.readText()

    fun delete(id: String): Boolean = File(dir, "$id.json").delete()

    private fun entry(f: File): Entry? {
        val doc = (runCatching { ConfigDocument.parse(f.readText()) }.getOrNull() as? ConfigDocument.Parsed.Ok)?.document ?: return null
        return Entry(f.nameWithoutExtension, f, doc.meta, doc.values.size, f.length())
    }

    private fun prune() {
        if (keepUnlimited) return
        val files = (dir.listFiles { f -> f.name.endsWith(".json") } ?: return).sortedByDescending { it.name.substringBefore('.').substringBefore('-').toLongOrNull() ?: 0 }
        files.drop(DEFAULT_KEEP).forEach { it.delete() }
    }

    companion object {
        const val DEFAULT_KEEP = 50

        /** 按计划写入并读回核对：返回没写对的设置（空 = 全部生效）。写之前调用方先存一份“恢复前”的版本。 */
        fun apply(store: SettingsStore, plan: ConfigDocument.Plan): List<Setting<*>> {
            // 整批恢复 / 应用方案有自己的版本历史，不逐项记“最近改动”
            io.github.clearkit.starter.app.ModuleApp.instance.journal.paused { store.atomically {
                val writes = plan.writes
                if (writes.isNotEmpty()) setAll(*writes.map { it.key to it.value }.toTypedArray())
                plan.resets.forEach { reset(it) }
            } }
            val bad = ArrayList<Setting<*>>()
            for ((s, v) in plan.writes) if (store.raw(s.key) != v) bad += s
            for (s in plan.resets) if (store.raw(s.key) != null) bad += s
            return bad
        }
    }
}
