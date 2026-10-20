package io.github.clearkit.starter.tools.symbolfinder

import io.github.wzgrx.dexadapt.dexkit.DesktopDexKit
import io.github.wzgrx.dexadapt.dexkit.DexKitSource
import io.github.clearkit.starter.model.HostBuild
import io.github.clearkit.starter.symbols.SymbolCatalog
import io.github.wzgrx.dexadapt.SymbolTable
import io.github.wzgrx.dexadapt.resolve.SymbolResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge
import java.io.File

/**
 * 手机上的本机适配和离线符号表一致（借鉴 x-clean-harness 的“桌面跑 DexKit”）：用 App 同一份 [DexKitSource] 加桌面版
 * libdexkit，在每个内置版本的 APK 上按“未知版本”的方式解析（不用按版本钉死的位置），结果必须和 dexlib2 生成、
 * 提交在仓库里的符号表逐条相同。以前这条路径只能在手机上自检。
 *
 * 需要【宿主】安装包（HOST_APK_DIR）和 `scripts/build-dexkit-desktop.sh` 构建的 libdexkit.so；完整门禁
 * （HOST_REQUIRE_APKS=1）下缺了就失败，否则跳过。
 */
class DexKitParityTest {
    private val apkDir = System.getenv("HOST_APK_DIR")?.let(::File)?.takeIf { it.isDirectory }
    private val required = System.getenv("HOST_REQUIRE_APKS") == "1"
    private val lib = DesktopDexKit.library

    @Test
    fun `dexkit adaptation on every bundled build matches the committed tables`() {
        if (required) {
            assertTrue("完整门禁需要【宿主】安装包：HOST_APK_DIR 没设或目录不存在", apkDir != null)
            assertTrue("完整门禁需要桌面版 DexKit：先运行 scripts/build-dexkit-desktop.sh（找的是 $lib）", lib.isFile)
        }
        val builds = buildsWithApk(apkDir)
        assumeTrue("没有【宿主】安装包（HOST_APK_DIR），跳过", builds.isNotEmpty())
        assumeTrue("desktop libdexkit not built", lib.isFile)
        DesktopDexKit.load()
        for (build in builds) {
            val committed = javaClass.classLoader!!.getResourceAsStream(SymbolTable.resourcePath(build.versionCode))!!
                .bufferedReader().use { SymbolTable.fromJson(it.readText()) }
            val started = System.currentTimeMillis()
            val table = DexKitBridge.create(apkFor(build, apkDir!!).path).use { bridge ->
                SymbolResolver(DexKitSource(bridge, ""), build.versionCode).table(SymbolCatalog.all)
            }
            System.err.println("DexKit ${build.label}: ${table.entries.size} found, ${table.missing.size} missing (${(System.currentTimeMillis() - started) / 1000}s)")
            // 标注了“这个版本没有”（absentIn）的符号本机适配也找不到，这是对的；除此之外一个都不能缺
            val absent = SymbolCatalog.all.filter { build.name in it.absentIn }.map { it.id }.toSet()
            assertEquals("${build.label} 本机适配解析不到", emptyMap<String, String>(), table.missing.filterKeys { it !in absent })
            assertEquals("${build.label} 标注了没有、本机适配却找到了（标注错了？）", emptySet<String>(), absent.filter { it in table.entries }.toSet())
            val different = committed.entries.keys.filter { committed.entries[it] != table.entries[it] }
            assertEquals("${build.label} 本机适配和离线符号表不一致：${different.joinToString()}", emptyList<String>(), different)
        }
    }
}
