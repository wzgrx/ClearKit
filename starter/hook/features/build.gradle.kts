plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.clearkit.starter.hook.features"
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
    }
    lint {
        // 这些界面跑在【宿主】进程里，用不了模块自己的资源（见 AGENTS.md），文案只有中文、直接写在代码里，
        // 所以 setText 拼接文字是有意的（lint SetTextI18n）。
        disable += "SetTextI18n"
    }
}

dependencies {
    api(project(":hook:runtime"))
    api(project(":hook:ui"))
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
}
