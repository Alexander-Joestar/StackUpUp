package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IntInsnNode

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
    fun `unknown item handler dynamic plan disabled even when getSlotLimit returns literal 64`() {
        val unsafeHandlerBytes = classBytes("io.alexjoest.stackupup.core.TestUnsafeItemHandler")

        assertTrue(unsafeHandlerBytes.hasGetSlotLimitReturningLiteral64())
        assertFalse(
            CompatibilityLimitPatch.planFor(
                "io.alexjoest.stackupup.core.TestUnsafeItemHandler",
                unsafeHandlerBytes,
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

    private fun ByteArray.hasGetSlotLimitReturningLiteral64(): Boolean {
        val node = ClassNode()
        ClassReader(this).accept(node, 0)
        val method = node.methods.single { it.name == "getSlotLimit" && it.desc == "(I)I" }
        return method.instructions.iterator().asSequence().any { instruction ->
            instruction is IntInsnNode &&
                instruction.opcode == Opcodes.BIPUSH &&
                instruction.operand == 64
        }
    }
}
