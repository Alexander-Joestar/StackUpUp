package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicCompatPlanBuilderTest {
    @Test
    fun `明显无关的类不应生成补丁计划`() {
        val plan = DynamicCompatPlanBuilder.build("java.lang.String")
        assertFalse(plan.hasPatches)
    }

    @Test
    fun `计划对象应正确反映是否包含补丁`() {
        val emptyPlan = DynamicCompatPlan(emptyList())
        val nonEmptyPlan = DynamicCompatPlan(listOf(CompatibilityLimitPatch.rewrite("getSlotLimit")))
        assertFalse(emptyPlan.hasPatches)
        assertTrue(nonEmptyPlan.hasPatches)
    }

    @Test
    fun `已迁入固定 mixin 的库存目标不应继续生成动态补丁`() {
        for (target in FixedCompatTargets.all()) {
            assertFalse(
                DynamicCompatPlanBuilder.build(target).hasPatches,
                "固定目标不应继续生成动态补丁: $target"
            )
        }
    }

    @Test
    fun `声明了目标方法的动态 inventory 目标仍应生成补丁`() {
        assertTrue(
            DynamicCompatPlanBuilder.build(
                "io.alexjoest.stackupup.core.TestInventoryOverride",
                classBytes("io.alexjoest.stackupup.core.TestInventoryOverride")
            ).hasPatches
        )
    }

    @Test
    fun `未声明目标方法的继承类不应生成动态补丁计划`() {
        assertFalse(
            DynamicCompatPlanBuilder.build(
                "net.minecraftforge.items.wrapper.PlayerInvWrapper",
                classBytes("net.minecraftforge.items.wrapper.PlayerInvWrapper")
            ).hasPatches
        )
        assertFalse(
            DynamicCompatPlanBuilder.build(
                "net.minecraft.inventory.SlotCrafting",
                classBytes("net.minecraft.inventory.SlotCrafting")
            ).hasPatches
        )
    }

    private fun classBytes(className: String): ByteArray {
        val resourcePath = className.replace('.', '/') + ".class"
        return requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "无法读取类字节码: $className"
        }.use { it.readBytes() }
    }
}
