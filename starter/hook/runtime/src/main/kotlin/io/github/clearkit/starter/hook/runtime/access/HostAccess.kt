// 由 DexAdapt 生成（./gradlew :tools:symbolfinder:run），不要手改。
@file:Suppress("unused", "RedundantSuppression", "UNCHECKED_CAST", "FunctionName", "PropertyName")

package io.github.clearkit.starter.hook.runtime.access

import io.github.wzgrx.dexadapt.Catalog
import io.github.wzgrx.dexadapt.ClassSymbol
import io.github.wzgrx.dexadapt.FieldSymbol
import io.github.wzgrx.dexadapt.MethodSymbol
import io.github.wzgrx.dexadapt.runtime.SymbolLinker
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 目录里 10 个符号的类型化访问器。用不了的符号抛 SymbolUnavailable（原因同 SymbolLinker），
 * `OrNull` 版本返回 null；字段的 `OrNull` 版本在对象不是字段所在类的实例时也返回 null（模型对象可能是别的类）。
 */
class HostAccess(private val linker: SymbolLinker, catalog: Catalog) {
    private val ids = catalog.symbols.associateBy { it.id }

    private fun c(id: String): Class<*> = linker.clazz(ids.getValue(id) as ClassSymbol)
    private fun f(id: String): Field = linker.field(ids.getValue(id) as FieldSymbol)
    private fun m(id: String): java.lang.reflect.Executable = linker.method(ids.getValue(id) as MethodSymbol)

    private fun read(id: String, target: Any?): Any? = f(id).get(target)

    private fun readOrNull(id: String, target: Any?): Any? {
        val field = runCatching { f(id) }.getOrNull() ?: return null
        if (!Modifier.isStatic(field.modifiers) && !field.declaringClass.isInstance(target)) return null
        return runCatching { field.get(target) }.getOrNull()
    }

    private fun call(id: String, receiver: Any?, args: Array<out Any?>): Any? = when (val e = m(id)) {
        is Method -> try { e.invoke(receiver, *args) } catch (t: java.lang.reflect.InvocationTargetException) { throw t.targetException }
        is Constructor<*> -> try { e.newInstance(*args) } catch (t: java.lang.reflect.InvocationTargetException) { throw t.targetException }
        else -> error("$id: $e")
    }

    val net: Net = Net()

    /** 组 `net`。 */
    inner class Net {
        /** `net.fastJson`：类。 */
        val fastJson: Class<*> get() = c("net.fastJson")
        val fastJsonOrNull: Class<*>? get() = runCatching { c("net.fastJson") }.getOrNull()

        /** `net.fastJsonParse`：方法（java.lang.String, java.lang.reflect.Type, int, com.alibaba.fastjson.parser.Feature[]） → java.lang.Object。静态方法 receiver 传 null。 */
        fun fastJsonParse(receiver: Any?, a0: String?, a1: Any?, a2: Int, a3: Any?): Any? = call("net.fastJsonParse", receiver, arrayOf<Any?>(a0, a1, a2, a3))
        val fastJsonParseAvailable: Boolean get() = linker.available(ids.getValue("net.fastJsonParse"))

        /** `net.fastJsonParseFull`：方法（java.lang.String, java.lang.reflect.Type, com.alibaba.fastjson.parser.ParserConfig, com.alibaba.fastjson.parser.deserializer.ParseProcess, int, com.alibaba.fastjson.parser.Feature[]） → java.lang.Object。静态方法 receiver 传 null。 */
        fun fastJsonParseFull(receiver: Any?, a0: String?, a1: Any?, a2: Any?, a3: Any?, a4: Int, a5: Any?): Any? = call("net.fastJsonParseFull", receiver, arrayOf<Any?>(a0, a1, a2, a3, a4, a5))
        val fastJsonParseFullAvailable: Boolean get() = linker.available(ids.getValue("net.fastJsonParseFull"))

        /** `net.fastJsonParseText`：方法（java.lang.String） → com.alibaba.fastjson.JSONObject。静态方法 receiver 传 null。 */
        fun fastJsonParseText(receiver: Any?, a0: String?): Any? = call("net.fastJsonParseText", receiver, arrayOf<Any?>(a0))
        val fastJsonParseTextAvailable: Boolean get() = linker.available(ids.getValue("net.fastJsonParseText"))

        /** `net.gson`：类。 */
        val gson: Class<*> get() = c("net.gson")
        val gsonOrNull: Class<*>? get() = runCatching { c("net.gson") }.getOrNull()

        /** `net.gsonFromReaderType`：方法（java.io.Reader, java.lang.reflect.Type） → java.lang.Object。静态方法 receiver 传 null。 */
        fun gsonFromReaderType(receiver: Any?, a0: Any?, a1: Any?): Any? = call("net.gsonFromReaderType", receiver, arrayOf<Any?>(a0, a1))
        val gsonFromReaderTypeAvailable: Boolean get() = linker.available(ids.getValue("net.gsonFromReaderType"))

        /** `net.kotlinxJson`：类。 */
        val kotlinxJson: Class<*> get() = c("net.kotlinxJson")
        val kotlinxJsonOrNull: Class<*>? get() = runCatching { c("net.kotlinxJson") }.getOrNull()

        /** `net.kotlinxDecode`：方法（kotlinx.serialization.DeserializationStrategy, java.lang.String） → java.lang.Object。静态方法 receiver 传 null。 */
        fun kotlinxDecode(receiver: Any?, a0: Any?, a1: String?): Any? = call("net.kotlinxDecode", receiver, arrayOf<Any?>(a0, a1))
        val kotlinxDecodeAvailable: Boolean get() = linker.available(ids.getValue("net.kotlinxDecode"))
    }

    val example: Example = Example()

    /** 组 `example`。 */
    inner class Example {
        /** `example.launchActivity`：类。 */
        val launchActivity: Class<*> get() = c("example.launchActivity")
        val launchActivityOrNull: Class<*>? get() = runCatching { c("example.launchActivity") }.getOrNull()

        /** `example.launchOnCreate`：方法（android.os.Bundle） → void。静态方法 receiver 传 null。 */
        fun launchOnCreate(receiver: Any?, a0: android.os.Bundle?) { call("example.launchOnCreate", receiver, arrayOf<Any?>(a0)) }
        val launchOnCreateAvailable: Boolean get() = linker.available(ids.getValue("example.launchOnCreate"))
    }
}
