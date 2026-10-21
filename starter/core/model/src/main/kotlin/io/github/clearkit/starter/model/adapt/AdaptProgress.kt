package io.github.clearkit.starter.model.adapt

import kotlinx.serialization.Serializable

/**
 * App 本机适配（本机解析【宿主】安装包）的当前状态，【宿主】里的适配卡片按它画进度。
 * [phase]：IDLE 没在跑、RUNNING 正在解析、DONE 刚完成、FAILED 失败；[done] / [total] 是已解析 / 全部符号数（还不知道总数时 total 为 0）。
 */
@Serializable
data class AdaptProgress(
    val phase: String = IDLE,
    val done: Int = 0,
    val total: Int = 0,
    val message: String = "",
) {
    val running: Boolean get() = phase == RUNNING

    companion object {
        const val IDLE = "IDLE"
        const val RUNNING = "RUNNING"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
    }
}

/** 适配卡片上的文字和进度（纯函数，【宿主】侧和测试共用）。 */
object AdaptCardText {
    /** “本机解析 · 查找状态 523 / 550”；还不知道总数时只说正在准备。 */
    fun counter(done: Int, total: Int): String =
        if (total <= 0) "本机解析 · 正在准备…" else "本机解析 · 查找状态 ${done.coerceIn(0, total)} / $total"

    /** 进度条位置 0–1。 */
    fun fraction(done: Int, total: Int): Float = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    /** 适配完成后的标题。 */
    fun adaptedTitle(versionName: String): String = "已适配【宿主】 $versionName"

    /** 结果卡片的标题：有用不了的写出个数。 */
    fun resultTitle(r: AdaptReport): String =
        if (r.unavailable.isEmpty()) adaptedTitle(r.hostVersionName) else "已适配 ${r.hostVersionName} · ${r.unavailable.size} 项用不了"

    /** 结果卡片的副标题。 */
    fun resultSubtitle(r: AdaptReport): String = when {
        r.restart.isNotEmpty() -> "${r.active} 项已生效，重开一次后全部生效"
        r.unavailable.isNotEmpty() -> "${r.active} 项已生效；下面这些这一版找不到要用的地方"
        r.observing > 0 -> "${r.active} 项已生效，${r.observing} 项先观察，用到时自动确认"
        else -> "全部找到，${r.active} 项已生效"
    }

    /** 查找情况一行：“找到 99 / 99 处”。 */
    fun foundLine(r: AdaptReport): String = "找到 ${r.found.coerceAtMost(r.total)} / ${r.total} 处" + if (r.local) " · 本机适配" else ""

    /** “重开后生效 7 项”下面的功能名：先列 [limit] 个，多的用“等”。 */
    fun restartList(r: AdaptReport, limit: Int = 5): String =
        r.restart.take(limit).joinToString(" · ") + if (r.restart.size > limit) " 等" else ""

    /**
     * 启动时的一行提示（平时 / 重开后）：第一次用这个版本时说“已适配”；有问题时写出问题，[warn] 为真用橙色。
     * 返回 (副标题, warn)。
     */
    fun loadedLine(r: AdaptReport, firstForVersion: Boolean): Pair<String, Boolean> = when {
        r.unavailable.isNotEmpty() -> "${r.active} 项生效 · ${r.unavailable.size} 项这一版用不了" to true
        r.restart.isNotEmpty() -> "${r.active} 项生效 · ${r.restart.size} 项重开后生效" to true
        firstForVersion -> "已适配 ${r.hostVersionName} · ${r.active} 项生效 · 全部正常" to false
        else -> "${r.active} 项生效" to false
    }

    /** 重开会打断正在播放的内容时的提醒。 */
    fun restartWarning(playing: Boolean): String = if (playing) "会中断正在播放的内容，几秒后自动打开、回到首页" else "几秒后自动打开，回到首页"
}
