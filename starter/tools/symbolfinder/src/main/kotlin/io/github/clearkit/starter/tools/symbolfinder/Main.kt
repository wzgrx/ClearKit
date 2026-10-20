package io.github.clearkit.starter.tools.symbolfinder

import io.github.wzgrx.dexadapt.dexlib.DexIndex
import io.github.clearkit.starter.model.HostBuild
import io.github.clearkit.starter.symbols.SymbolCatalog
import io.github.wzgrx.dexadapt.SymbolTable
import io.github.wzgrx.dexadapt.resolve.MarginReport
import io.github.wzgrx.dexadapt.resolve.SymbolMargin
import java.io.File

/** 每个内置版本的安装包在 `$HOST_APK_DIR` 里的位置：`<渠道小写>-<versionCode>/base.apk`（从手机上 adb pull 的原样）。 */
fun apkFor(build: HostBuild, dir: File): File = File(dir, "${build.channel.name.lowercase()}-${build.versionCode}/base.apk")

/** 有安装包的内置版本（没有安装包的跳过：符号表留给手机上的本机适配生成）。 */
fun buildsWithApk(dir: File?): List<HostBuild> = if (dir == null) emptyList() else HostBuild.entries.filter { apkFor(it, dir).isFile }

/**
 * 重新生成 core/symbols/src/main/resources/symbols/ 里的内置符号表、余量报告、版本矩阵、spec 和类型化访问器。
 * 用法：设好 HOST_APK_DIR 后 `./gradlew :tools:symbolfinder:run --args="<仓库根目录>"`。
 * 没有安装包时只生成不需要安装包的文件（访问器、spec），并提示怎么准备安装包（ClearKit docs/10-environment.md）。
 */
fun main(args: Array<String>) {
    val root = File(args.firstOrNull() ?: ".").absoluteFile
    val apkDir = System.getenv("HOST_APK_DIR")?.let(::File)?.takeIf { it.isDirectory }
    val outDir = File(root, "core/symbols/src/main/resources/symbols").apply { mkdirs() }
    var failed = false
    val margins = LinkedHashMap<Long, List<SymbolMargin>>()
    val tables = LinkedHashMap<HostBuild, SymbolTable>()
    val summaries = StringBuilder()
    val builds = buildsWithApk(apkDir)
    HostBuild.entries.filter { it !in builds }.forEach { b ->
        println("SKIP ${b.label}: 没有安装包（应在 ${apkDir?.let { apkFor(b, it).path } ?: "\$HOST_APK_DIR/${b.channel.name.lowercase()}-${b.versionCode}/base.apk"}）")
    }
    for (build in builds) {
        val apk = apkFor(build, apkDir!!)
        val started = System.currentTimeMillis()
        val index = DexIndex.load(apk)
        val resolver = Resolver(index, build)
        val table = resolver.table(SymbolCatalog.all)
        tables[build] = table
        File(outDir, SymbolTable.resourcePath(build.versionCode).substringAfter('/')).writeText(table.toJson() + "\n")
        println(
            "${build.label}: ${table.entries.size} resolved, ${table.missing.size} missing " +
                "(${index.classes.size} classes, ${(System.currentTimeMillis() - started) / 1000}s)",
        )
        table.missing.forEach { (id, why) -> println("  missing $id: $why") }
        if (table.missing.isNotEmpty()) failed = true
        // 余量：逐个去掉一个条件，看哪些查询的唯一性只靠一个条件（下个版本最容易失效的）。
        val marginStarted = System.currentTimeMillis()
        margins[build.versionCode] = resolver.margins(SymbolCatalog.all)
        summaries.append(Resolver.marginSummary(build.label, margins.getValue(build.versionCode)))
            .append("  (${(System.currentTimeMillis() - marginStarted) / 1000}s)\n")
    }
    print(summaries)
    if (builds.isEmpty()) println("没有任何内置版本的安装包：只生成了访问器和 spec。准备安装包见 ClearKit docs/10-environment.md「收集安装包」。")
    File(outDir, MarginReport.RESOURCE.substringAfter('/')).writeText(MarginReport.of(SymbolCatalog.fingerprint, margins).toJson() + "\n")
    File(root, AdaptMatrix.PATH).apply { parentFile.mkdirs() }.writeText(AdaptMatrix.render(tables, margins))
    File(root, AdaptMatrix.HTML_PATH).writeText(AdaptMatrix.html(tables, margins))
    // 查询目录导出成 DexAdapt 的 YAML spec：命令行工具（dexadapt matrix / diff / repair）直接用。
    File(root, AdaptMatrix.SPEC_PATH).writeText(AdaptMatrix.spec())
    File(root, AdaptMatrix.ACCESS_PATH).apply { parentFile.mkdirs() }.writeText(AdaptMatrix.access())
    if (failed) System.exit(1)
}
