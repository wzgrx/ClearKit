package io.github.clearkit.starter.tools.symbolfinder

import io.github.wzgrx.dexadapt.dexlib.DexIndex
import io.github.clearkit.starter.model.HostBuild
import io.github.clearkit.starter.symbols.SymbolCatalog
import io.github.wzgrx.dexadapt.SymbolTable
import io.github.wzgrx.dexadapt.resolve.MarginReport
import io.github.wzgrx.dexadapt.resolve.SymbolMargin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Re-resolves the catalog against the real host APKs and checks that the committed tables are
 * current and complete. Skipped when HOST_APK_DIR is not set (the APKs are not in the repo) —
 * except under `HOST_REQUIRE_APKS=1` (scripts/verify.sh full gate), where a missing APK fails.
 */
class SymbolTablesTest {
    private val apkDir = System.getenv("HOST_APK_DIR")?.let(::File)?.takeIf { it.isDirectory }
    private val required = System.getenv("HOST_REQUIRE_APKS") == "1"

    @Test
    fun `committed tables match the APKs and resolve every symbol`() {
        if (required) assertTrue("完整门禁需要【宿主】安装包：HOST_APK_DIR 没设或目录不存在", apkDir != null)
        if (required) HostBuild.entries.forEach { assertTrue("完整门禁缺 ${it.label} 的安装包：${apkFor(it, apkDir!!)}", apkFor(it, apkDir).isFile) }
        val builds = buildsWithApk(apkDir)
        assumeTrue("没有【宿主】安装包（HOST_APK_DIR），跳过", builds.isNotEmpty())
        val margins = LinkedHashMap<Long, List<SymbolMargin>>()
        val tables = LinkedHashMap<HostBuild, SymbolTable>()
        for (build in builds) {
            val resolver = Resolver(DexIndex.load(apkFor(build, apkDir!!)), build)
            val generated = resolver.table(SymbolCatalog.all)
            assertEquals("${build.label} unresolved symbols", emptyMap<String, String>(), generated.missing)
            val committed = javaClass.classLoader!!.getResourceAsStream(SymbolTable.resourcePath(build.versionCode))
                ?.bufferedReader()?.use { SymbolTable.fromJson(it.readText()) }
            assertEquals("${build.label} table is stale: run :tools:symbolfinder:run", generated, committed)
            margins[build.versionCode] = resolver.margins(SymbolCatalog.all)
            tables[build] = generated
        }
        // 余量只提示、不算失败：哪些查询的唯一性只靠一个条件（下个版本最容易失效），以及 margin.json 是否过期。
        val warning = StringBuilder("WARNING 单点依赖的查询（不影响通过，见 symbols/margin.json）：\n")
        margins.forEach { (vc, list) -> warning.append(Resolver.marginSummary(HostBuild.entries.first { it.versionCode == vc }.label, list).lines().first()).append('\n') }
        val report = MarginReport.of(SymbolCatalog.fingerprint, margins)
        val committedMargin = javaClass.classLoader!!.getResourceAsStream(MarginReport.RESOURCE)?.bufferedReader()?.use { MarginReport.fromJson(it.readText()) }
        if (committedMargin != report) warning.append("WARNING symbols/margin.json 过期：运行 :tools:symbolfinder:run 重新生成\n")
        val matrix = File("../../${AdaptMatrix.PATH}").takeIf { it.isFile }?.readText()
        if (matrix != AdaptMatrix.render(tables, margins)) warning.append("WARNING ${AdaptMatrix.PATH} 过期：运行 :tools:symbolfinder:run 重新生成\n")
        val spec = File("../../${AdaptMatrix.SPEC_PATH}").takeIf { it.isFile }?.readText()
        if (spec != AdaptMatrix.spec()) warning.append("WARNING ${AdaptMatrix.SPEC_PATH} 过期：运行 :tools:symbolfinder:run 重新生成\n")
        System.err.print(warning)
    }

    @Test
    fun `generated accessors are current`() {
        // 不需要 APK：访问器只由查询目录决定。改了符号定义没重新生成时直接失败（编译期就要用到它）。
        val committed = File("../../${AdaptMatrix.ACCESS_PATH}").readText()
        assertEquals("${AdaptMatrix.ACCESS_PATH} 过期：运行 ./gradlew :tools:symbolfinder:run 重新生成", AdaptMatrix.access(), committed)
    }
}
