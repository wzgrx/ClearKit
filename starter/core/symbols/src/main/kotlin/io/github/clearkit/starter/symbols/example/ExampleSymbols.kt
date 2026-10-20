package io.github.clearkit.starter.symbols.example

import io.github.wzgrx.dexadapt.ClassQuery
import io.github.wzgrx.dexadapt.MethodQuery
import io.github.wzgrx.dexadapt.MethodShape
import io.github.wzgrx.dexadapt.SymbolGroup
import io.github.wzgrx.dexadapt.TypeRef
import io.github.wzgrx.dexadapt.type

/**
 * 示例符号：【宿主】的启动页和它的 onCreate（ExampleHookFeature 用）。
 *
 * 启动页的类名写在安装包清单里，不会被混淆：`aapt2 dump badging base.apk | grep launchable-activity` 能直接查到。
 * **生成脚本会填上**；没填对时符号生成报 `missing`，按 ClearKit docs/11-cookbook.md「查启动页类名」改这里。
 *
 * 真正的功能找的是混淆过的类和方法，查询要用字符串常量、方法形状、调用关系这些跨版本稳定的特征，见 docs/11「写查询」。
 */
object ExampleSymbols : SymbolGroup("example") {
    val launchActivity = cls("launchActivity", ClassQuery(name = "com.example.host.MainActivity"))

    /** `onCreate(Bundle)`。 */
    val launchOnCreate = method(
        "launchOnCreate",
        MethodQuery(
            owner = TypeRef.Of(launchActivity),
            shape = MethodShape(name = "onCreate", returns = type("void"), params = listOf(type("android.os.Bundle"))),
        ),
    )
}
