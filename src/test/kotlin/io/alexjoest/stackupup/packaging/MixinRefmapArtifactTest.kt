package io.alexjoest.stackupup.packaging

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile
import kotlin.streams.toList

/**
 * 打包 jar 的 refmap 契约与 manifest 入口结构检查（非行为验证：只读产物字节，不验证运行时装载/Mixin 实际生效）。
 *
 * 回归背景：`build.gradle.kts` 用 `mixins.$archivesBaseName.refmap.json` 生成 refmap，而
 * `src/main/resources/mixins.stackupup.*.json` 的 `refmap` 字段原先硬编码 `mixins.StackUpUp.refmap.json`。
 * 默认 `archives_base_name=StackUpUp` 时二者巧合一致；`-Parchives_base_name=RenamedMod` 下构建仍成功、
 * jar 内 refmap 已改名、17 个配置却仍指向旧名 → 全部 Mixin 无法 remap 注入点，且没有任何测试失败。
 *
 * 断言的真值来源是**产物本身**而非构建脚本副本：先从 jar 里找出唯一的 `mixins.*.refmap.json` 条目，
 * 再要求每个打包 mixin 配置都指向它。这样硬编码名字、生成端与消费端任何一侧漂移都会直接失败。
 *
 * 覆盖项（任一失败都显式失败，不静默跳过）：
 * 1. 每个打包 mixin 配置的 `refmap` 值指向 jar 内存在且内容非空的 refmap 条目；
 * 2. refmap 的 `mappings` 键在同 jar 内可解析为 .class（防止把空壳/错文件当 refmap）；
 * 3. MANIFEST 的 `MixinConnector` / `FMLCorePlugin` 值的类字节在同 jar 内存在；
 * 4. 打包 JSON 资源不含未替换的 `${...}` 占位符（防止 expand 漏替换或写入 Provider 的 toString）。
 *
 * 该检查读取的是 `jar` 任务产物（dev jar）；发布用的 reobf jar 与之条目集相同，由 `assemble`/`reobfJar` 承担。
 */
class MixinRefmapArtifactTest {

    @Test
    fun packagedMixinConfigs_shouldAllReferenceThePackagedRefmap() {
        val refmapName = packagedRefmapName()
        val configs = packagedMixinConfigs()

        assertTrue(configs.isNotEmpty(), "打包 jar 内没有任何 mixin 配置: $PACKAGED_JAR")
        for ((config, refmap) in configs) {
            assertEquals(
                refmapName,
                refmap,
                "配置 $config 的 refmap 值与 jar 内实际生成的 refmap 不一致，改名后该配置的注入点无法 remap",
            )
        }
    }

    @Test
    fun packagedRefmap_shouldBeNonEmptyAndResolveMappingsToPackagedClasses() {
        val refmapName = packagedRefmapName()
        val mappings = refmapMappingKeys(refmapName)

        assertTrue(mappings.isNotEmpty(), "refmap $refmapName 的 mappings 为空，注入点无法 remap")
        val unresolvable = mappings.filterNot { packagedEntryBytes("$it.class").isNotEmpty() }.sorted()
        assertEquals(
            emptyList<String>(),
            unresolvable,
            "refmap $refmapName 的 mappings 指向 jar 内不存在的类: $unresolvable",
        )
    }

    @Test
    fun packagedManifest_shouldPointEntryClassesAtPackagedClasses() {
        val manifest = JarFile(PACKAGED_JAR.toFile()).use { it.manifest }
        for (attribute in listOf("MixinConnector", "FMLCorePlugin")) {
            val className = manifest?.mainAttributes?.getValue(attribute)
            assertTrue(!className.isNullOrBlank(), "MANIFEST 缺少 $attribute 属性: $PACKAGED_JAR")
            val binaryPath = requireNotNull(className).replace('.', '/')
            assertTrue(
                packagedEntryBytes("$binaryPath.class").isNotEmpty(),
                "MANIFEST 的 $attribute=$className 在 jar 内没有对应类字节",
            )
        }
    }

