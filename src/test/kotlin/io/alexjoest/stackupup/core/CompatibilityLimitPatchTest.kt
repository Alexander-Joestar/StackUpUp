package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompatibilityLimitPatchTest {
    @Test
    fun `unrelatedClass_shouldNotGeneratePlan`() {
        val patches = CompatibilityLimitPatch.planFor("java.lang.String")
        assertTrue(patches.isEmpty())
    }

    @Test
    fun `patchList_shouldCorrectlyReflectEmptiness`() {
        val emptyPatches = emptyList<Any>()
        val nonEmptyPatches = CompatibilityLimitPatch.planFor(
            "io.alexjoest.stackupup.core.TestInventoryOverride",
            classBytes("io.alexjoest.stackupup.core.TestInventoryOverride"),
        )
        assertTrue(emptyPatches.isEmpty())
        assertTrue(nonEmptyPatches.isNotEmpty())
    }

    @Test
    fun `fixedMixinTarget_shouldNotGenerateDynamicPatch`() {
        for (target in FixedCompatTargets.all()) {
            assertFalse(
                CompatibilityLimitPatch.planFor(target).isNotEmpty(),
                "固定目标不应继续生成动态补丁: $target",
            )
        }
    }

    @Test
    fun `declaredDynamicTarget_shouldStillGeneratePatch`() {
        assertTrue(
            CompatibilityLimitPatch.planFor(
                "io.alexjoest.stackupup.core.TestInventoryOverride",
                classBytes("io.alexjoest.stackupup.core.TestInventoryOverride"),
            ).isNotEmpty(),
        )
    }

    @Test
    fun `inheritedNoMethod_shouldNotGeneratePlan`() {
        assertFalse(
            CompatibilityLimitPatch.planFor(
                "net.minecraftforge.items.wrapper.PlayerInvWrapper",
                classBytes("net.minecraftforge.items.wrapper.PlayerInvWrapper"),
            ).isNotEmpty(),
        )
        assertFalse(
            CompatibilityLimitPatch.planFor(
                "net.minecraft.inventory.SlotCrafting",
                classBytes("net.minecraft.inventory.SlotCrafting"),
            ).isNotEmpty(),
        )
    }

    private fun classBytes(className: String): ByteArray {
        val resourcePath = className.replace('.', '/') + ".class"
        return requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "无法读取类字节码: $className"
        }.use { it.readBytes() }
    }
}
