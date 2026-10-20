package io.github.clearkit.starter.symbols.net

import io.github.wzgrx.dexadapt.ClassQuery
import io.github.wzgrx.dexadapt.MethodQuery
import io.github.wzgrx.dexadapt.MethodShape
import io.github.wzgrx.dexadapt.SymbolGroup
import io.github.wzgrx.dexadapt.TypeRef
import io.github.wzgrx.dexadapt.type

/**
 * 接口数据的 JSON 解析入口（库的公开方法，大多数 App 不混淆它们）：fastjson、Gson、kotlinx.serialization。
 * NetPipeline 挂这些方法，把文本先交给 JSON 规则改一遍。
 *
 * **宿主没用某个库时**：第一次生成符号表会报这几个缺失（`missing`）。在对应符号后面加 `.absentIn(HostBuild.版本)`
 * 标注“这个版本没有”（见 ClearKit docs/11-cookbook.md「符号缺失怎么办」）。至少要有一个库能挂上，JSON 规则才有用。
 */
object NetSymbols : SymbolGroup("net") {
    val fastJson = cls("fastJson", ClassQuery(name = "com.alibaba.fastjson.JSON"))

    /** `parseObject(String, Type, int, Feature[])`：Retrofit 的 fastjson 转换器常用。 */
    val fastJsonParse = method(
        "fastJsonParse",
        MethodQuery(
            owner = TypeRef.Of(fastJson),
            shape = MethodShape(
                name = "parseObject", isStatic = true, returns = type("java.lang.Object"),
                params = listOf(type("java.lang.String"), type("java.lang.reflect.Type"), type("int"), type("com.alibaba.fastjson.parser.Feature[]")),
            ),
        ),
    )

    /** `parseObject(String, Type, ParserConfig, ParseProcess, int, Feature[])`：其余按类型解析的重载最后都走它。 */
    val fastJsonParseFull = method(
        "fastJsonParseFull",
        MethodQuery(
            owner = TypeRef.Of(fastJson),
            shape = MethodShape(
                name = "parseObject", isStatic = true, returns = type("java.lang.Object"),
                params = listOf(
                    type("java.lang.String"), type("java.lang.reflect.Type"), type("com.alibaba.fastjson.parser.ParserConfig"),
                    type("com.alibaba.fastjson.parser.deserializer.ParseProcess"), type("int"), type("com.alibaba.fastjson.parser.Feature[]"),
                ),
            ),
        ),
    )

    /** `parseObject(String)`：自己解析成 JSONObject 的地方。 */
    val fastJsonParseText = method(
        "fastJsonParseText",
        MethodQuery(
            owner = TypeRef.Of(fastJson),
            shape = MethodShape(name = "parseObject", isStatic = true, returns = type("com.alibaba.fastjson.JSONObject"), params = listOf(type("java.lang.String"))),
        ),
    )

    val gson = cls("gson", ClassQuery(name = "com.google.gson.Gson"))

    /** `fromJson(Reader, Type)`：Retrofit 的 Gson 转换器。方法名可能被混淆，按参数和返回值找。 */
    val gsonFromReaderType = method(
        "gsonFromReaderType",
        MethodQuery(owner = TypeRef.Of(gson), shape = MethodShape(returns = type("java.lang.Object"), params = listOf(type("java.io.Reader"), type("java.lang.reflect.Type")))),
    )

    val kotlinxJson = cls("kotlinxJson", ClassQuery(name = "kotlinx.serialization.json.Json"))

    /** `decodeFromString(DeserializationStrategy, String)`。 */
    val kotlinxDecode = method(
        "kotlinxDecode",
        MethodQuery(
            owner = TypeRef.Of(kotlinxJson),
            shape = MethodShape(
                name = "decodeFromString", returns = type("java.lang.Object"),
                params = listOf(type("kotlinx.serialization.DeserializationStrategy"), type("java.lang.String")),
            ),
        ),
    )
}
