package io.github.clearkit.starter.hook.features.net

import io.github.clearkit.starter.hook.runtime.Feature
import io.github.clearkit.starter.hook.runtime.FeatureScope
import io.github.clearkit.starter.hook.runtime.HookCall
import io.github.clearkit.starter.hook.runtime.ModuleLog
import io.github.clearkit.starter.model.net.JsonRewrite
import io.github.clearkit.starter.model.net.JsonRule
import io.github.clearkit.starter.model.settings.BoolSetting
import io.github.clearkit.starter.model.settings.SettingsReader
import io.github.clearkit.starter.symbols.net.NetSymbols
import io.github.wzgrx.dexadapt.MethodSymbol
import java.io.Reader
import java.io.StringReader
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 接口返回数据的改写入口：【宿主】解析返回的 JSON 之前，先把文本交给已启用的规则（[NetRuleFeature.rules]）改一遍。
 *
 * 挂的是 JSON 库的公开方法（[NetSymbols]）：fastjson `parseObject`、Gson `fromJson(Reader, Type)`、kotlinx `decodeFromString`。
 * 宿主没用到的库找不到，只记一行日志，其它照常工作。宿主用 gRPC / protobuf 时这里改不到，参考 BiliClear 的 NetPipeline 加。
 *
 * **加规则不用改这个文件**：写一个 [NetRuleFeature]，在 AllFeatures.netRules 里登记。
 */
object NetPipeline : Feature("net.rewrite", "接口数据改写") {
    /** 用到这条管线的功能（AllFeatures 登记）。 */
    var consumers: List<NetRuleFeature> = emptyList()

    override val switches: List<BoolSetting> get() = consumers.flatMap { it.switches }
    override val listSwitches get() = consumers.flatMap { it.listSwitches }
    override val keepInSafeMode = true

    override fun isOn(settings: SettingsReader): Boolean = consumers.any { it.isOn(settings) }

    private class Entry(val feature: NetRuleFeature, val scope: FeatureScope)

    private val entries = CopyOnWriteArrayList<Entry>()

    /** Gson 从 Reader 读、按模型类名触发的规则关心的类名：先用它粗筛，免得每个回复都读一遍。 */
    @Volatile private var gsonModels: Set<String> = emptySet()

    fun attach(feature: NetRuleFeature, scope: FeatureScope) {
        entries.removeAll { it.feature === feature }
        entries += Entry(feature, scope)
        gsonModels = entries.flatMap { e -> e.feature.rules.flatMap { it.models } }.toSet()
    }

    override fun FeatureScope.install() {
        io.github.clearkit.starter.hook.features.module.HostCounters.attachImpact()
        fun entry(symbol: MethodSymbol, body: (HookCall) -> Any?) {
            symbols.methodOrNull(symbol)?.let { hook(it, body) } ?: ModuleLog.w("net pipeline: ${symbol.id} missing")
        }
        entry(NetSymbols.fastJsonParse, ::fastJson)
        entry(NetSymbols.fastJsonParseFull, ::fastJson)
        entry(NetSymbols.fastJsonParseText, ::fastJsonText)
        entry(NetSymbols.gsonFromReaderType, ::gson)
        entry(NetSymbols.kotlinxDecode, ::kotlinx)
    }

    private fun fastJson(call: HookCall): Any? {
        val text = call.arg(0) as? String ?: return call.proceed()
        val types = typeNames(call.arg(1) as? Type)
        if (ModuleLog.verbose) trace("fastjson", types.joinToString(","), text)
        val out = rewriteJson(text, types) ?: return call.proceed()
        // 两个重载（4 个、6 个参数）共用：只换第一个参数
        return call.proceed(out, *call.args.drop(1).toTypedArray())
    }

    private fun fastJsonText(call: HookCall): Any? {
        val text = call.arg(0) as? String ?: return call.proceed()
        if (ModuleLog.verbose) trace("fastjson-text", "JSONObject", text)
        val out = rewriteJson(text) ?: return call.proceed()
        return call.proceed(out)
    }