    @Test
    fun packagedJsonResources_shouldHaveNoUnresolvedPlaceholders() {
        JarFile(PACKAGED_JAR.toFile()).use { jar ->
            for (entry in jar.entries()) {
                if (entry.isDirectory || !entry.name.endsWith(".json")) {
                    continue
                }
                val text = String(jar.getInputStream(entry).use { it.readBytes() }, StandardCharsets.UTF_8)
                assertFalse(
                    text.contains("\${"),
                    "打包资源 ${entry.name} 仍含未替换的 \${...} 占位符:\n$text",
                )
            }
        }
    }

    /** jar 内唯一的 refmap 条目名；缺失、真空或重复都直接失败。 */
    private fun packagedRefmapName(): String {
        val entries = JarFile(PACKAGED_JAR.toFile()).use { jar ->
            jar.entries().asSequence().map { it.name }.filter { REFMAP_ENTRY.matches(it) }.toList()
        }
        assertEquals(1, entries.size, "打包 jar 内应有且仅有一个 mixins.*.refmap.json 条目，实际: $entries")
        val refmapName = entries.single()
        assertTrue(packagedEntryBytes(refmapName).isNotEmpty(), "refmap $refmapName 内容为空")
        return refmapName
    }

    /** 打包的 mixin 配置（排除 refmap 自身）→ 其 refmap 字段值。 */
    private fun packagedMixinConfigs(): Map<String, String> = buildMap {
        JarFile(PACKAGED_JAR.toFile()).use { jar ->
            for (entry in jar.entries()) {
                if (entry.isDirectory || !entry.name.startsWith("mixins.") || REFMAP_ENTRY.matches(entry.name)) {
                    continue
                }
                val text = String(jar.getInputStream(entry).use { it.readBytes() }, StandardCharsets.UTF_8)
                val refmap = jsonObject(text)["refmap"]?.takeIf { it.isJsonPrimitive }?.asString
                assertTrue(refmap != null, "打包配置 ${entry.name} 缺少 refmap 字段:\n$text")
                put(entry.name, refmap!!)
            }
        }
    }

    private fun refmapMappingKeys(refmapName: String): List<String> {
        val mappings = jsonObject(String(packagedEntryBytes(refmapName), StandardCharsets.UTF_8))["mappings"]
        assertTrue(mappings != null && mappings.isJsonObject, "refmap $refmapName 缺少 mappings 对象")
        return mappings!!.asJsonObject.entrySet().map { it.key }.sorted()
    }

    private fun jsonObject(text: String) = JsonParser().parse(text).asJsonObject

    private fun packagedEntryBytes(entryName: String): ByteArray {
        assertTrue(Files.isRegularFile(PACKAGED_JAR), "缺少打包产物 (先运行 test/jar 任务): $PACKAGED_JAR")
        return JarFile(PACKAGED_JAR.toFile()).use { jar ->
            jar.getEntry(entryName)?.let { entry -> jar.getInputStream(entry).use { it.readBytes() } } ?: ByteArray(0)
        }
    }

    private companion object {
        val PACKAGED_JAR: Path = packagedJarPath()
        val REFMAP_ENTRY = Regex("""^mixins\..*\.refmap\.json$""")

        /**
         * 打包 jar 路径：优先取构建脚本经 `stackupup.packagedJar` 系统属性注入的 `jar` 任务产物
         * （`test` 已 `dependsOn(jar)`，故 Gradle 路径总是走这里）。属性缺席时（例如 IDE 直跑测试）
         * 回退到 `build/libs` 下唯一的 `*-dev.jar`；两者都取不到时显式失败，不静默跳过。
         */
        fun packagedJarPath(): Path {
            val injected = System.getProperty("stackupup.packagedJar")
            if (!injected.isNullOrBlank()) {
                return Paths.get(injected)
            }
            val libsDirectory = Paths.get("build", "libs")
            val candidates = if (Files.isDirectory(libsDirectory)) {
                Files.list(libsDirectory).use { stream ->
                    stream.filter { it.fileName.toString().endsWith("-dev.jar") }.toList()
                }
            } else {
                emptyList()
            }
            return candidates.singleOrNull()
                ?: throw AssertionError(
                    "无法确定打包 jar：系统属性 stackupup.packagedJar 未设置，且 $libsDirectory 下的 *-dev.jar 候选为 $candidates",
                )
        }
    }
}
