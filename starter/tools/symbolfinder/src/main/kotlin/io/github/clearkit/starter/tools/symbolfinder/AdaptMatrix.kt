package io.github.clearkit.starter.tools.symbolfinder

import io.github.clearkit.starter.model.HostBuild
import io.github.wzgrx.dexadapt.SymbolTable
import io.github.wzgrx.dexadapt.matrix.Build
import io.github.wzgrx.dexadapt.matrix.Matrix
import io.github.wzgrx.dexadapt.resolve.SymbolMargin

/**
 * 【宿主】内置版本的版本矩阵（DexAdapt 的 [Matrix]）：生成到 docs/adapt/matrix.md，SymbolTablesTest 检查是否过期。
 */
object AdaptMatrix {
    const val PATH = "docs/adapt/matrix.md"
    const val HTML_PATH = "docs/adapt/matrix.html"
    const val SPEC_PATH = "docs/adapt/catalog.yaml"

    /** 全部符号定义的 YAML（DexAdapt spec 格式），由 :tools:symbolfinder:run 生成。 */
    fun spec(): String = "# 由 ./gradlew :tools:symbolfinder:run 从 core/symbols 生成，不要手改。给 DexAdapt 命令行用。\n" +
        io.github.wzgrx.dexadapt.spec.Spec(io.github.clearkit.starter.model.HOST_PACKAGE, io.github.clearkit.starter.symbols.SymbolCatalog.catalog).toYaml()

    /** 生成的类型化访问器（DexAdapt 的 Accessors）：功能用它代替按名字反射，由 :tools:symbolfinder:run 生成。 */
    const val ACCESS_PATH = "hook/runtime/src/main/kotlin/io/github/clearkit/starter/hook/runtime/access/HostAccess.kt"

    fun access(): String = io.github.wzgrx.dexadapt.codegen.Accessors.kotlin(
        io.github.clearkit.starter.symbols.SymbolCatalog.catalog,
        io.github.wzgrx.dexadapt.codegen.Accessors.Options("io.github.clearkit.starter.hook.runtime.access", "HostAccess", "./gradlew :tools:symbolfinder:run"),
    )

    /** 同样的矩阵画成热力图（HTML）。 */
    fun html(tables: Map<HostBuild, SymbolTable>, margins: Map<Long, List<SymbolMargin>>): String = Matrix.html(
        tables.mapKeys { build(it.key) }, margins, channelOrder = HostBuild.Channel.entries.map { it.label }, title = "【模块】版本矩阵",
    )

    fun build(b: HostBuild) = Build(b.name, b.channel.label, b.versionCode, b.versionName)

    fun render(tables: Map<HostBuild, SymbolTable>, margins: Map<Long, List<SymbolMargin>>): String = Matrix.render(
        tables.mapKeys { build(it.key) }, margins,
        channelOrder = HostBuild.Channel.entries.map { it.label },
        regenerate = "由 `./gradlew :tools:symbolfinder:run --args=\"\$PWD\"` 生成，不要手改。说明见 [README.md](README.md)。",
    )
}
