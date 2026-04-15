package pl.asie.stackup.core

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

object ItemStackPatch {
    @JvmStatic
    fun patchCountGetSet(node: ClassNode) {
        for (mn in node.methods) {
            if ("<init>" == mn.name) {
                val it = mn.instructions.iterator()
                while (it.hasNext()) {
                    val insn = it.next()
                    if (insn is LdcInsnNode && "Count" == insn.cst) {
                        val in2 = it.next()
                        if (in2.opcode == Opcodes.INVOKEVIRTUAL) {
                            var patched = false
                            val min2 = in2 as MethodInsnNode
                            if (min2.name == "getByte") {
                                min2.name = "getInteger"
                                patched = true
                            } else if (min2.name == "func_74771_c") {
                                min2.name = "func_74762_e"
                                patched = true
                            }

                            if (patched) {
                                min2.desc = "(Ljava/lang/String;)I"
                                println("Patched ItemStack Count getter!")
                            }
                        }
                    }
                }
            } else if ("func_77955_b" == mn.name || "writeToNBT" == mn.name) {
                val it = mn.instructions.iterator()
                while (it.hasNext()) {
                    val insn = it.next()
                    if (insn is LdcInsnNode && "Count" == insn.cst) {
                        it.next()
                        it.next()
                        it.next()
                        val in2: AbstractInsnNode = it.next()
                        if (in2.opcode == Opcodes.INVOKEVIRTUAL) {
                            var patched = false
                            val min2 = in2 as MethodInsnNode
                            if (min2.name == "setByte") {
                                min2.name = "setInteger"
                                patched = true
                            } else if (min2.name == "func_74774_a") {
                                min2.name = "func_74768_a"
                                patched = true
                            }

                            if (patched) {
                                min2.desc = "(Ljava/lang/String;I)V"
                                println("Patched ItemStack Count setter!")
                                it.previous()
                                it.previous()
                                it.remove()
                            }
                        }
                    }
                }
            }
        }
    }
}
