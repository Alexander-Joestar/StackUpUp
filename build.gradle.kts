import org.gradle.api.internal.artifacts.dsl.dependencies.DependenciesExtensionModule.module
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.compiler
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import java.io.File
import java.util.Locale
import java.util.Locale.getDefault
import java.util.jar.JarFile

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlinVersion.get()}")
    }
}

plugins {
    id("java")
    id("java-library")
    kotlin("jvm") version libs.versions.kotlinVersion
    id("maven-publish")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
    id("eclipse")
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.2"
    id("com.matthewprenger.cursegradle") version "1.4.0"
    id("com.diffplug.spotless") version "7.0.3"
}

@Suppress("PropertyName")
val mod_version: String by project

@Suppress("PropertyName")
val maven_group: String by project

@Suppress("PropertyName")
val mod_id: String by project

@Suppress("PropertyName")
val archives_base_name: String by project
group = maven_group
version = mod_version

@Suppress("PropertyName")
val forgelin_continuous_version: String by project

@Suppress("PropertyName")
val use_access_transformer: String by project

@Suppress("PropertyName")
val use_mixins: String by project

@Suppress("PropertyName")
val use_coremod: String by project

@Suppress("PropertyName")
val use_assetmover: String by project

@Suppress("PropertyName")
val include_mod: String by project

@Suppress("PropertyName")
val coremod_plugin_class_name: String by project

fun compatibleGradleProperty(
    newName: String,
    legacyName: String,
): String? = providers.gradleProperty(newName).orNull ?: providers.gradleProperty(legacyName).orNull

val autoTestRequestedTasks = setOf("runClientAutoTest", "runServerAutoTest")
val autoTestRequestedByTask = gradle.startParameter.taskNames.any { it in autoTestRequestedTasks }
val autoTestRequestedByProperty =
    compatibleGradleProperty("stackupupDevAutoTest", "stackupDevAutoTest")?.toBoolean() == true
val autoTestMode =
    when {
        gradle.startParameter.taskNames.any { it == "runServerAutoTest" } -> "server"
        gradle.startParameter.taskNames.any { it == "runClientAutoTest" } -> "client"
        else -> compatibleGradleProperty("stackupupDevAutoTestMode", "stackupDevAutoTestMode")
    }
val enableDevAutoTest = autoTestRequestedByTask || autoTestRequestedByProperty
val autoTestOre = compatibleGradleProperty("stackupupDevAutoTestOre", "stackupDevAutoTestOre") ?: "ingotSteel"
val autoTestRule =
    compatibleGradleProperty("stackupupDevAutoTestRule", "stackupDevAutoTestRule") ?: "ore = $autoTestOre -> 1024"
val autoTestItem = compatibleGradleProperty("stackupupDevAutoTestItem", "stackupDevAutoTestItem") ?: ""
val autoTestMeta = compatibleGradleProperty("stackupupDevAutoTestMeta", "stackupDevAutoTestMeta") ?: "11305"
val autoTestCount = compatibleGradleProperty("stackupupDevAutoTestCount", "stackupDevAutoTestCount") ?: "128"
val autoTestWorldFolder =
    compatibleGradleProperty("stackupupDevAutoTestWorldFolder", "stackupDevAutoTestWorldFolder") ?: "stackupup_dev_autotest"
val autoTestWorldName =
    compatibleGradleProperty("stackupupDevAutoTestWorldName", "stackupDevAutoTestWorldName") ?: "StackUpUp 自动测试"
val autoTestServerPort =
    compatibleGradleProperty("stackupupDevAutoTestServerPort", "stackupDevAutoTestServerPort") ?: "0"
val autoTestMatrix = compatibleGradleProperty("stackupupDevAutoTestMatrix", "stackupDevAutoTestMatrix") ?: "false"

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

