package io.github.clearkit.starter.hook.ui

import java.util.Locale

/**
 * 界面文字的简繁转换（App 和【宿主】里的面板共用）：设置“界面文字”选了繁体（或跟随系统、系统是繁体中文）时，
 * 显示前用系统 ICU 的 Hans-Hant 转换；Android 10 以下没有这个转换，照显示简体。转换结果缓存。
 */
object ZhText {
    /** system / hans / hant（设置 appearance.locale 的值）。 */
    @Volatile var mode: String = "system"
        set(value) { if (field != value) { field = value; cache.clear() } }

    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var tr: Any? = null
    @Volatile private var failed = false

    val traditional: Boolean
        get() = when (mode) {
            "hant" -> true
            "hans" -> false
            else -> Locale.getDefault().let { it.language == "zh" && (it.script == "Hant" || it.country in setOf("TW", "HK", "MO")) }
        }

    fun convert(text: String): String {
        if (!traditional || text.isEmpty() || android.os.Build.VERSION.SDK_INT < 29 || failed) return text
        if (text.none { it.code in 0x4E00..0x9FFF }) return text
        return cache.getOrPut(text) {
            val t = (tr as? android.icu.text.Transliterator) ?: runCatching { android.icu.text.Transliterator.getInstance("Hans-Hant") }.getOrNull()?.also { tr = it }
                ?: run { failed = true; return@getOrPut text }
            t.transliterate(text)
        }
    }

    fun convert(text: CharSequence): CharSequence = if (text is String) convert(text) else if (traditional) convert(text.toString()) else text
}
