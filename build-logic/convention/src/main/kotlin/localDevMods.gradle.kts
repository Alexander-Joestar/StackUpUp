// 本地开发模组依赖：三目录语义、ContainedDeps 判定、准备任务与接线、dev 依赖接入。
// 行为与重构前根脚本一致；.jar.disable 的 classpath wiring 语义不作“修正”。

import java.io.File
import java.util.jar.JarFile

plugins {
    // 需要 rfg.deobf 类型接入 dev 依赖；RFG 已由 minecraft 插件应用，实际应用按插件 id 去重。
    id("com.gtnewhorizons.retrofuturagradle")
}

fun localDevModRuntimeName(file: File): String =
    if (file.name.endsWith(".jar.disable")) {
        file.name.removeSuffix(".disable")
    } else {
        file.name
    }

fun jarManifestAttribute(
    file: File,
    attributeName: String,
): String? {
    if (!file.name.endsWith(".jar")) {
        return null
    }

    return runCatching {
        JarFile(file).use { jar ->
            jar.manifest?.mainAttributes?.getValue(attributeName)
        }
    }.getOrNull()
}

fun requiresFmlDirectoryScan(file: File): Boolean = !jarManifestAttribute(file, "ContainedDeps").isNullOrBlank()

val localDevModSourceFiles =
    buildList {
        addAll(
            fileTree("local-dev-mods") {
                include("*.jar")
            }.files,
        )
        addAll(
            fileTree("run/mods") {
                include("*.jar")
            }.files,
        )
        addAll(
            fileTree("run/mods") {
                include("*.jar.disable")
            }.files,
        )
    }.sortedBy { it.name }

val preparedLocalDevModDirectory = layout.buildDirectory.dir("generated/local-dev-mods")
val preparedLocalDevModFiles =
    localDevModSourceFiles.map { sourceFile ->
        preparedLocalDevModDirectory.get().file(localDevModRuntimeName(sourceFile)).asFile
    }

val compileOnlyLocalDevModFiles = localDevModSourceFiles.filter { it.name.endsWith(".jar") }
val runtimeLocalDevModSourceFiles =
    localDevModSourceFiles.filter { sourceFile ->
        sourceFile.name.endsWith(".jar") && sourceFile.parentFile?.name == "local-dev-mods"
    }
val scanOnlyLocalDevModSourceFiles = runtimeLocalDevModSourceFiles.filter(::requiresFmlDirectoryScan)
val runtimeClasspathLocalDevModSourceFiles = runtimeLocalDevModSourceFiles - scanOnlyLocalDevModSourceFiles
val copiedRuntimeLocalDevModFiles =
    runtimeClasspathLocalDevModSourceFiles.map { sourceFile ->
        preparedLocalDevModDirectory.get().file(localDevModRuntimeName(sourceFile)).asFile
    }

// dev 专用配置：只进本地开发编译/运行，不随模组发布。
val devOnlyNonPublishable =
    configurations.create("devOnlyNonPublishable") {
        description = "仅用于本地开发编译和运行、但不会发布的依赖。"
        isCanBeConsumed = false
        isCanBeResolved = false
    }
val runtimeOnlyNonPublishable =
    configurations.create("runtimeOnlyNonPublishable") {
        description = "仅用于开发运行时、不随本模组发布的依赖。"
        isCanBeConsumed = false
        isCanBeResolved = false
    }

configurations.named("compileOnly").configure { extendsFrom(devOnlyNonPublishable) }
configurations.named("runtimeClasspath").configure { extendsFrom(runtimeOnlyNonPublishable) }
configurations.named("testRuntimeClasspath").configure { extendsFrom(runtimeOnlyNonPublishable) }

val prepareLocalDevMods =
    tasks.register<Sync>("prepareLocalDevMods") {
        group = "build setup"
        description = "准备本地开发模组依赖，并兼容停用中的 .jar.disable 形式。"
        into(preparedLocalDevModDirectory)
        from("local-dev-mods") {
            include("*.jar")
        }
        from("run/mods") {
            include("*.jar")
        }
        from("run/mods") {
            include("*.jar.disable")
            rename("""(.+)\.disable$""", "$1")
        }
        includeEmptyDirs = false
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

val prepareScanOnlyLocalDevMods =
    tasks.register("prepareScanOnlyLocalDevMods") {
        group = "build setup"
        description = "把带 ContainedDeps 的本地开发模组复制到 run/mods，确保 FML 按目录扫描装载。"
        onlyIf { scanOnlyLocalDevModSourceFiles.isNotEmpty() }
        doLast {
            val runModsDirectory = file("run/mods")
            if (!runModsDirectory.exists()) {
                runModsDirectory.mkdirs()
            }

            for (sourceFile in scanOnlyLocalDevModSourceFiles) {
                sourceFile.copyTo(
                    target = runModsDirectory.resolve(localDevModRuntimeName(sourceFile)),
                    overwrite = true,
                )
            }
        }
    }

// dev 依赖接入：现成 jar 统一复制到 build/generated/local-dev-mods 后以 devOnly 方式接入。
// 1. 所有现成 jar 都进入编译类路径，保证 IDEA 与注解处理器可解析目标类；
// 2. 只有 local-dev-mods 下、且不依赖 FML 目录扫描语义的模组才额外注入运行时；
// 3. 带 ContainedDeps 的 jar（如 EnderCore）必须交给 run/mods 目录扫描装载，否则内嵌依赖
//    不会展开；run/mods 里的 jar 会被 FML 目录扫描发现，不能再走 classpath，避免重复装载。
dependencies {
    for (localDevMod in compileOnlyLocalDevModFiles) {
        add("devOnlyNonPublishable", rfg.deobf(project.files(localDevMod)))
    }

    for (localDevMod in copiedRuntimeLocalDevModFiles) {
        add("runtimeOnlyNonPublishable", rfg.deobf(project.files(localDevMod)))
    }
}

if (localDevModSourceFiles.isNotEmpty()) {
    tasks.withType<AbstractCompile>().configureEach {
        dependsOn(prepareLocalDevMods)
    }
    listOf("processResources", taskRunClient, taskRunServer, taskRunObfClient, taskRunObfServer).forEach { taskName ->
        tasks.named(taskName).configure {
            dependsOn(prepareLocalDevMods)
        }
    }
    if (scanOnlyLocalDevModSourceFiles.isNotEmpty()) {
        listOf(taskRunClient, taskRunServer, taskRunObfClient, taskRunObfServer).forEach { taskName ->
            tasks.named(taskName).configure {
                dependsOn(prepareScanOnlyLocalDevMods)
            }
        }
    }
}