val prepareAutoTestServerFiles =
    tasks.register("prepareAutoTestServerFiles") {
        group = "build setup"
        description = "为服务端自动验收准备无交互启动所需的 EULA 与基础配置。"
        doLast {
            val runDirectory = file("run")
            if (!runDirectory.exists()) {
                runDirectory.mkdirs()
            }

            val eulaFile = runDirectory.resolve("eula.txt")
            eulaFile.writeText(
                """
                # 自动生成：仅用于本地开发自动验收
                eula=true
                """.trimIndent() + System.lineSeparator(),
                Charsets.UTF_8,
            )

            val serverPropertiesFile = runDirectory.resolve("server.properties")
            val existingLines =
                if (serverPropertiesFile.exists()) {
                    serverPropertiesFile.readLines(Charsets.UTF_8)
                } else {
                    emptyList()
                }

            val filteredLines =
                existingLines
                    .filterNot { it.startsWith("online-mode=") }
                    .filterNot { it.startsWith("server-port=") }
                    .filterNot { it.startsWith("level-name=") }
            val finalLines =
                buildList {
                    add("online-mode=false")
                    add("server-port=$autoTestServerPort")
                    add("level-name=$autoTestWorldFolder")
                    addAll(filteredLines)
                }
            serverPropertiesFile.writeText(
                finalLines.joinToString(separator = System.lineSeparator(), postfix = System.lineSeparator()),
                Charsets.UTF_8,
            )
        }
    }

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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    // Generate sources and javadocs jars when building and publishing
    withSourcesJar()
    // withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

configurations {
    val embed = create("embed")
    val runtimeOnlyNonPublishable =
        create("runtimeOnlyNonPublishable") {
            description = "仅用于开发运行时、不随本模组发布的依赖。"
            isCanBeConsumed = false
            isCanBeResolved = false
        }
    val devOnlyNonPublishable =
        create("devOnlyNonPublishable") {
            description = "仅用于本地开发编译和运行、但不会发布的依赖。"
            isCanBeConsumed = false
            isCanBeResolved = false
        }

    implementation.configure {
        extendsFrom(embed)
    }
    compileOnly.configure {
        extendsFrom(devOnlyNonPublishable)
    }
    runtimeClasspath.configure {
        extendsFrom(runtimeOnlyNonPublishable)
    }
    testRuntimeClasspath.configure {
        extendsFrom(runtimeOnlyNonPublishable)
    }
}

if (localDevModSourceFiles.isNotEmpty()) {
    tasks.withType<AbstractCompile>().configureEach {
        dependsOn(prepareLocalDevMods)
    }
    listOf("processResources", "runClient", "runServer", "runObfClient", "runObfServer").forEach { taskName ->
        tasks.named(taskName).configure {
            dependsOn(prepareLocalDevMods)
        }
    }
    if (scanOnlyLocalDevModSourceFiles.isNotEmpty()) {
        listOf("runClient", "runServer", "runObfClient", "runObfServer").forEach { taskName ->
            tasks.named(taskName).configure {
                dependsOn(prepareScanOnlyLocalDevMods)
            }
        }
    }
}

if (enableDevAutoTest && autoTestMode == "server") {
    tasks.named("runServer").configure {
        dependsOn(prepareAutoTestServerFiles)
    }
}

minecraft {
    mcVersion.set("1.12.2")

    // MCP Mappings
    mcpMappingChannel.set("stable")
    mcpMappingVersion.set("39")

    // Set username here, the UUID will be looked up automatically
    username.set("Developer")

    // Add any additional tweaker classes here
    // extraTweakClasses.add("org.spongepowered.asm.launch.MixinTweaker")

    // Add various JVM arguments here for runtime
    val args = mutableListOf("-ea:$group")
    if (use_coremod.toBoolean()) {
        args += "-Dfml.coreMods.load=$coremod_plugin_class_name"
    }
    if (use_mixins.toBoolean()) {
        args += "-Dmixin.hotSwap=true"
        args += "-Dmixin.checks.interfaces=true"
        args += "-Dmixin.debug.export=true"
    }
    if (enableDevAutoTest) {
        // 开发期自动验收默认关闭。
        // 显式通过 -PstackupupDevAutoTest=true 或 run*AutoTest 任务开启。
        args += "-Dstackupup.dev.autoTest.enabled=true"
        args += "-Dstackupup.dev.autoTest.mode=${autoTestMode ?: "client"}"
        args += "-Dstackupup.dev.autoTest.ore=$autoTestOre"
        args += "-Dstackupup.dev.autoTest.rule=$autoTestRule"
        args += "-Dstackupup.dev.autoTest.item=$autoTestItem"
        args += "-Dstackupup.dev.autoTest.meta=$autoTestMeta"
        args += "-Dstackupup.dev.autoTest.count=$autoTestCount"
        args += "-Dstackupup.dev.autoTest.worldFolder=$autoTestWorldFolder"
        args += "-Dstackupup.dev.autoTest.worldName=$autoTestWorldName"
        args += "-Dstackupup.dev.autoTest.matrix=$autoTestMatrix"
        args += "-Dstackupup.dev.autoTest.autoShutdown=true"
        args += "-Dstackupup.dev.autoTest.failFast=true"
        args += "-Dstackupup.dev.autoTest.clearInventoryBeforeGive=true"
    }
    extraRunJvmArguments.addAll(args)

    // Include and use dependencies' Access Transformer files
    useDependencyAccessTransformers.set(true)

    // Add any properties you want to swap out for a dynamic value at build time here
    // Any properties here will be added to a class at build time, the name can be configured below
    // Example:
    injectedTags.put("VERSION", mod_version)
    injectedTags.put("MOD_ID", mod_id)
    injectedTags.put("MOD_NAME", archives_base_name)
}

