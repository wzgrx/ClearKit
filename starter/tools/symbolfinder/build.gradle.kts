plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

application {
    mainClass.set("io.github.clearkit.starter.tools.symbolfinder.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx12g")
}

dependencies {
    implementation(project(":core:symbols"))
    implementation(libs.dexadapt.dexlib)
    implementation(libs.dexlib2)
    testImplementation(libs.junit)
    // DexKitParityTest：App 用的同一份 DexKitSource，加 DexKit 的 JVM 类、FlatBuffers 和桌面原生库加载（DexAdapt 的 dexkit-desktop）。
    testImplementation(libs.dexadapt.dexkit.desktop)
}

tasks.test {
    // 【宿主】安装包不在仓库里：没设 HOST_APK_DIR 时要读 APK 的测试跳过；scripts/verify.sh 完整门禁设
    // HOST_REQUIRE_APKS=1，这时没有 APK 就失败而不是跳过（V1）。
    val apkDir = System.getenv("HOST_APK_DIR")?.takeIf { File(it).isDirectory }
    if (apkDir != null) {
        environment("HOST_APK_DIR", apkDir)
        // APK 是这个测试的输入（V3）：换了 APK 不能沿用上次 UP-TO-DATE 或缓存的结果。
        inputs.dir(apkDir).withPropertyName("hostApks").withPathSensitivity(PathSensitivity.RELATIVE)
    }
    inputs.property("hostApkDirSet", apkDir != null)
    // 桌面版 libdexkit（scripts/build-dexkit-desktop.sh 构建，放在仓库外）。
    val dexkitHome = System.getenv("DEXKIT_DESKTOP_HOME").orEmpty()
    environment("DEXKIT_DESKTOP_HOME", dexkitHome)
    inputs.property("dexkitDesktopHome", dexkitHome)
    val requireApks = System.getenv("HOST_REQUIRE_APKS").orEmpty()
    environment("HOST_REQUIRE_APKS", requireApks)
    inputs.property("requireApks", requireApks)
    maxHeapSize = "6g"
    // SymbolTablesTest 把“单点依赖”的余量提示写到 stderr（不算失败），在控制台上显示出来。
    testLogging { events("standard_error") }
}
