pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri(System.getenv("DEXKIT_MAVEN_REPO")
                ?: ((System.getenv("DEXKIT_SOURCE_HOME")
                    ?: (System.getProperty("user.home") + "/tools/dexkit-source")) + "/maven"))
            content {
                includeModule("org.luckypray", "dexkit")
                includeModule("com.google.flatbuffers", "flatbuffers-java")
            }
        }
        google()
        mavenCentral()
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "clearstarter"

// 通用适配库 DexAdapt（查询、解析器、dexlib / DexKit 数据源）：和本仓库并排放在 ../dexadapt，用复合构建直接引用源码。
includeBuild("../dexadapt") {
    dependencySubstitution {
        for (m in listOf("core", "dexlib", "dexkit", "dexkit-desktop", "runtime")) {
            substitute(module("io.github.wzgrx.dexadapt:dexadapt-$m")).using(project(":$m"))
        }
    }
}

include(":core:model")
include(":core:symbols")
include(":tools:symbolfinder")
include(":hook:runtime")
include(":hook:ui")
include(":hook:features")
include(":app")
