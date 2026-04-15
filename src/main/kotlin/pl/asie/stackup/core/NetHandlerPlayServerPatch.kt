package pl.asie.stackup.core

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MethodInsnNode

object NetHandlerPlayServerPatch {
    @JvmStatic
    fun patchCreativeInventory(node: ClassNode) {
        for (mn in node.methods) {
            if ("processCreativeInventoryAction" == mn.name || "func_147344_a" == mn.name) {
                val it = mn.instructions.iterator()
                while (it.hasNext()) {
                    val insn = it.next()
                    if (insn.opcode == Opcodes.INVOKEVIRTUAL) {
                        val min = insn as MethodInsnNode
                        if (min.owner == "net/minecraft/item/ItemStack" &&
                            (min.name == "getCount" || min.name == "func_190916_E")
                        ) {
                            val in2 = it.next()
                            if (in2.opcode == Opcodes.BIPUSH) {
                                val intInsnNode = in2 as IntInsnNode
                                if (intInsnNode.operand == 64) {
                                    println("Patched processCreativeInventoryAction count check!")
                                    it.set(
                                        MethodInsnNode(
                                            Opcodes.INVOKESTATIC,
                                            "pl/asie/stackup/StackUpHelpers",
                                            "getMaxStackSize",
                                            "()I",
                                            false
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
