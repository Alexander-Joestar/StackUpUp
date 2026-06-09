package io.alexjoest.stackupup.core;

import io.alexjoest.stackupup.Constants;
import io.alexjoest.stackupup.StackUpUpIds;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

public final class CompatibilityLimitPatch {

    private CompatibilityLimitPatch() {}

    private static final int DEFAULT_STACK_LIMIT = Constants.VANILLA_STACK_LIMIT;
    private static final String HELPER_OWNER = StackUpUpIds.STACK_LIMIT_HOOKS_INTERNAL_NAME;
    private static final String HELPER_NAME = "getCompatibilityStackSize";
    private static final String HELPER_DESC = "()I";

    public static List<Consumer<ClassNode>> planFor(String transformedName) {
        return planFor(transformedName, null);
    }

    public static List<Consumer<ClassNode>> planFor(String transformedName, byte[] basicClass) {
        int declaredProfiles;
        if (basicClass != null) {
            declaredProfiles = DynamicCompatMethodProbe.detectProfiles(basicClass);
        } else {
            declaredProfiles = DynamicCompatTargetProfile.NONE;
        }

        if (declaredProfiles == DynamicCompatTargetProfile.NONE) {
            return Collections.emptyList();
        }

        int profile = DynamicCompatTargetClassifier.classify(transformedName, declaredProfiles);
        if (profile == DynamicCompatTargetProfile.ITEM_HANDLER) {
            return Collections.emptyList();
        }

        String[] methods = DynamicCompatTargetProfile.methodsFor(profile);
        if (methods == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(rewrite(methods));
    }

    static Consumer<ClassNode> rewrite(String... methods) {
        return node -> {
            for (MethodNode method : node.methods) {
                if (!matchesAny(method.name, methods)) {
                    continue;
                }

                InsnList instructions = method.instructions;
                ListIterator<AbstractInsnNode> iterator = instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode instruction = iterator.next();
                    if (instruction.getOpcode() != Opcodes.BIPUSH) {
                        continue;
                    }

                    IntInsnNode intInstruction = (IntInsnNode) instruction;
                    if (intInstruction.operand != DEFAULT_STACK_LIMIT) {
                        continue;
                    }

                    iterator.set(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HELPER_OWNER,
                        HELPER_NAME,
                        HELPER_DESC,
                        false
                    ));
                }
            }
        };
    }

    private static boolean matchesAny(String name, String[] candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
