package io.alexjoest.stackupup.core

import io.alexjoest.stackupup.StackUpUpIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

class MaxStackConstantPatchTest {
    @Test
    fun `目标方法中的 64 常量应被替换为统一上限入口`() {
        val method = MethodNode(Opcodes.ACC_PUBLIC, "onExtract", "()V", null, null)
        method.instructions.add(IntInsnNode(Opcodes.BIPUSH, 64))
        method.instructions.add(InsnNode(Opcodes.RETURN))
        val node = ClassNode().apply {
            name = "example/ItemGridHandlerProbe"
            methods.add(method)
        }

        CompatibilityLimitPatch.rewrite("onExtract").accept(node)

        val patched = method.instructions.toArray().filterIsInstance<MethodInsnNode>().single()
        assertEquals(StackUpUpIds.STACK_LIMIT_HOOKS_INTERNAL_NAME, patched.owner)
        assertEquals("getCompatibilityStackSize", patched.name)
        assertEquals("()I", patched.desc)
    }

    @Test
    fun `非目标方法不应被误替换`() {
        val method = MethodNode(Opcodes.ACC_PUBLIC, "notTarget", "()V", null, null)
        method.instructions.add(IntInsnNode(Opcodes.BIPUSH, 64))
        method.instructions.add(InsnNode(Opcodes.RETURN))
        val node = ClassNode().apply {
            name = "example/UntouchedProbe"
            methods.add(method)
        }

        CompatibilityLimitPatch.rewrite("onExtract").accept(node)

        val patched = method.instructions.toArray().filterIsInstance<MethodInsnNode>().firstOrNull()
        assertNull(patched)
        val original = method.instructions.toArray().filterIsInstance<IntInsnNode>().single()
        assertEquals(64, original.operand)
    }
}


