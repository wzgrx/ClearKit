package io.github.clearkit.starter.symbols

import io.github.clearkit.starter.model.HostBuild
import io.github.wzgrx.dexadapt.SymbolTable
import io.github.wzgrx.dexadapt.resolve.SymbolGraph
import org.junit.Assert.assertEquals
import org.junit.Test

/** 不需要 APK、秒级完成的目录和内置表检查（“表是否最新”由 tools/symbolfinder 的 SymbolTablesTest 对着真实 APK 做）。 */
class SymbolCatalogTest {
    @Test
    fun `catalog has no forward references, dangling references or cycles`() {
        assertEquals(emptyList<String>(), SymbolGraph.problems(SymbolCatalog.all))
    }

    @Test
    fun `built-in tables cover exactly the current catalog`() {
        // 内置表不记 catalog 指纹，只能比符号 id：加了、删了、改了名的符号没重新生成表，这里马上发现。
        for (build in HostBuild.entries) {
            val text = javaClass.classLoader!!.getResourceAsStream(SymbolTable.resourcePath(build.versionCode))
                ?.bufferedReader()?.use { it.readText() }
            // 还没有安装包时没有内置表（App 在手机上本机适配）；完整门禁（HOST_REQUIRE_APKS=1）下必须有
            if (text == null && System.getenv("HOST_REQUIRE_APKS") != "1") { System.err.println("SKIP ${build.label}: 还没有内置表"); continue }
            val table = SymbolTable.fromJson(requireNotNull(text) { "no built-in table for ${build.label}" })
            assertEquals(build.versionCode, table.versionCode)
            val expected = SymbolCatalog.all.filter { build.name !in it.absentIn }.map { it.id }.toSortedSet()
            assertEquals("${build.label}：内置表和查询目录不一致，运行 :tools:symbolfinder:run 重新生成", expected, (table.entries.keys + table.missing.keys).toSortedSet())
            assertEquals("${build.label} unresolved", emptyMap<String, String>(), table.missing)
        }
    }

    @Test
    fun `built-in version codes are unique across channels`() {
        // 内置表按版本号存放（symbols/<versionCode>.json）：两个渠道的版本号撞了会互相覆盖
        assertEquals(HostBuild.entries.size, HostBuild.entries.map { it.versionCode }.toSet().size)
    }

    @Test
    fun `resolver code summary in the catalog fingerprint is current`() {
        // 本机表的指纹里有解析代码的摘要：DexAdapt 改了这几个文件的代码（不算注释和空白），这里给出新值，
        // 填到 SymbolCatalog.RESOLVER_SOURCES；各手机上的本机表会因此重扫。算法和红薯助手的同名测试一样。
        val dexadapt = java.io.File("../../../dexadapt")
        val files = listOf(
            "dexkit/src/main/kotlin/io/github/wzgrx/dexadapt/dexkit/DexKitSource.kt",
            "core/src/main/kotlin/io/github/wzgrx/dexadapt/resolve/SymbolResolver.kt",
            "core/src/main/kotlin/io/github/wzgrx/dexadapt/resolve/SymbolSource.kt",
        )
        val code = files.joinToString("\n") { path -> codeOnly(java.io.File(dexadapt, path).readText()) }
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        assertEquals("解析代码变了：确认要不要加 RESOLVER_VERSION，再把 SymbolCatalog.RESOLVER_SOURCES 改成新值", digest, SymbolCatalog.RESOLVER_SOURCES)
    }

    private fun codeOnly(text: String): String = text
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .lines().map { it.substringBefore("// ").trim() }.filter { it.isNotEmpty() }
        .joinToString("\n")
}
