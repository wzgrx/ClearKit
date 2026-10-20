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

    /** 结果卡片的副标题（查找状态在下面的进度行里，这里不重复）。 */
    fun result(found: Int, total: Int, active: Int): String =
        if (found >= total) "全部找到，$active 个功能已生效" else "$active 个功能已生效"

    /** 适配完成后的标题。 */
    fun adaptedTitle(versionName: String): String = "已适配【宿主】 $versionName"

    /** 有找不到的符号时的说明（全部找到时 null）。 */
    fun missingNote(found: Int, total: Int): String? =
        (total - found).takeIf { it > 0 }?.let { "$it 处没找到，依赖它们的功能暂不可用" }
}
