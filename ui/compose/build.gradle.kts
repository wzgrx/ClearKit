// 净界家族共用界面库（App 端，Compose）：主题、字号、图标、动效、组件、外壳（底栏、页头、页面栈）。
// 不单独构建。各模块在自己的 settings.gradle.kts 里引用这份源码（ClearKit 和模块仓库并排放）：
//
//   include(":clearkit-ui")
//   project(":clearkit-ui").projectDir = file("../clear-kit/ui/compose")
//
// 版本跟着模块自己的版本目录（libs）走，用到的条目：plugins android-library、kotlin-compose；
// versions compileSdk、compileSdkMinor、buildTools、minSdk；libraries androidx-compose-bom、
// androidx-compose-ui、androidx-compose-foundation、androidx-compose-material3、androidx-activity-compose、
// backdrop、kyant-shapes。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.wzgrx.clearkit.ui"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.activity.compose)
    // 页面栈给每页自己的生命周期和 ViewModel 存储（同 Jetpack Navigation 的每个目的地）
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    // 液态玻璃（底栏、浮层）和它要的圆角形状
    api(libs.backdrop)
    api(libs.kyant.shapes)
}
