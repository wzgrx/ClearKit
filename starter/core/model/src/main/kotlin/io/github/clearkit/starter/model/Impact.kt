package io.github.clearkit.starter.model

/**
 * 效果计数：规则每去掉一条广告或不想看的内容就报一次（只报种类和条数，不报内容）。
 * 【宿主】进程里由 HostCounters 接上 [sink] 按周累计，App 首页显示“这周帮你挡下 N 条”；没接时什么都不做（单测里）。
 */
object Impact {
    /** 列表里的一条（广告卡片、推荐条目）。要分别统计别的种类时在这里加常量，首页的合计在 HomePage 的 week() 里。 */
    const val CARD = "card"

    @Volatile
    var sink: ((kind: String, n: Int) -> Unit)? = null

    /** 名单里某一条命中（设置键、名单里存的那一行），App 的名单页显示“这周隐藏 N 条”。 */
    @Volatile
    var hitSink: ((key: String, entry: String) -> Unit)? = null

    /**
     * 同一条内容（[id]：视频编号、文字）在这次运行里只算一次：首页刷新、返回时同一批卡片会被反复判断，
     * 不去重的话一张卡片会被算很多次。不给 [id] 的照算。
     */
    private val seen = object : LinkedHashMap<Int, Boolean>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Boolean>?) = size > 4000
    }

    private fun first(token: Int): Boolean = synchronized(seen) { seen.put(token, true) == null }

    fun hidden(kind: String, n: Int = 1, id: Any? = null) {
        if (n <= 0) return
        if (id != null && !first(31 * kind.hashCode() + id.hashCode())) return
        runCatching { sink?.invoke(kind, n) }
    }

    fun hit(key: String, entry: String, text: String? = null) {
        if (text != null && !first(31 * (31 * key.hashCode() + entry.hashCode()) + text.hashCode())) return
        runCatching { hitSink?.invoke(key, entry) }
    }

    /** 单测用：清掉去重记录。 */
    fun resetSeen() = synchronized(seen) { seen.clear() }

    /** 名单里一行的短标识（计数的键里用，不存原文）。App 和【宿主】算法相同。 */
    fun entryId(entry: String): String = Integer.toHexString(entry.trim().hashCode())
}
