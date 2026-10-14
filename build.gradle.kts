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
    id("eclipse")
    alias(libs.plugins.cursegradle)
    // build-logic convention 插件（T15：构建脚本 build-logic 模块化重构，实现见 build-logic/）
    alias(libs.plugins.conventionsJvm)
    alias(libs.plugins.conventionsRepositories)
    alias(libs.plugins.conventionsSpotless)
    alias(libs.plugins.conventionsTest)
    alias(libs.plugins.conventionsIdea)
    alias(libs.plugins.conventionsMinecraft)
    alias(libs.plugins.conventionsAutoTest)
    alias(libs.plugins.conventionsLocalDevMods)
}

group = mavenGroup
version = modVersion

// refmap 名是生成端（MixinObfuscationProcessor 的 -AoutRefMapFile）与消费端（src/main/resources 下
// 17 个 mixins.stackupup.*.json 的 refmap 字段）共用的唯一真值来源：下面把它同时交给 enableMixins
// 与 processResources 的 expand，改 archives_base_name 时两端一起变，不再出现「jar 内 refmap 换了名、
// 配置仍指向旧名」的静默失效。
val mixinRefmapName = "mixins.$archivesBaseName.refmap.json"

configurations {
    val embed = create("embed")

    implementation.configure {
        extendsFrom(embed)
    }
}

dependencies {
    implementation("io.github.chaosunity.forgelin:Forgelin-Continuous:$forgelinContinuousVersion") {
        exclude("net.minecraftforge")
    }

    if (useAssetmover) {
        implementation(libs.assetmover)
    }

    if (useMixins) {
        // Change your mixin refmap name here (见文件顶部 mixinRefmapName：单一真值来源)。
        // modUtils.enableMixins 只接受 String 坐标，因此 mixinbooter 以字符串插值引用目录版本。
        val mixin =
            modUtils.enableMixins("zone.rong:mixinbooter:${libs.versions.mixinbooter.get()}", mixinRefmapName) as String
        api(mixin) {
            isTransitive = false
        }
        annotationProcessor(libs.asmDebugAll)
        annotationProcessor(libs.guava)
        annotationProcessor(libs.gson)
        // refmap 修复（8.6.1）：mixinbooter 11.17 的 META-INF/services 只注册 MixinExtrasAP，
        // obfuscation AP（MixinObfuscationProcessor{Injection,Targets}）在独立 cleanmix 0.7.2 中；
        // 此处仅编译期挂载 AP（对应 10.7 基线 asm/guava/gson 同为 AP-only），不进 implementation/runtime。
        annotationProcessor(libs.cleanmix)
        annotationProcessor(mixin) {
            isTransitive = false
        }
    }

    testImplementation(platform(libs.junitBom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// refmap 打包契约：17 个 mixin 配置的 refmap 字段不再硬编码名字，而是由 processResources 用与
// enableMixins 同一个 mixinRefmapName 插值。expand 必须收到已解析的字面量——传 Provider 只会把
// toString() 结果写进产物（参见 mcmod.info 的 mcversion 回归）。
// 排除 refmap 自身：archives_base_name 恰为全小写 mod_id 时其文件名会落进同一 glob，而 refmap 是
// 编译期 AP 生成的 JSON，应保持逐字节原样、不经过模板引擎。Gradle Kotlin DSL 对 Action<T> 用
// sam-with-receiver，lambda 的接收者是 FileCopyDetails，this.name 即待处理文件名。
tasks.named<ProcessResources>("processResources") {
    filesMatching("mixins.$modId.*.json") {
        if (this.name != mixinRefmapName) {
            expand("refmap" to mixinRefmapName)
        }
    }
}

// 产物级结构检查读取打包 jar：Gradle 的 test 默认不依赖 jar，不接线就会让「jar 未构建」变成静默跳过。
// 这里把 jar 任务的产物路径以系统属性交给测试，测试不再自己拼文件名字符串（jar 的文件名来自
// Gradle 项目名 + 版本 + RFG 的 "dev" classifier，与 archives_base_name 并不同源）。
tasks.named<Test>("test") {
    val packagedJar = tasks.named<Jar>("jar")
    dependsOn(packagedJar)
    // 用 archiveFileName（配置期可解析的属性）拼路径；archiveFile 是任务产物 Provider，
    // 在配置期取值会触发 "before task has completed" 失败。
    val packagedJarFile = packagedJar.get().destinationDirectory.file(packagedJar.get().archiveFileName)
    systemProperty("stackupup.packagedJar", packagedJarFile.get().asFile.absolutePath)
}
