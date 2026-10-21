package io.github.clearkit.starter.symbols

import io.github.clearkit.starter.symbols.example.ExampleSymbols
import io.github.clearkit.starter.symbols.net.NetSymbols
import io.github.wzgrx.dexadapt.Catalog
import io.github.wzgrx.dexadapt.Symbol
import io.github.wzgrx.dexadapt.SymbolGroup

object SymbolCatalog {
    /** 模块用到的全部符号组：写一个功能时把它的组加到这里。 */
    val groups: List<SymbolGroup> = listOf(
        NetSymbols,
        ExampleSymbols,
    )

    /**
     * 解析逻辑（DexAdapt 的 SymbolResolver、DexKitSource）的版本：改了匹配规则就加一，指纹跟着变，各手机上的本机表会重扫。
     */
    const val RESOLVER_VERSION = 1

    /**
     * 解析代码（DexAdapt 的 SymbolResolver、SymbolSource、DexKitSource，去掉注释和空白）的摘要，由 SymbolCatalogTest 守着：
     * 改了这几个文件，测试失败并给出新值，填回来即可。它算进 [fingerprint]，各手机上的本机表跟着重扫。
     */
    const val RESOLVER_SOURCES = "ab2992555789007e"

    /**
     * 全部符号定义（id、查询、钉子、“某版本没有”）+ 解析器版本 + DexKit 版本的指纹。本机适配生成的表记下它，
     * 模块更新后查询变了，旧表就算过期，App 自动重扫。
     */
    val fingerprint: String by lazy {
        catalog.fingerprint("resolver=$RESOLVER_VERSION\nsources=$RESOLVER_SOURCES\ndexkit=${SymbolBuildInfo.DEXKIT_VERSION}\n")
    }

    /** DexAdapt 的目录（重复 id 在这里就会报错）。 */
    val catalog: Catalog by lazy { Catalog(*groups.toTypedArray()) }

    val all: List<Symbol> get() = catalog.symbols

    /**
     * 这个渠道本来就没有的符号：在该渠道**每个**内置版本里都标了“这个版本没有”（absentIn）。
     * 本机适配新版本时，这些符号找不到是正常的（例如只有国际版才有的入口），不算缺失、不提示。
     */
    fun channelAbsent(channel: io.github.clearkit.starter.model.HostBuild.Channel): Set<String> {
        val builds = io.github.clearkit.starter.model.HostBuild.entries.filter { it.channel == channel }.map { it.name }
        if (builds.isEmpty()) return emptySet()
        return all.filter { s -> builds.all { it in s.absentIn } }.map { it.id }.toSet()
    }

    /** 按宿主包名取 [channelAbsent]；不认识的包名返回空。 */
    fun channelAbsentFor(packageName: String): Set<String> =
        io.github.clearkit.starter.model.HostBuild.channelOf(packageName)?.let(::channelAbsent).orEmpty()
}
