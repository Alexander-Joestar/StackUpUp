package pl.asie.stackup.core

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.math.abs

object RenderEntityItemPatch {
    @JvmStatic
    fun patchDistanceConstant(node: ClassNode) {
        for (mn in node.methods) {
            if ("doRender" == mn.name || "func_76986_a" == mn.name) {
                val it = mn.instructions.iterator()
                while (it.hasNext()) {
                    val insn = it.next()
                    if (insn.opcode == Opcodes.LDC) {
                        val min = insn as LdcInsnNode
                        if (min.cst is Number && abs(abs((min.cst as Number).toFloat()) - 0.09375F) < 0.001F) {
                            val isNegative = (min.cst as Number).toFloat() < 0

                            it.set(VarInsnNode(Opcodes.ALOAD, 1))
                            it.add(
                                MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "pl/asie/stackup/client/StackUpClientHelpers",
                                    if (isNegative) "getItemRenderDistanceNeg" else "getItemRenderDistance",
                                    "(Lnet/minecraft/entity/item/EntityItem;)F",
                                    false
                                )
                            )
                            println("Patched item render distance constant in RenderEntityItem!")
                        }
                    }
                }
            }
        }
    }
}
