package io.alexjoest.stackupup.core

import io.alexjoest.stackupup.Constants
import io.alexjoest.stackupup.StackUpUpIds
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.Collections
import java.util.function.Consumer

internal object CompatibilityLimitPatch {
    private const val DEFAULT_STACK_LIMIT: Int = Constants.VANILLA_STACK_LIMIT
    private const val HELPER_OWNER: String = StackUpUpIds.STACK_LIMIT_HOOKS_INTERNAL_NAME
    private const val HELPER_NAME: String = "getCompatibilityStackSize"
    private const val HELPER_DESC: String = "()I"

    fun planFor(transformedName: String, basicClass: ByteArray? = null): List<Consumer<ClassNode>> {
        val declaredProfiles = basicClass?.let(DynamicCompatMethodProbe::detectProfiles)
        if (declaredProfiles == DynamicCompatTargetProfile.NONE) {
            return Collections.emptyList()
        }

        val profile = if (declaredProfiles != null) {
            DynamicCompatTargetClassifier.classify(transformedName, declaredProfiles)
        } else {
            DynamicCompatTargetClassifier.classify(transformedName)
        }
        val methods = DynamicCompatTargetProfile.methodsFor(profile) ?: return Collections.emptyList()

        return Collections.singletonList(rewrite(*methods))
    }

    fun rewrite(vararg methods: String): Consumer<ClassNode> {
        return Consumer { node ->
            for (method in node.methods) {
                if (!matchesAny(method.name, methods)) {
                    continue
                }

                var patchesMade = 0
                val iterator = method.instructions.iterator()
                while (iterator.hasNext()) {
                    val instruction = iterator.next()
                    if (instruction.opcode != Opcodes.BIPUSH) {
                        continue
                    }

                    val intInstruction = instruction as IntInsnNode
                    if (intInstruction.operand != DEFAULT_STACK_LIMIT) {
                        continue
                    }

                    iterator.set(
                        MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            HELPER_OWNER,
                            HELPER_NAME,
                            HELPER_DESC,
                            false
                        )
                    )
                    patchesMade++
                }
            }
        }
    }

    private fun matchesAny(name: String, candidates: Array<out String>): Boolean {
        for (candidate in candidates) {
            if (candidate == name) {
                return true
            }
        }
        return false
    }
}
