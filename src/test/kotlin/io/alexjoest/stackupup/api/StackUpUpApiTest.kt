package io.alexjoest.stackupup.api

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import io.alexjoest.stackupup.rules.io.RuleSourceLocator
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class StackUpUpApiTest {
    private lateinit var tempDir: File
    private lateinit var worldDir: File
    private lateinit var worldMarkdownFile: File
    private lateinit var previousSnapshot: RuleSnapshot
    private lateinit var previousIndex: OreDictIndex
    private var previousActiveMaxStackSize: Int = 64
    private var previousConfiguredMaxStackSize: Int = 64

    @BeforeEach
    fun setUp() {
        Bootstrap.register()
        tempDir = createTempDirectory("stackupup-api").toFile()
        worldDir = File(tempDir, "world").apply { mkdirs() }
        worldMarkdownFile = File(File(File(worldDir, "data"), StackUpUpIds.MOD_ID), StackUpUpIds.WORLD_MARKDOWN_RULES_FILE_NAME)
        RuleSourceLocator.setWorldDirectoryForTests(worldDir)
        previousSnapshot = RuleRuntime.currentSnapshot()
        previousIndex = RuleRuntime.oreDictIndex()
        previousActiveMaxStackSize = StackUpUpConfig.activeMaxStackSize
        previousConfiguredMaxStackSize = StackUpUpConfig.general.maxStackSize
        StackUpUpConfig.general.maxStackSize = 10240
        StackUpUpConfig.activeMaxStackSize = 10240
        RuleRuntime.replaceRuntime(RuleSnapshot(version = 0L, rules = emptyList()), previousIndex)
    }

    @AfterEach
    fun tearDown() {
        StackUpUpConfig.general.maxStackSize = previousConfiguredMaxStackSize
        StackUpUpConfig.activeMaxStackSize = previousActiveMaxStackSize
        RuleRuntime.replaceRuntime(previousSnapshot, previousIndex)
        RuleSourceLocator.setWorldDirectoryForTests(null)
        RuleFileLocator.resetForTests()
        tempDir.deleteRecursively()
    }

    @Test
    fun `getLimit 无规则时返回物品原始上限`() {
        val item = FixedLimitItem(16).setRegistryName(ResourceLocation("stackupup_api_test", "plain_item"))

        assertEquals(16, StackUpUpApi.getLimit(ItemStack(item, 1, 0)))
    }

    @Test
    fun `getLimit 命中规则时返回动态上限`() {
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 1L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_api_test:ruled_item -> 512", 1),
                ),
            ),
        )
        val item = FixedLimitItem(64).setRegistryName(ResourceLocation("stackupup_api_test", "ruled_item"))

        assertEquals(512, StackUpUpApi.getLimit(ItemStack(item, 1, 0)))
    }

    @Test
    fun `getLimit 入参为 null 时返回 fallback`() {
        assertEquals(7, StackUpUpApi.getLimit(null, 7))
    }

    @Test
    fun `getState 状态存储不可用时返回 null`() {
        RuleSourceLocator.setWorldDirectoryForTests(File(tempDir, "missing-world"))

        assertNull(StackUpUpApi.getState("phase1"))
    }

    @Test
    fun `getState 键缺失时返回 false`() {
        writeWorldMarkdownState("phase1" to true)

        assertFalse(StackUpUpApi.getState("phase2")!!)
    }

    @Test
    fun `getState 键存在时返回存储值`() {
        writeWorldMarkdownState("phase1" to true, "phase2" to false)

        assertTrue(StackUpUpApi.getState("phase1")!!)
        assertEquals(false, StackUpUpApi.getState("phase2"))
    }

    @Test
    fun `setState 写入新值时返回 true 重复写入返回 false`() {
        writeWorldMarkdownState("phase1" to false)

        assertTrue(StackUpUpApi.setState("phase1", true))
        assertEquals(true, StackUpUpApi.getState("phase1"))
        assertFalse(StackUpUpApi.setState("phase1", true))
    }

    @Test
    fun `setState 状态存储不可用时返回 false`() {
        RuleSourceLocator.setWorldDirectoryForTests(File(tempDir, "missing-world"))

        assertFalse(StackUpUpApi.setState("phase1", true))
    }

    @Test
    fun `getState 未知键不抛异常`() {
        writeWorldMarkdownState("phase1" to true)

        assertFalse(StackUpUpApi.getState("never-declared-key")!!)
    }

    @Test
    fun `reload 规则文件无错误时返回 true`() {
        val rulesDir = File(File(tempDir, "config"), StackUpUpIds.RULES_DIRECTORY_NAME).apply { mkdirs() }
        File(rulesDir, StackUpUpIds.RULES_FILE_NAME).writeText("item = minecraft:egg -> 512\n", Charsets.UTF_8)
        RuleFileLocator.setConfigDirectory(File(tempDir, "config"))

        assertTrue(StackUpUpApi.reload())
    }

    @Test
    fun `reload 规则文件含非法行时返回 false`() {
        val rulesDir = File(File(tempDir, "config"), StackUpUpIds.RULES_DIRECTORY_NAME).apply { mkdirs() }
        File(rulesDir, StackUpUpIds.RULES_FILE_NAME).writeText("not a rule\n", Charsets.UTF_8)
        RuleFileLocator.setConfigDirectory(File(tempDir, "config"))

        assertFalse(StackUpUpApi.reload())
    }

    @Test
    fun `setState 触发 gate 翻转时重载规则并更新上限`() {
        val item = FixedLimitItem(64).setRegistryName(ResourceLocation("stackupup_api_test", "gated_item"))
        RuleFileLocator.setConfigDirectory(File(tempDir, "config"))
        writeWorldMarkdownDocument(
            listOf(
                "# state",
                "- phase1 = false",
                "",
                "# rules",
                "## state(\"phase1\")",
                "```stackupup",
                "item = stackupup_api_test:gated_item -> 128",
                "```",
            ),
        )
        assertEquals(64, StackUpUpApi.getLimit(ItemStack(item, 1, 0)))

        assertTrue(StackUpUpApi.setState("phase1", true))

        assertEquals(128, StackUpUpApi.getLimit(ItemStack(item, 1, 0)))
    }

    @Test
    fun `setState 未被任何 gate 引用时不重载规则`() {
        val item = FixedLimitItem(64).setRegistryName(ResourceLocation("stackupup_api_test", "ungated_item"))
        RuleFileLocator.setConfigDirectory(File(tempDir, "config"))
        writeWorldMarkdownDocument(
            listOf(
                "# state",
                "- phase1 = false",
                "",
                "# rules",
                "## always",
                "```stackupup",
                "item = stackupup_api_test:ungated_item -> 128",
                "```",
            ),
        )

        assertTrue(StackUpUpApi.setState("phase1", true))

        assertEquals(64, StackUpUpApi.getLimit(ItemStack(item, 1, 0)))
        assertEquals(true, StackUpUpApi.getState("phase1"))
    }

    private fun writeWorldMarkdownState(vararg states: Pair<String, Boolean>) {
        worldMarkdownFile.parentFile?.mkdirs()
        val content = buildString {
            appendLine("# state")
            for ((name, value) in states) {
                appendLine("- $name = $value")
            }
        }
        worldMarkdownFile.writeText(content, Charsets.UTF_8)
    }

    private fun writeWorldMarkdownDocument(lines: List<String>) {
        worldMarkdownFile.parentFile?.mkdirs()
        worldMarkdownFile.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}

private class FixedLimitItem(private val limit: Int) : Item() {
    override fun getItemStackLimit(stack: ItemStack): Int = limit
}
