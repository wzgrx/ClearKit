package io.github.clearkit.starter.tools.symbolfinder

import io.github.wzgrx.dexadapt.dexlib.DexIndex
import io.github.clearkit.starter.model.HostBuild
import io.github.wzgrx.dexadapt.Resolved
import io.github.wzgrx.dexadapt.Symbol
import io.github.wzgrx.dexadapt.SymbolTable
import io.github.wzgrx.dexadapt.resolve.SymbolMargin
import io.github.wzgrx.dexadapt.resolve.SymbolResolver

/**
 * 给已知版本 [build] 生成内置符号表：匹配逻辑在 core/symbols 的 [SymbolResolver]（手机上的本机适配用同一份），
 * 这里只接上全量索引、手工钉死的位置和“该版本没有”的标注。
 */
class Resolver(index: DexIndex, build: HostBuild) {
    private val resolver = SymbolResolver(
        source = index,
        versionCode = build.versionCode,
        pinFor = { it.pins[build.name] },
        skip = { build.name in it.absentIn },
        log = { System.err.println(it) },
    )

    fun table(symbols: List<Symbol>): SymbolTable = resolver.table(symbols)

    fun resolve(symbol: Symbol): Result<Resolved> = resolver.resolve(symbol)

    /** 每条查询的余量（逐个去掉一个条件后的候选数）；钉死、跳过、没解析出来、按类名找的不算。 */
    fun margins(symbols: List<Symbol>): List<SymbolMargin> = symbols.mapNotNull(resolver::margin)

    companion object {
        /** 余量的文字摘要：多少条单点依赖、各自的关键条件。 */
        fun marginSummary(label: String, margins: List<SymbolMargin>): String = buildString {
            val single = margins.filter { it.singlePoint }
            val shapeOnly = single.count { it.shapeOnly }
            append("$label margin: ${margins.size} queries checked, ${single.size} single-point (单点依赖), $shapeOnly of them on a shape-only constraint (仅靠形状)\n")
            single.sortedBy { it.id }.forEach { m -> append("  ${if (m.shapeOnly) "!" else " "} ${m.id}: ${m.critical.joinToString(", ")}\n") }
        }
    }
}
