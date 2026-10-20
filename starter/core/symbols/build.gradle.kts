plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 生成解析器的构建信息：手机上本机适配用的 DexKit 版本写进符号定义指纹（R2），升级 DexKit 后各手机上的本机表自动重扫，
// 【宿主】进程（没有 App 的 BuildConfig）也能算出同一个指纹。版本只在 libs.versions.toml 里写一处。
val generateBuildInfo = tasks.register("generateSymbolBuildInfo") {
    val out = layout.buildDirectory.dir("generated/source/buildinfo/kotlin")
    val version = libs.versions.dexkit.get()
    inputs.property("dexkit", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("io/github/clearkit/starter/symbols/SymbolBuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package io.github.clearkit.starter.symbols\n\n" +
                "/** 构建时生成（core/symbols/build.gradle.kts），不要手改。 */\n" +
                "object SymbolBuildInfo {\n" +
                "    /** App 本机适配用的 DexKit 版本（libs.versions.toml）。 */\n" +
                "    const val DEXKIT_VERSION = \"" + version + "\"\n" +
                "}\n",
        )
    }
}
kotlin.sourceSets.named("main") { kotlin.srcDir(generateBuildInfo) }

dependencies {
    api(project(":core:model"))
    // 查询模型、解析器、符号表来自 DexAdapt；这里只留【宿主】的符号定义（各 SymbolGroup）和内置表。
    api(libs.dexadapt.core)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
