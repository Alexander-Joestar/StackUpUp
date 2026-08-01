// build-logic：本仓库的 Gradle 复合构建，承载全部 convention 插件。
// 插件实现位于 :convention 模块，由根构建通过 includeBuild("build-logic") 引用。

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            // RetroFuturaGradle：convention 脚本的 plugins 块在编译期解析插件类需要 GTNH Maven。
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        mavenLocal()
    }
}

rootProject.name = "build-logic"

include(":convention")
