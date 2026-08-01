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
        // Change your mixin refmap name here:
        // modUtils.enableMixins 只接受 String 坐标，因此 mixinbooter 以字符串插值引用目录版本。
        val mixin =
            modUtils.enableMixins("zone.rong:mixinbooter:${libs.versions.mixinbooter.get()}", "mixins.$archivesBaseName.refmap.json") as String
        api(mixin) {
            isTransitive = false
        }
        compileOnly(libs.mixinextrasCommon)
        annotationProcessor(libs.mixinextrasCommon)
        annotationProcessor(libs.asmDebugAll)
        annotationProcessor(libs.guava)
        annotationProcessor(libs.gson)
        annotationProcessor(mixin) {
            isTransitive = false
        }
    }

    testImplementation(platform(libs.junitBom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
