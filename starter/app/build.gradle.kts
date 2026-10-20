plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * 发布签名：密钥库和口令都在仓库外，用环境变量传进来（scripts/signing-env.sh 设置，见 ClearKit docs/10-environment.md）。
 * 没设时用调试签名：能装能测，但换一台电脑构建的包不能覆盖安装。**签名密钥永远不要提交进仓库。**
 */
val releaseStore = System.getenv("MODULE_RELEASE_STORE_FILE")?.takeIf { file(it).isFile }

android {
    namespace = "io.github.clearkit.starter"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        // 模块的包名（生成脚本替换）：发布后不要再改，改了就是另一个应用
        applicationId = "io.github.clearkit.starter"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("mod.versionCode").get().toInt()
        versionName = providers.gradleProperty("mod.versionName").get()
        buildConfigField("String", "DEXKIT_VERSION", "\"${libs.versions.dexkit.get()}\"")
        // 只有 DexKit 带原生库；LSPosed 2.x 的设备都是 64 位 ARM（lint ChromeOsAbiSupport）。
        //noinspection ChromeOsAbiSupport
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (releaseStore != null) create("stable") {
            storeFile = file(releaseStore)
            storePassword = System.getenv("MODULE_RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("MODULE_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("MODULE_RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        val stable = signingConfigs.findByName("stable")
        debug {
            if (stable != null) signingConfig = stable
        }
        release {
            signingConfig = stable ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*.version", "META-INF/LICENSE*", "kotlin/**", "DebugProbesKt.bin")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // 界面截图测试（ScreenshotTest）只在 -Pbc.shots=<输出目录> 时跑，平时跳过
        unitTests.all {
            it.systemProperty("bc.shots", (project.findProperty("bc.shots") ?: "").toString())
            it.systemProperty("bc.shots.routes", (project.findProperty("bc.shots.routes") ?: "").toString())
        }
    }
}

dependencies {
    implementation(project(":hook:features"))
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    // 新版本【宿主】的本机适配（HostAdapter）
    implementation(libs.dexkit)
    implementation(libs.dexadapt.dexkit)
    implementation(libs.dexadapt.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
