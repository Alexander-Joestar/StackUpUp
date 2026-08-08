package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream

/**
 * MixinConfigRegistrationValidator 单元测试（T14.5 停止条件 1/3 的实现验证）。
 *
 * 结构检查与行为验证的边界：
 * - 真实 classpath 校验（core/late 全量）是结构检查——证明「当前 13 个 JSON ↔ 类登记 ↔ @Mixin 注解」对齐；
 * - 合成场景（缺资源、列不存在的类、列无 @Mixin 的类、未注册 JSON）断言校验器按预期报告/抛异常，属行为验证。
 */
class MixinConfigRegistrationValidatorTest {

    private val realClassLoader: ClassLoader = javaClass.classLoader

    @Test
    fun `coreConfig_onRealClasspath_shouldHaveNoProblems`() {
        assertEquals(
            emptyList<MixinConfigRegistrationValidator.Problem>(),
            MixinConfigRegistrationValidator.validateConfig(StackUpUpIds.EARLY_MIXIN_CONFIG, realClassLoader),
        )
    }

    @Test
    fun `lateConfigs_onRealClasspath_shouldHaveNoProblems`() {
        val lateConfigs = StackUpUpLateMixinLoader().getMixinConfigs()
        assertEquals(emptyList<MixinConfigRegistrationValidator.Problem>(), MixinConfigRegistrationValidator.validateConfigs(lateConfigs, realClassLoader))
    }

    @Test
    fun `missingCoreConfigResource_shouldFailFast`() {
        val emptyLoader = object : ClassLoader() {
            override fun getResourceAsStream(name: String): InputStream? = null
        }
        val exception = assertThrows(IllegalStateException::class.java) {
            MixinConfigRegistrationValidator.requireCoreConfigValid(StackUpUpIds.EARLY_MIXIN_CONFIG, emptyLoader)
        }
        assertTrue(
            exception.message.orEmpty().contains(StackUpUpIds.EARLY_MIXIN_CONFIG),
            "fail-fast 异常必须点名缺失的配置，实际: ${exception.message}",
        )
    }

    @Test
    fun `jsonListingMissingMixinClass_shouldReportProblem`() {
        val json = """
            {"package": "io.alexjoest.stackupup.mixin.early", "mixins": ["NoSuchMixin"]}
        """.trimIndent()
        val problems = MixinConfigRegistrationValidator.validateJson("synthetic.json", json, realClassLoader)
        assertEquals(1, problems.size)
        assertTrue(problems.single().toString().contains("NoSuchMixin"), "实际: ${problems.single()}")
    }

    @Test
    fun `jsonListingClassWithoutMixinAnnotation_shouldReportProblem`() {
        val json = """
            {"package": "io.alexjoest.stackupup.mixin.early", "mixins": ["VanillaInventoryTargets"]}
        """.trimIndent()
        val problems = MixinConfigRegistrationValidator.validateJson("synthetic.json", json, realClassLoader)
        assertEquals(1, problems.size)
        assertTrue(problems.single().toString().contains("@Mixin"), "实际: ${problems.single()}")
    }

    @Test
    fun `jsonListingRealMixinClass_shouldHaveNoProblems`() {
        val json = """
            {"package": "io.alexjoest.stackupup.mixin.early", "mixins": ["ContainerMixin"]}
        """.trimIndent()
        assertEquals(
            emptyList<MixinConfigRegistrationValidator.Problem>(),
            MixinConfigRegistrationValidator.validateJson("synthetic.json", json, realClassLoader),
        )
    }

    @Test
    fun `findUnregisteredConfigFiles_shouldReportConfigsNotRegisteredByLoaders`() {
        val configFiles = listOf(
            "mixins.stackupup.early.json",
            "mixins.stackupup.late.ae2.json",
            "mixins.stackupup.late.ghost.json",
        )
        val registered = listOf("mixins.stackupup.early.json", "mixins.stackupup.late.ae2.json")
        assertEquals(
            listOf("mixins.stackupup.late.ghost.json"),
            MixinConfigRegistrationValidator.findUnregisteredConfigFiles(configFiles, registered),
        )
    }
}
