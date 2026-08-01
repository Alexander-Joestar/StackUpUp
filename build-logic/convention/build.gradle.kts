// build-logic/convention：全部 convention 插件的实现模块。
// kotlin-dsl 使脚本插件（src/main/kotlin/*.gradle.kts）可编译并注册为可应用插件；
// 仓库用于解析脚本 plugins 块声明的插件（RFG 在 GTNH Maven，其余在 Plugin Portal）。

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven {
        // RetroFuturaGradle
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
        mavenContent {
            includeGroup("com.gtnewhorizons")
            includeGroupByRegex("com\\.gtnewhorizons\\..+")
        }
    }
    mavenLocal()
}

// 预编译脚本插件（src/main/kotlin/*.gradle.kts）的 plugins 块不允许携带版本号，
// 插件类必须作为 implementation 依赖进入本模块编译类路径；应用时即按此依赖版本生效。
// 版本与根构建 gradle/libs.versions.toml 保持同步。
dependencies {
    // 实现构件坐标必须与对应 plugin marker pom 的 dependencies 声明一致（2026-08-08 实测核对）：
    // RFG 2.0.2 marker 声明 group 为 com.gtnewhorizons（非 com.gtnewhorizons.retrofuturagradle）；
    // idea-ext 1.1.7 marker 声明旧式 portal 坐标 gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext。
    implementation("com.gtnewhorizons:retrofuturagradle:2.0.2")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.3")
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.1.7")
}

// 八个 convention 插件按关注点拆分，均由脚本插件实现。
// 插件 id 以 stackupup.conventions.* 命名，根构建经 gradle/libs.versions.toml 的 alias 应用。
// implementationClass 必须是预编译脚本插件生成的 <文件名>Plugin 类（Gradle 9.4 实测，
// 如 jvm.gradle.kts -> JvmPlugin；Jvm_gradle 仅为脚本体类，不能作为插件应用）。
gradlePlugin {
    plugins {
        register("stackupup-jvm") {
            id = "stackupup.conventions.jvm"
            implementationClass = "JvmPlugin"
        }
        register("stackupup-repositories") {
            id = "stackupup.conventions.repositories"
            implementationClass = "RepositoriesPlugin"
        }
        register("stackupup-spotless") {
            id = "stackupup.conventions.spotless"
            implementationClass = "SpotlessPlugin"
        }
        register("stackupup-test") {
            id = "stackupup.conventions.test"
            implementationClass = "TestPlugin"
        }
        register("stackupup-idea") {
            id = "stackupup.conventions.idea"
            implementationClass = "IdeaPlugin"
        }
        register("stackupup-minecraft") {
            id = "stackupup.conventions.minecraft"
            implementationClass = "MinecraftPlugin"
        }
        register("stackupup-auto-test") {
            id = "stackupup.conventions.auto-test"
            implementationClass = "AutoTestPlugin"
        }
        register("stackupup-local-dev-mods") {
            id = "stackupup.conventions.local-dev-mods"
            implementationClass = "LocalDevModsPlugin"
        }
    }
}