// Generate a group.archives_base_name.Tags class
tasks.injectTags.configure {
    // Change Tags class' name here:
    outputClassName.set("io.alexjoest.stackupup.Tags")
}

tasks.named("compileInjectedTagsKotlin").configure {
    dependsOn("injectTags")
}

tasks.named("compileMcLauncherKotlin").configure {
    dependsOn("createMcLauncherFiles")
}

tasks.named("compilePatchedMcKotlin").configure {
    dependsOn("decompressDecompiledSources")
}

repositories {
    mavenCentral()
    maven {
        name = "CleanroomMC Maven"
        url = uri("https://maven.cleanroommc.com")
    }
    maven {
        name = "SpongePowered Maven"
        url = uri("https://repo.spongepowered.org/maven")
    }
    maven {
        name = "CurseMaven"
        url = uri("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    mavenLocal() // Must be last for caching to work
}

dependencies {
    implementation("io.github.chaosunity.forgelin:Forgelin-Continuous:${forgelin_continuous_version}") {
        exclude("net.minecraftforge")
    }

    if (use_assetmover.toBoolean()) {
        implementation("com.cleanroommc:assetmover:2.5")
    }

    // Example of deobfuscating a dependency
    // implementation rfg.deobf("curse.maven:had-enough-items-557549:4543375")
    // 本地开发版模组入口：
    // 1. 优先读取 local-dev-mods/*.jar；
    // 2. 兼容 run/mods/*.jar.disable，便于把发行包“停用但继续参与开发类路径”；
    // 3. 统一复制到 build/generated/local-dev-mods，再以 devOnly 方式接入。
    // 所有现成 jar 都进入编译类路径，保证 IDEA 与注解处理器可解析目标类。
    for (localDevMod in compileOnlyLocalDevModFiles) {
        add("devOnlyNonPublishable", rfg.deobf(project.files(localDevMod)))
    }

    // 只有 local-dev-mods 下、且不依赖 FML 目录扫描语义的模组才额外注入运行时。
    // 带 ContainedDeps 的 jar（如 EnderCore）必须交给 run/mods 目录扫描装载，
    // 否则内嵌依赖不会展开，coremod/plugin 类会在运行时缺失。
    // run/mods 里的 jar 会被 FML 目录扫描发现，不能再走 classpath，避免重复装载。
    for (localDevMod in copiedRuntimeLocalDevModFiles) {
        add("runtimeOnlyNonPublishable", rfg.deobf(project.files(localDevMod)))
    }

    if (use_mixins.toBoolean()) {
        // Change your mixin refmap name here:
        val mixin =
            modUtils.enableMixins("zone.rong:mixinbooter:10.7", "mixins.${archives_base_name}.refmap.json") as String
        api(mixin) {
            isTransitive = false
        }
        compileOnly("io.github.llamalad7:mixinextras-common:0.5.0")
        annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.0")
        annotationProcessor("org.ow2.asm:asm-debug-all:5.2")
        annotationProcessor("com.google.guava:guava:24.1.1-jre")
        annotationProcessor("com.google.code.gson:gson:2.8.6")
        annotationProcessor(mixin) {
            isTransitive = false
        }
    }

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Adds Access Transformer files to tasks
@Suppress("Deprecation")
if (use_access_transformer.toBoolean()) {
    for (at in sourceSets.getByName("main").resources.files) {
        if (at.name.lowercase(getDefault()).endsWith("_at.cfg")) {
            tasks.deobfuscateMergedJarToSrg
                .get()
                .accessTransformerFiles
                .from(at)
            tasks.srgifyBinpatchedJar
                .get()
                .accessTransformerFiles
                .from(at)
        }
    }
}

@Suppress("UnstableApiUsage")
tasks.withType<ProcessResources> {
    // This will ensure that this task is redone when the versions change
    inputs.property("version", mod_version)
    inputs.property("mcversion", minecraft.mcVersion)

    // Replace various properties in mcmod.info and pack.mcmeta if applicable
    filesMatching(arrayListOf("mcmod.info", "pack.mcmeta")) {
        expand(
            "version" to mod_version,
            "mcversion" to minecraft.mcVersion,
        )
    }

    if (use_access_transformer.toBoolean()) {
        rename("(.+_at.cfg)", "META-INF/$1") // Make sure Access Transformer files are in META-INF folder
    }
}

tasks.withType<Jar> {
    manifest {
        val attributeMap = mutableMapOf<String, String>()
        if (use_coremod.toBoolean()) {
            attributeMap["FMLCorePlugin"] = coremod_plugin_class_name
            if (include_mod.toBoolean()) {
                attributeMap["FMLCorePluginContainsFMLMod"] = true.toString()
                attributeMap["ForceLoadAsMod"] =
                    project.gradle.startParameter.taskNames
                        .any { it == "build" }
                        .toString()
            }
        }
        if (use_access_transformer.toBoolean()) {
            attributeMap["FMLAT"] = archives_base_name + "_at.cfg"
        }
        attributes(attributeMap)
    }
    // Add all embedded dependencies into the jar
    from(
        provider {
            configurations.getByName("embed").map {
                if (it.isDirectory()) it else zipTree(it)
            }
        },
    )
}

idea {
    module {
        inheritOutputDirs = true
    }
    project {
        settings {
            runConfigurations {
                add(
                    Gradle("1. Run Client").apply {
                        setProperty("taskNames", listOf("runClient"))
                    },
                )
                add(
                    Gradle("2. Run Server").apply {
                        setProperty("taskNames", listOf("runServer"))
                    },
                )
                add(
                    Gradle("2a. Run Server AutoTest Matrix").apply {
                        setProperty("taskNames", listOf("runServerAutoTestMatrix"))
                    },
                )
                add(
                    Gradle("3. Run Obfuscated Client").apply {
                        setProperty("taskNames", listOf("runObfClient"))
                    },
                )
                add(
                    Gradle("4. Run Obfuscated Server").apply {
                        setProperty("taskNames", listOf("runObfServer"))
                    },
                )
            }
            compiler.javac {
                afterEvaluate {
                    javacAdditionalOptions = "-encoding utf8"
                    moduleJavacAdditionalOptions =
                        mutableMapOf(
                            (project.name + ".main") to
                                tasks.compileJava
                                    .get()
                                    .options.compilerArgs
                                    .joinToString(" ") { "\"$it\"" },
                        )
                }
            }
        }
    }
}

tasks.named("processIdeaSettings").configure {
    dependsOn("injectTags")
}

val isWindows: Boolean = System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")

fun gradlew(vararg args: String): List<String> =
    if (isWindows) {
        listOf("cmd", "/c", ".\\gradlew.bat", *args)
    } else {
        listOf("./gradlew", *args)
    }

// ...existing code...

tasks.register("runClientAutoTest") {
    group = "modded minecraft"
    description = "以开发自动验收模式运行客户端。"
    dependsOn("runClient")
}

tasks.register("runServerAutoTest") {
    group = "modded minecraft"
    description = "以开发自动验收模式运行服务端。"
    dependsOn("runServer")
}

tasks.register<Exec>("runServerAutoTestMatrix") {
    group = "modded minecraft"
    description = "在单次服务端启动中运行核心自动验收矩阵。"
    workingDir = project.projectDir
    commandLine(
        gradlew(
            "--no-daemon",
            "runServerAutoTest",
            "-PstackupupDevAutoTest=true",
            "-PstackupupDevAutoTestMode=server",
            "-PstackupupDevAutoTestMatrix=true",
            "-PstackupupDevAutoTestRule=",
            "-PstackupupDevAutoTestServerPort=0",
            "-PstackupupDevAutoTestWorldFolder=stackupup_dev_autotest_matrix",
            "-PstackupupDevAutoTestWorldName=StackUpUp 自动测试 Matrix",
            "-PstackupupDevAutoTestCount=128",
            "--stacktrace",
        ),
    )
}

tasks.register<Exec>("runServerAutoTestIngotSteel") {
    group = "modded minecraft"
    description = "运行 IngotSteel 服务端自动验收样例。"
    workingDir = project.projectDir
    commandLine(
        gradlew(
            "--no-daemon",
            "runServerAutoTest",
            "-PstackupupDevAutoTest=true",
            "-PstackupupDevAutoTestMode=server",
            "-PstackupupDevAutoTestOre=ingotSteel",
            "-PstackupupDevAutoTestRule=ore = ingotSteel -> 1024",
            "-PstackupupDevAutoTestServerPort=0",
            "-PstackupupDevAutoTestWorldFolder=stackupup_dev_autotest_ingotsteel",
            "-PstackupupDevAutoTestWorldName=StackUpUp 自动测试 IngotSteel",
            "-PstackupupDevAutoTestCount=128",
            "--stacktrace",
        ),
    )
}

tasks.register<Exec>("runServerAutoTestPlateSteel") {
    group = "modded minecraft"
    description = "运行 PlateSteel 服务端自动验收样例。"
    workingDir = project.projectDir
    commandLine(
        gradlew(
            "--no-daemon",
            "runServerAutoTest",
            "-PstackupupDevAutoTest=true",
            "-PstackupupDevAutoTestMode=server",
            "-PstackupupDevAutoTestOre=plateSteel",
            "-PstackupupDevAutoTestRule=ore = plateSteel -> 1024",
            "-PstackupupDevAutoTestServerPort=0",
            "-PstackupupDevAutoTestWorldFolder=stackupup_dev_autotest_platesteel",
            "-PstackupupDevAutoTestWorldName=StackUpUp 自动测试 PlateSteel",
            "-PstackupupDevAutoTestCount=128",
            "--stacktrace",
        ),
    )
}

tasks.register<Exec>("runServerAutoTestDustSteel") {
    group = "modded minecraft"
    description = "运行 DustSteel 服务端自动验收样例。"
    workingDir = project.projectDir
    commandLine(
        gradlew(
            "--no-daemon",
            "runServerAutoTest",
            "-PstackupupDevAutoTest=true",
            "-PstackupupDevAutoTestMode=server",
            "-PstackupupDevAutoTestOre=dustSteel",
            "-PstackupupDevAutoTestRule=ore = dustSteel -> 1024",
            "-PstackupupDevAutoTestServerPort=0",
            "-PstackupupDevAutoTestWorldFolder=stackupup_dev_autotest_duststeel",
            "-PstackupupDevAutoTestWorldName=StackUpUp 自动测试 DustSteel",
            "-PstackupupDevAutoTestCount=128",
            "--stacktrace",
        ),
    )
}

tasks.register<Exec>("runServerAutoTestVacuumTube") {
    group = "modded minecraft"
    description = "运行 VacuumTube 服务端自动验收样例。"
    workingDir = project.projectDir
    commandLine(
        gradlew(
            "--no-daemon",
            "runServerAutoTest",
            "-PstackupupDevAutoTest=true",
            "-PstackupupDevAutoTestMode=server",
            "-PstackupupDevAutoTestItem=gregtech:meta_item_1",
            "-PstackupupDevAutoTestMeta=516",
            "-PstackupupDevAutoTestRule=item = gregtech:meta_item_1 && meta = 516 -> 512",
            "-PstackupupDevAutoTestServerPort=0",
            "-PstackupupDevAutoTestWorldFolder=stackupup_dev_autotest_vacuumtube",
            "-PstackupupDevAutoTestWorldName=StackUpUp 自动测试 VacuumTube",
            "-PstackupupDevAutoTestCount=128",
            "--stacktrace",
        ),
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// ── formatting & line ending enforcement ─────────────────────────
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "indent_size" to "4",
                    "max_line_length" to "160",
                    "ktlint_code_style" to "intellij_idea",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }

    java {
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }

    format("misc") {
        target(".gitattributes", ".gitignore", ".editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// 安装 git pre-push hook → 推送前自动检查格式
// ./gradlew spotlessInstallGitPrePushHook
