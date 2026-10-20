plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.clearkit.starter.hook.ui"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    buildToolsVersion = libs.versions.buildTools.get()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // 界面截图（HostShotsTest）只在 -Pbc.shots=<输出目录> 时跑
        unitTests.all { it.systemProperty("bc.shots", (project.findProperty("bc.shots") ?: "").toString()) }
    }
    lint {
        // 这些界面跑在【宿主】进程里，用不了模块自己的资源（见 AGENTS.md），文案只有中文、直接写在代码里，
        // 所以 setText 拼接文字是有意的（lint SetTextI18n）。
        disable += "SetTextI18n"
    }
}

dependencies {
    // 悬浮球工具直接用 core 的 Settings 定义（和 App 共用一份）。
    api(project(":core:model"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
