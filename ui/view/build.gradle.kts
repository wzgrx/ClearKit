// 净界家族共用界面库（宿主端，View）：跑在宿主进程里的界面（设置抽屉、工具盘、提示）用的颜色、尺寸，
// 和 App 端（../compose）同一份规范 v1。不依赖 Compose，也不带资源（宿主进程里用不了模块的资源）。
// 不单独构建。各模块在自己的 settings.gradle.kts 里引用这份源码：
//
//   include(":clearkit-view")
//   project(":clearkit-view").projectDir = file("../clear-kit/ui/view")
//
// 版本跟着模块自己的版本目录（libs）走：plugins android-library；versions compileSdk、compileSdkMinor、buildTools、minSdk。
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.wzgrx.clearkit.view"
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
}
