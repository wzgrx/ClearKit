package io.github.clearkit.starter.model.diag

import io.github.clearkit.starter.model.bridge.FeatureStatus
import io.github.clearkit.starter.model.bridge.RuntimeStatus
import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.Setting
import io.github.clearkit.starter.model.settings.Settings
import io.github.clearkit.starter.model.settings.SettingsReader
import io.github.clearkit.starter.model.settings.StringSetSetting
import io.github.clearkit.starter.model.settings.StringSetting
import io.github.clearkit.starter.model.settings.TextFormat

/**
 * 诊断（设计 033、034、135、086）：功能记录的筛选、设置之间的前置条件检查、外部干扰、脱敏的复现报告。
 * 只读：这里不改任何设置，也不扫描别的模块的代码。
 */
object Diagnostics {
    // ---- 功能记录筛选（033） ----

    enum class Filter(val label: String, val states: Set<FeatureStatus.State>) {
        ALL("全部", FeatureStatus.State.entries.toSet()),
        PROBLEM("出错", setOf(FeatureStatus.State.FAILED, FeatureStatus.State.CRASH_DISABLED)),
        MISSING("不可用", setOf(FeatureStatus.State.UNAVAILABLE)),
        OBSERVING("观察中", setOf(FeatureStatus.State.OBSERVING, FeatureStatus.State.SAFE_MODE_OFF)),
        ACTIVE("生效", setOf(FeatureStatus.State.ACTIVE)),
        OFF("已关闭", setOf(FeatureStatus.State.OFF)),
    }

    fun stateLabel(s: FeatureStatus.State) = when (s) {
        FeatureStatus.State.ACTIVE -> "生效"
        FeatureStatus.State.OFF -> "已关闭"
        FeatureStatus.State.OBSERVING -> "观察中"
        FeatureStatus.State.SAFE_MODE_OFF -> "安全模式暂停"
        FeatureStatus.State.UNAVAILABLE -> "不可用"
        FeatureStatus.State.FAILED -> "出错"
        FeatureStatus.State.CRASH_DISABLED -> "崩溃后停用"
    }

    fun filter(features: List<FeatureStatus>, query: String, filter: Filter): List<FeatureStatus> = features.filter { f ->
        f.state in filter.states && (query.isBlank() || f.title.contains(query, true) || f.id.contains(query, true) || f.detail.orEmpty().contains(query, true))
    }

    // ---- 冲突与前置条件（034） ----

    enum class Level(val label: String) { FIX("需处理"), CHECK("需核对"), HINT("提示") }

    /** [route]：点了打开哪个设置页（App 路由），null 表示没有对应页面。 */
    data class Issue(val level: Level, val title: String, val detail: String, val route: String? = null)

    private data class Needs(val child: Setting<*>, val parent: BoolSetting, val route: String)

    /** 子项开着、但它依赖的总开关关着：子项不会生效。加了“依赖总开关”的设置时在这里登记。 */
    private val NEEDS: List<Needs> = emptyList()

    private fun on(s: Setting<*>, reader: SettingsReader): Boolean = when (s) {
        is BoolSetting -> reader[s]
        is StringSetSetting -> reader[s].isNotEmpty()
        is StringSetting -> reader[s].isNotEmpty()
        else -> false
    }

    /**
     * [stored]：显式保存过的键（子项默认开着、用户从没碰过的，不算“开着”）；[status]：各客户端最近的报告；
     * [installed]：已安装的可能干扰的包。
     */
    fun issues(reader: SettingsReader, stored: Set<String>, status: Collection<RuntimeStatus>, installed: Set<String>): List<Issue> {
        val out = ArrayList<Issue>()
        if (!reader[Settings.Module.enabled]) out += Issue(Level.FIX, "模块主开关关着", "所有功能都不会生效", "module")
        if (reader[Settings.Module.safeMode]) out += Issue(Level.CHECK, "安全模式开着", "只保留净化类功能，其它暂停", "module")
        for (n in NEEDS) {
            if (n.child.key in stored && on(n.child, reader) && !reader[n.parent]) {
                out += Issue(Level.HINT, "“${n.child.title}”不会生效", "它依赖的“${n.parent.title}”关着", n.route)
            }
        }
        for (s in status) {
            val bad = s.features.filter { it.state == FeatureStatus.State.FAILED || it.state == FeatureStatus.State.CRASH_DISABLED }
            for (feat in bad) out += Issue(Level.FIX, "${feat.title}：${stateLabel(feat.state)}", feat.detail ?: "看功能状态里的原因", "status")
            val missing = s.features.filter { it.state == FeatureStatus.State.UNAVAILABLE }
            if (missing.isNotEmpty()) {
                out += Issue(Level.CHECK, "${missing.size} 个功能在 ${s.hostVersionName} 上不可用", missing.take(6).joinToString("、") { it.title } + if (missing.size > 6) " 等" else "", "adapt")
            }
        }
        return out.sortedBy { it.level.ordinal }
    }

    // ---- 复现报告（033、086、135） ----

    /** 这项设置的值能不能原样写进报告：凭据不写，名单只写数量（里面可能有 UID、关键词）。 */
    fun reportValue(s: Setting<*>, value: Any?): String = when {
        value == null -> "默认"
        s is StringSetting && s.format == TextFormat.SECRET -> if (value.toString().isEmpty()) "空" else "已填（隐藏）"
        s is StringSetSetting && s.choices.isEmpty() -> "${(value as? Set<*>)?.size ?: 0} 项"
        s is StringSetting && s.format in setOf(TextFormat.URL, TextFormat.HOST) -> if (value.toString().isEmpty()) "空" else "已填（隐藏）"
        s is StringSetting && value.toString().length > 40 -> "已填（${value.toString().length} 字）"
        else -> value.toString()
    }

    data class Device(val android: String, val sdk: Int, val model: String, val moduleVersion: String, val root: String = "")

    fun report(device: Device, stored: Map<String, Any?>, status: Collection<RuntimeStatus>, issues: List<Issue>, now: Long): String = buildString {
        appendLine("# 【模块】 诊断报告")
        appendLine("生成时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(now))}")
        appendLine("模块：${device.moduleVersion}；安卓 ${device.android}（接口 ${device.sdk}）；${device.model}" + if (device.root.isNotEmpty()) "；根权限：${device.root}" else "")
        appendLine("说明：不含账号、登录信息、访问密钥、服务器地址；名单类设置只写数量。")
        appendLine()
        for (s in status.sortedBy { it.hostPackage }) {
            appendLine("## ${s.hostPackage} ${s.hostVersionName}（${s.hostVersionCode}）")
            appendLine("适配：${if (s.adapted) "有表" else "没有表"}；主开关：${if (s.masterEnabled) "开" else "关"}；安全模式：${if (s.safeMode) "开" else "关"}；模块 ${s.moduleVersion}")
            for (feat in s.features.sortedBy { it.state.ordinal }) {
                appendLine("- ${feat.title}（${feat.id}）：${stateLabel(feat.state)}" + (feat.detail?.let { "，$it" } ?: ""))
            }
            appendLine()
        }
        if (issues.isNotEmpty()) {
            appendLine("## 检查结果")
            issues.forEach { appendLine("- [${it.level.label}] ${it.title}：${it.detail}") }
            appendLine()
        }
        appendLine("## 改过的设置")
        for (s in Settings.all) {
            val v = stored[s.key] ?: continue
            appendLine("- ${s.key}（${s.title}）= ${reportValue(s, v)}")
        }
        val layered = stored.keys.count { it.contains('|') }
        if (layered > 0) appendLine("- 分层覆盖：$layered 项")
    }
}
