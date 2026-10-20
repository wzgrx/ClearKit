package io.github.clearkit.starter.hook.runtime

import io.github.wzgrx.dexadapt.ClassQuery
import io.github.wzgrx.dexadapt.ClassSymbol
import io.github.wzgrx.dexadapt.FieldQuery
import io.github.wzgrx.dexadapt.FieldSymbol
import io.github.wzgrx.dexadapt.MethodQuery
import io.github.wzgrx.dexadapt.MethodSymbol
import io.github.wzgrx.dexadapt.Resolved
import io.github.wzgrx.dexadapt.SymbolTable
import io.github.wzgrx.dexadapt.type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostSymbolsTest {
    private val stringClass = ClassSymbol("t.string", ClassQuery(name = "java.lang.String"))
    private val regionMatches = MethodSymbol("t.regionMatches", MethodQuery())
    private val ctor = MethodSymbol("t.ctor", MethodQuery())
    private val badReturn = MethodSymbol("t.badReturn", MethodQuery())
    private val missing = MethodSymbol("t.missing", MethodQuery())
    private val field = FieldSymbol("t.field", FieldQuery(owner = type("java.awt.Point")))

    private val table = SymbolTable(
        versionCode = 1,
        dexSha256 = "",
        entries = mapOf(
            "t.string" to Resolved.Class("java.lang.String"),
            "t.regionMatches" to Resolved.Method("java.lang.String", "regionMatches", listOf("boolean", "int", "java.lang.String", "int", "int"), "boolean"),
            "t.ctor" to Resolved.Method("java.lang.StringBuilder", "<init>", listOf("java.lang.String"), "void"),
            "t.badReturn" to Resolved.Method("java.lang.String", "length", emptyList(), "long"),
            "t.field" to Resolved.Field("java.awt.Point", "x", "int"),
        ),
        missing = mapOf("t.missing" to "no match"),
    )

    private val symbols = HostSymbols(table, javaClass.classLoader!!)

    @Test
    fun `classes methods constructors and fields resolve to reflection objects`() {
        assertEquals(String::class.java, symbols.clazz(stringClass))
        assertEquals("regionMatches", symbols.method(regionMatches).name)
        assertEquals(StringBuilder::class.java, symbols.method(ctor).declaringClass)
        assertEquals("x", symbols.field(field).name)
    }

    @Test
    fun `changed return type and missing entries are reported, not thrown at callers that ask`() {
        assertFalse(symbols.available(badReturn))
        assertTrue(symbols.problem(badReturn)!!.contains("return type"))
        assertFalse(symbols.available(missing))
        assertTrue(symbols.problem(missing)!!.contains("no match"))
        assertEquals(null, symbols.methodOrNull(missing))
    }

    @Test
    fun `without a table every symbol is unavailable`() {
        val none = HostSymbols(null, javaClass.classLoader!!)
        assertFalse(none.supported)
        assertNotNull(none.problem(stringClass))
    }

    @Test
    fun `replacing the table drops results looked up with the old one`() {
        val hot = HostSymbols(SymbolTable(1, "", emptyMap(), mapOf("t.string" to "no match")), javaClass.classLoader!!)
        assertFalse(hot.available(stringClass)) // 旧表里没有，失败结果进了旧表的缓存
        hot.replace(table)
        assertTrue(hot.available(stringClass)) // 换表后按新表重新查，不会拿到旧缓存
        assertEquals(table, hot.table)
    }
}
