package pl.asie.stackup.core

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode

object RenderItemPatch {
    @JvmStatic
    fun patchDrawItemCount(node: ClassNode) {
        for (mn in node.methods) {
            if ("renderItemOverlayIntoGUI" == mn.name || "func_180453_a" == mn.name) {
                val it = mn.instructions.iterator()
                while (it.hasNext()) {
                    val insn = it.next()
                    if (insn.opcode == Opcodes.INVOKEVIRTUAL) {
                        val min = insn as MethodInsnNode
                        if (min.owner == "net/minecraft/client/gui/FontRenderer" && min.desc == "(Ljava/lang/String;FFI)I") {
                            it.set(
                                MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "pl/asie/stackup/client/StackUpClientHelpers",
                                    "drawItemCountWithShadow",
                                    "(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;FFI)I",
                                    false
                                )
                            )
                            println("Patched item count render in RenderItem!")
                            break
                        }
                    }
                }
            }
        }
    }
}
