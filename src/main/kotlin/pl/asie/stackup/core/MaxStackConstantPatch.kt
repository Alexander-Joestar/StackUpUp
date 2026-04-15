package pl.asie.stackup.core

import com.google.common.collect.Sets
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.function.Consumer

object MaxStackConstantPatch {
    @JvmStatic
    fun patchMaxLimit(vararg methods: String): Consumer<ClassNode> {
        val methodSet = Sets.newHashSet(*methods)
        return Consumer { node ->
            for (mn in node.methods) {
                if (methodSet.contains(mn.name)) {
                    var patchesMade = 0
                    val it = mn.instructions.iterator()
                    while (it.hasNext()) {
                        val insn = it.next()
                        if (insn.opcode == Opcodes.BIPUSH) {
                            val iin = insn as IntInsnNode
                            if (iin.operand == 64) {
                                println("Patched max stack check in ${node.name} -> ${mn.name}!")
                                it.set(
                                    MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        "pl/asie/stackup/StackUpHelpers",
                                        "getMaxStackSize",
                                        "()I",
                                        false
                                    )
                                )
                                patchesMade++
                            }
                        }
                    }

                    if (patchesMade > 1) {
                        println("NOTE: Made $patchesMade patches in ${node.name} -> ${mn.name}!")
                    }
                }
            }
        }
    }
}
