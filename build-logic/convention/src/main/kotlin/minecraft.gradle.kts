// RFG 接入：minecraft{}、AT、ProcessResources、jar manifest、injectTags 与 Kotlin 编译接线。
// 行为与重构前根脚本对应段落一致，逐项搬运；autoTest 的 -D 注入已移交 autoTest.gradle.kts。

import java.util.Locale.getDefault

plugins {
    id("com.gtnewhorizons.retrofuturagradle")
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
    val args = mutableListOf("-ea:$mavenGroup")
    if (useCoremod) {
        args += "-Dfml.coreMods.load=$coremodPluginClassName"
    }
    if (useMixins) {
        args += "-Dmixin.hotSwap=true"
        args += "-Dmixin.checks.interfaces=true"
        args += "-Dmixin.debug.export=true"
    }
    extraRunJvmArguments.addAll(args)

    // Include and use dependencies' Access Transformer files
    useDependencyAccessTransformers.set(true)

    // Add any properties you want to swap out for a dynamic value at build time here
    // Any properties here will be added to a class at build time, the name can be configured below
    injectedTags.put("VERSION", modVersion)
    injectedTags.put("MOD_ID", modId)
    injectedTags.put("MOD_NAME", archivesBaseName)
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

// Adds Access Transformer files to tasks
@Suppress("Deprecation")
if (useAccessTransformer) {
    for (at in extensions
        .getByType<SourceSetContainer>()
        .getByName("main")
        .resources.files) {
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
    inputs.property("version", modVersion)
    inputs.property("mcversion", minecraft.mcVersion)

    // Replace various properties in mcmod.info and pack.mcmeta if applicable
    filesMatching(arrayListOf("mcmod.info", "pack.mcmeta")) {
        expand(
            "version" to modVersion,
            "mcversion" to minecraft.mcVersion,
        )
    }

    if (useAccessTransformer) {
        rename("(.+_at.cfg)", "META-INF/$1") // Make sure Access Transformer files are in META-INF folder
    }
}

tasks.withType<Jar> {
    manifest {
        val attributeMap = mutableMapOf<String, String>()
        if (useCoremod) {
            attributeMap["FMLCorePlugin"] = coremodPluginClassName
            if (includeMod) {
                attributeMap["FMLCorePluginContainsFMLMod"] = true.toString()
                attributeMap["ForceLoadAsMod"] =
                    project.gradle.startParameter.taskNames
                        .any { it == "build" }
                        .toString()
            }
        }
        if (useAccessTransformer) {
            attributeMap["FMLAT"] = archivesBaseName + "_at.cfg"
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
