package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpCore
import io.alexjoest.stackupup.StackUpUpIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

/**
 * 配置 ↔ loader ↔ mixin 类三方对齐的结构检查（T14.5 停止条件 3「配置未注册无检测」的反向方向）。
 *
 * 全部为结构检查（文件系统枚举 + JSON/源码文本比对），不做运行行为验证：
 * - 资源目录中实际存在的 JSON 必须被 loader 注册（JSON 有类但 loader 不注册 → 失败）；
 * - mixin 包内带 @Mixin 的类必须登记在某个 JSON（未注册目标 → 失败）。
 *
 * 运行时（打包 jar）无法枚举资源目录/类列表，这两个反向方向只在 dev/test 用文件系统覆盖，
 * 运行时只做正向校验（见 [MixinConfigValidator.validateConfigs]）。
 */
class MixinConfigRegistrationAlignmentTest {

    @Test
    fun `allJsonResources_shouldBeRegisteredByLoaders`() {
        val configFiles = Files.list(RESOURCES_DIR).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.startsWith("mixins.${StackUpUpIds.MOD_ID}.") && it.endsWith(".json") }
                .toList()
        }
        val registered = StackUpUpCore().getMixinConfigs() + StackUpUpLateMixinLoader().getMixinConfigs()
        val unregistered = MixinConfigValidator.findUnregisteredConfigFiles(configFiles, registered)
        assertEquals(emptyList<String>(), unregistered, "存在 JSON 但 loader 未注册: $unregistered")
    }

    @Test
    fun `everyMixinSourceClass_shouldBeRegisteredInSomeJson`() {
        val registeredNames = readAllJsonClassNames()
        val unregisteredMixins = buildList {
            addAll(mixinClassesFromSource("early"))
            addAll(mixinClassesFromSource("late"))
        }.filterNot { it in registeredNames }.sorted()
        assertEquals(emptyList<String>(), unregisteredMixins, "带 @Mixin 注解但未登记在任何 JSON 的类: $unregisteredMixins")
    }

    private fun mixinClassesFromSource(subpackage: String): List<String> {
        val directory = Paths.get("src", "main", "java", "io", "alexjoest", "stackupup", "mixin", subpackage)
        return Files.list(directory).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.endsWith(".java") }
                .filter { fileName ->
                    val source = String(Files.readAllBytes(directory.resolve(fileName)), StandardCharsets.UTF_8)
                    // 只认注解使用形态 @Mixin(，避免 javadoc 里的 {@code @Mixin} 误判（VanillaInventoryTargets）。
                    source.contains("@Mixin(")
                }
                .map { it.removeSuffix(".java") }
                .toList()
        }
    }

    private fun readAllJsonClassNames(): Set<String> {
        val configs = StackUpUpCore().getMixinConfigs() + StackUpUpLateMixinLoader().getMixinConfigs()
        return buildSet {
            for (config in configs) {
                val json = String(Files.readAllBytes(RESOURCES_DIR.resolve(config)), StandardCharsets.UTF_8)
                for (key in listOf("mixins", "client", "server")) {
                    addAll(extractJsonStringArray(json, key))
                }
            }
        }
    }

    private fun extractJsonStringArray(json: String, key: String): List<String> {
        val pattern = Regex(""""$key"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val body = pattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        return Regex(""""([^"]+)"""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()
    }

    private companion object {
        val RESOURCES_DIR: Path = Paths.get("src", "main", "resources")
    }
}
