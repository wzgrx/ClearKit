package io.github.clearkit.starter.hook.runtime

import io.github.wzgrx.dexadapt.runtime.SymbolLinker
import io.github.wzgrx.dexadapt.ClassSymbol
import io.github.wzgrx.dexadapt.FieldSymbol
import io.github.wzgrx.dexadapt.MethodSymbol
import io.github.wzgrx.dexadapt.Symbol
import io.github.wzgrx.dexadapt.SymbolTable
import java.lang.reflect.Executable
import java.lang.reflect.Field

/**
 * 【宿主】进程里把符号表变成反射对象：DexAdapt 的 [SymbolLinker]（按表加载、核对签名、缓存、热替换），
 * 这里只加上读内置表和本机适配远程文件的部分。
 */
class HostSymbols(table: SymbolTable?, private val loader: ClassLoader) {
    // 推测的位置（本机适配解析不到时的分级回退）只给有契约的方法符号用：它们的 Hook 先观察，契约验证通过才起作用
    private val linker = SymbolLinker(
        table, loader, unsupported = "此版本【宿主】未适配", log = { ModuleLog.w(it) },
        useGuess = { id -> id in io.github.clearkit.starter.symbols.SymbolCatalog.catalog.contracts },
    )

    /** 类型化访问器（生成的 [io.github.clearkit.starter.hook.runtime.access.HostAccess]）：代替按名字反射，跟着换表。 */
    val access by lazy { io.github.clearkit.starter.hook.runtime.access.HostAccess(linker, io.github.clearkit.starter.symbols.SymbolCatalog.catalog) }

    /** 当前用的表；本机适配在【宿主】运行中完成时会被换成新表（[replace]）。 */
    val table: SymbolTable? get() = linker.table
    val supported: Boolean get() = linker.supported

    /** 换成新表（热适配）：连同按旧表查过的结果一起换掉。 */
    fun replace(newTable: SymbolTable) = linker.replace(newTable)

    fun clazz(symbol: ClassSymbol): Class<*> = linker.clazz(symbol)
    fun method(symbol: MethodSymbol): Executable = linker.method(symbol)
    fun methodOrNull(symbol: MethodSymbol): Executable? = linker.methodOrNull(symbol)
    fun field(symbol: FieldSymbol): Field = linker.field(symbol)
    fun available(symbol: Symbol): Boolean = linker.available(symbol)

    /** [member]（[method] 等查出来的对象）是哪个符号。 */
    fun idOf(member: Any): String? = linker.idOf(member)

    /** 契约里的类型：`$id` 是类符号，否则是类名（按【宿主】的 ClassLoader 加载）。 */
    fun classOf(ref: String): Class<*>? = runCatching {
        if (ref.startsWith("$")) {
            val id = ref.removePrefix("$")
            clazz(io.github.clearkit.starter.symbols.SymbolCatalog.all.filterIsInstance<ClassSymbol>().first { it.id == id })
        } else {
            Class.forName(ref, false, loader)
        }
    }.getOrNull()

    /** Why [symbol] cannot be used, or null if it can. */
    fun problem(symbol: Symbol): String? = linker.problem(symbol)

    companion object {
        /**
         * 没有内置表的新版本：读 App 本机适配生成、写进 LSPosed 远程文件的表（HostAdapter）。
         * 表里记的版本号必须和正在运行的一致，免得【宿主】升级后用上旧版本的表。
         */
        fun loadRemote(xposed: io.github.libxposed.api.XposedInterface, versionCode: Long): SymbolTable? = runCatching {
            // 没有这个文件时 libxposed 返回 null（不是抛 FileNotFoundException）：有内置表、App 清掉了本机表时就是这样。
            val pfd: android.os.ParcelFileDescriptor? = xposed.openRemoteFile(SymbolTable.remoteFileName(versionCode))
            (pfd ?: return@runCatching null).use { java.io.FileInputStream(it.fileDescriptor).bufferedReader().readText() }.takeIf { it.isNotBlank() }?.let(SymbolTable::fromJson)?.takeIf { it.versionCode == versionCode }
        }.onFailure { if (it !is java.io.FileNotFoundException) ModuleLog.w("adapted symbol table $versionCode unreadable: ${it.message}") }
            .getOrNull()

        /** Loads the bundled table for [versionCode] from the module's own class path. */
        fun loadTable(versionCode: Long): SymbolTable? {
            val stream = HostSymbols::class.java.classLoader?.getResourceAsStream(SymbolTable.resourcePath(versionCode)) ?: return null
            return runCatching { stream.bufferedReader().use { SymbolTable.fromJson(it.readText()) } }
                .onFailure { ModuleLog.e("symbol table $versionCode unreadable", it) }
                .getOrNull()
        }
    }
}
