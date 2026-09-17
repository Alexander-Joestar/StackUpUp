package io.alexjoest.stackupup.packaging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * mcmod.info 的 Gradle 属性替换检查（结构检查：断言 processResources 产物文本，不验证运行时装载）。
 *
 * 回归背景：`minecraft.gradle.kts` 的 `expand("mcversion" to minecraft.mcVersion)` 传入的是
 * Gradle `Property<String>`；`expand()` 不解析 Provider，只调用 `toString()`，于是产物被写成
 * `extension 'minecraft' property 'mcVersion'`。Cleanroom Loader 用该字段做 MC 版本比较，
 * 不匹配即走 FML MissingModsException 判定缺前置，导致启动崩溃。
 *
 * 期望值取自 `gradle.properties` 与构建脚本中 `mcVersion.set(...)` 的声明，避免测试与产物同源硬编码。
 */
class McModInfoExpansionTest {

    @Test
    fun packagedMcModInfo_shouldCarryResolvedMcVersion() {
        val mcmodInfo = processResourcesMcModInfo()
        val actual = extractJsonString(mcmodInfo, "mcversion")

        assertEquals(
            declaredMcVersion(),
            actual,
            "产物 mcmod.info 的 mcversion 必须是解析后的字面 MC 版本: $actual",
        )
        assertFalse(
            actual!!.contains("extension") || actual.contains("property"),
            "mcversion 被写成了 Provider 的 toString 结果: $actual",
        )
    }

    @Test
    fun packagedMcModInfo_shouldStillSubstituteVersion() {
        val mcmodInfo = processResourcesMcModInfo()
        val actual = extractJsonString(mcmodInfo, "version")

        assertEquals(
            declaredModVersion(),
            actual,
            "产物 mcmod.info 的 version 必须保持替换为 mod_version: $actual",
        )
    }

    @Test
    fun packagedMcModInfo_shouldHaveNoUnresolvedTokens() {
        val mcmodInfo = processResourcesMcModInfo()
        assertFalse(
            mcmodInfo.contains("\${"),
            "产物 mcmod.info 仍含未替换的 \${...} 占位符:\n$mcmodInfo",
        )
    }

    private fun processResourcesMcModInfo(): String {
        assertTrue(
            Files.isRegularFile(MCMOD_INFO),
            "缺少 processResources 产物 (先运行 test/jar 任务): $MCMOD_INFO",
        )
        return String(Files.readAllBytes(MCMOD_INFO), StandardCharsets.UTF_8)
    }

    private fun declaredMcVersion(): String {
        assertTrue(Files.isRegularFile(MINECRAFT_CONVENTION), "缺少构建脚本: $MINECRAFT_CONVENTION")
        val source = String(Files.readAllBytes(MINECRAFT_CONVENTION), StandardCharsets.UTF_8)
        return requireNotNull(McVersionDeclaration.find(source)) {
            "构建脚本中未找到 mcVersion.set(\"...\") 声明: $MINECRAFT_CONVENTION"
        }
    }

    private fun declaredModVersion(): String {
        assertTrue(Files.isRegularFile(GRADLE_PROPERTIES), "缺少 gradle.properties: $GRADLE_PROPERTIES")
        val lines = Files.readAllLines(GRADLE_PROPERTIES, StandardCharsets.UTF_8)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("mod_version=")) {
                return trimmed.removePrefix("mod_version=").trim()
            }
        }
        throw AssertionError("gradle.properties 缺少 mod_version: $GRADLE_PROPERTIES")
    }

    private fun extractJsonString(json: String, key: String): String? = Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)

    private companion object {
        val MCMOD_INFO: Path = Paths.get("build", "resources", "main", "mcmod.info")
        val GRADLE_PROPERTIES: Path = Paths.get("gradle.properties")
        val MINECRAFT_CONVENTION: Path =
            Paths.get("build-logic", "convention", "src", "main", "kotlin", "minecraft.gradle.kts")
    }
}

/** 从 convention 脚本源码中提取 `mcVersion.set("<字面值>")` 的声明值。 */
private object McVersionDeclaration {
    private val PATTERN = Regex("""mcVersion\.set\(\s*"([^"]+)"\s*\)""")

    fun find(source: String): String? = PATTERN.find(source)?.groupValues?.get(1)
}
