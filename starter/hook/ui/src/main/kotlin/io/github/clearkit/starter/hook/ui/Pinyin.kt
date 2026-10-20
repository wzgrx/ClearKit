package io.github.clearkit.starter.hook.ui

/**
 * 拼音搜索（App 的搜索和【宿主】里的设置窗共用）：全拼和首字母。Android 10 起用系统 ICU 的汉字转拉丁，
 * 更早的系统只按中文搜。
 */
object Pinyin {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()
    @Volatile private var tr: Any? = null
    @Volatile private var trFailed = false

    /** 全拼（连写）和首字母。 */
    fun of(text: String): Pair<String, String> = cache.getOrPut(text) {
        if (android.os.Build.VERSION.SDK_INT < 29 || trFailed) return@getOrPut "" to ""
        val t = (tr as? android.icu.text.Transliterator) ?: runCatching { android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower") }
            .getOrNull()?.also { tr = it } ?: run { trFailed = true; return@getOrPut "" to "" }
        val words = text.map { ch -> if (ch.code in 0x4E00..0x9FFF) t.transliterate(ch.toString()).trim() else ch.lowercase() }
        words.joinToString("").filter { it.isLetterOrDigit() } to words.mapNotNull { it.firstOrNull()?.takeIf(Char::isLetterOrDigit) }.joinToString("")
    }

    /** [q] 是不是在 [text] 里：中文直接比；纯字母时也比拼音全拼和首字母。 */
    fun matches(text: String, q: String): Boolean {
        if (text.contains(q, true)) return true
        if (q.any { it.code > 0x7F }) return false
        val lq = q.lowercase().filter { it.isLetterOrDigit() }
        if (lq.isEmpty()) return false
        val (full, initials) = of(text)
        return initials.contains(lq) || full.contains(lq)
    }
}
