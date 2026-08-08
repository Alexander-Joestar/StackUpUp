package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.mixin.early.VanillaInventoryTargets
import net.minecraft.inventory.InventoryLargeChest
import net.minecraft.tileentity.TileEntityBeacon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 登记护栏（结构检查，非行为验证；T2a §6 规则 1/2 落地）。
 *
 * 三处登记必须双向一致：`VanillaInventoryTargets.TARGETS`（机器可读编译期表）↔
 * `VanillaInventoryLimitMixin` 的 `@Mixin` 列表 ↔ `docs/agent/t2b-原版目标表.md` §2 目标表；
 * 实现中存在但表内缺失 = 未登记目标 = 失败（Fail Fast），不得静默继续。
 */
class VanillaInventoryTargetsGuardTest {

    private fun readSource(relativePath: String): String = String(
        Files.readAllBytes(Paths.get(relativePath)),
        StandardCharsets.UTF_8,
    )

    private val tableTargets: Set<String> = VanillaInventoryTargets.TARGETS.map { it.name }.toSet()

    private fun readMixinSource(): String = readSource("src/main/java/io/alexjoest/stackupup/mixin/early/VanillaInventoryLimitMixin.java")

    private fun mixinAnnotationTargets(): Set<String> {
        val source = readMixinSource()
        // import 行建立 simpleName → 全限定名映射，把 @Mixin 列表解析到与
        // VanillaInventoryTargets.TARGETS（Class.name，全限定名）同一命名空间后做双向比对；
        // 解析不到全限定名的 token 直接失败（Fail Fast），不静默放行。
        val imports = Regex("(?m)^import ([A-Za-z0-9_.]+);$")
            .findAll(source)
            .map { it.groupValues[1] }
            .associateBy { it.substringAfterLast('.') }
        val mixinBlock = source.substringAfter("@Mixin({").substringBefore("})")
        return Regex("([A-Za-z0-9_.]+)\\.class")
            .findAll(mixinBlock)
            .map { it.groupValues[1] }
            .map { token ->
                if (token.contains('.')) {
                    token
                } else {
                    requireNotNull(imports[token]) {
                        "mixin 目标 $token 无法经 import 解析为全限定名，登记护栏无法比对"
                    }
                }
            }
            .toSet()
    }

    @Test
    fun `targetTable_shouldMatchMixinAnnotationListExactly`() {
        // 双向一致：实现中存在但表内缺失 = 未登记目标 = 失败；表内存在但实现缺失 = 登记悬空 = 失败。
        assertEquals(tableTargets, mixinAnnotationTargets())
    }

    @Test
    fun `targetTable_shouldMatchT2bDocumentTableExactly`() {
        // 文档契约（t2b §5）：§2 目标表每行以 "| net.minecraft.<全限定类名> |" 开头，全文档仅该表如此。
        val doc = readSource("docs/agent/t2b-原版目标表.md")
        val docTargets = Regex("(?m)^\\| (net\\.minecraft\\.[A-Za-z0-9_.]+) \\|")
            .findAll(doc)
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(12, docTargets.size, "目标表应恰为 12 项")
        assertEquals(tableTargets, docTargets)
    }

    @Test
    fun `excludedImplementers_shouldNotBeInTableNorMixin`() {
        assertEquals(
            setOf(InventoryLargeChest::class.java.name, TileEntityBeacon::class.java.name),
            VanillaInventoryTargets.EXCLUDED.map { it.name }.toSet(),
        )
        assertFalse(VanillaInventoryTargets.TARGETS.contains(InventoryLargeChest::class.java))
        assertFalse(VanillaInventoryTargets.TARGETS.contains(TileEntityBeacon::class.java))

        val source = readMixinSource()
        assertFalse(source.contains("InventoryLargeChest"), "转发箱（条件断链）不得出现在 mixin 目标中")
        assertFalse(source.contains("TileEntityBeacon"), "主动收紧类（返回 1）不得出现在 mixin 目标中")
    }

    @Test
    fun `runtimeSentinel_shouldBeReplacedByCompileTimeTable`() {
        val source = readMixinSource()
        assertFalse(source.contains("original == 64"), "运行时 original == 64 目标判断应已删除")
        assertFalse(source.contains("VANILLA_STACK_LIMIT"), "64 哨兵常量应已删除")
        assertTrue(source.contains("VanillaInventoryTargets.METHOD_DESCRIPTOR"), "目标方法 descriptor 应引用编译期表")
        assertTrue(source.contains("getInventoryStackLimit()I"), "descriptor 应为无参变体 getInventoryStackLimit()I")
    }
}
