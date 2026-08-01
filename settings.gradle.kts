pluginManagement {
    repositories {
        maven {
            // RetroFuturaGradle
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroup("com.gtnewhorizons.retrofuturagradle")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    // Automatic toolchain provisioning
    // 注：settings 的 plugins 块无法使用版本目录 alias（Gradle 9.4 实测报
    // “Unresolved reference 'libs'”），故此处保留字面量版本号，与
    // gradle/libs.versions.toml 的 [plugins].foojayResolverConvention 必须同步。
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// 版本目录由 gradle/libs.versions.toml 自动加载为 libs。

rootProject.name = "StackUpUp"

// T15：构建脚本模块化为 build-logic 复合构建，convention 插件经 includeBuild 解析。
includeBuild("build-logic")