    private fun gson(call: HookCall): Any? {
        val reader = call.arg(0) as? Reader ?: return call.proceed()
        val types = typeNames(call.arg(1) as? Type)
        // 没有按模型名触发的规则时不读：Reader 只能读一次，读了就要换成新的
        if (types.none { it in gsonModels } && entries.none { e -> e.scope.active && e.feature.rules.any { it.models.isEmpty() && it.types.isEmpty() } }) return call.proceed()
        val text = reader.readText()
        if (ModuleLog.verbose) trace("gson", types.joinToString(","), text)
        return call.proceed(StringReader(rewriteJson(text, types) ?: text), call.arg(1))
    }

    private fun kotlinx(call: HookCall): Any? {
        val text = call.arg(1) as? String ?: return call.proceed()
        if (ModuleLog.verbose) trace("kotlinx", "?", text)
        val out = rewriteJson(text) ?: return call.proceed()
        return call.proceed(call.arg(0), out)
    }

    private class Picked(val rule: JsonRule, val entry: Entry)

    private fun rewriteJson(text: String, types: Set<String>? = null): String? {
        if (entries.isEmpty()) return null
        val picked = entries.filter { it.scope.active }.flatMap { e -> e.feature.rules.filter { it.matches(text, types) }.map { Picked(it, e) } }
        if (picked.isEmpty()) return null
        val byRule = picked.associateBy { it.rule }
        return JsonRewrite.rewrite(
            text, picked.map { it.rule },
            onError = { rule, error -> byRule[rule]?.entry?.scope?.fail("rule ${rule.id}", error) },
            onChanged = { rule ->
                logOnce(rule.id)
                byRule[rule]?.entry?.let { io.github.clearkit.starter.hook.features.module.Evidence.acted(it.feature, it.scope.settings) }
            },
        )
    }

    /** 排查用（只在“保存详细日志”打开时）：每种模型第一次出现时记一行，带开头一段（令牌、密钥打码）。 */
    private val traced = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private fun trace(kind: String, name: String, text: String?) {
        if (!traced.add("$kind:$name:" + (text?.take(60)?.hashCode() ?: 0))) return
        ModuleLog.d("net $kind $name" + (text?.let { " len=${it.length} " + io.github.clearkit.starter.model.net.LogRedact.redact(it).take(400).replace('\n', ' ') } ?: ""))
    }

    /** 每条规则这次启动第一次生效时记一行日志（冒烟测试据此确认规则真的命中了）。 */
    private val logged = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private fun logOnce(id: String) {
        if (logged.add(id)) ModuleLog.i("rule $id applied (first time this launch)")
    }

    /** 解析目标类型的名字：类名，泛型包装（Response<T>）连同类型参数一起。 */
    private fun typeNames(type: Type?): Set<String> {
        val out = HashSet<String>(4)
        fun walk(t: Type?, depth: Int) {
            if (t == null || depth > 3) return
            when (t) {
                is Class<*> -> out += t.name
                is ParameterizedType -> { walk(t.rawType, depth + 1); t.actualTypeArguments.forEach { walk(it, depth + 1) } }
                else -> out += t.toString()
            }
        }
        walk(type, 0)
        return out
    }
}

/** 一个按接口数据净化的功能：声明自己的规则，安装时登记到 [NetPipeline]。 */
abstract class NetRuleFeature(id: String, title: String) : Feature(id, title) {
    /** 规则都靠接口数据改写：它换表后才装好时，这个功能在启动时就漏了（开屏、底栏这类要重开）。 */
    override val dependsOn: Feature get() = NetPipeline

    open val rules: List<JsonRule> = emptyList()

    override fun FeatureScope.install() {
        NetPipeline.attach(this@NetRuleFeature, this)
    }
}
