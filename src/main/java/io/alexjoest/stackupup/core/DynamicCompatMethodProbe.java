package io.alexjoest.stackupup.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 只扫描当前类直接声明的方法名。
 * <p>
 * 动态兼容层真正需要处理的是"当前类自己把 64 写死了"的情况；
 * 如果只是继承了父类实现，就没必要继续走完整的补丁流程。
 */
final class DynamicCompatMethodProbe {

    private DynamicCompatMethodProbe() {}

    static int detectProfiles(byte[] data) {
        MethodNameCollector visitor = new MethodNameCollector();
        ClassReader         reader  = new ClassReader(data);
        reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return visitor.profileMask;
    }

    private static class MethodNameCollector extends ClassVisitor {
        int profileMask = DynamicCompatTargetProfile.NONE;

        MethodNameCollector() {
            super(Opcodes.ASM5);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (matches(name, "getInventoryStackLimit", "func_70297_j_")) {
                profileMask |= DynamicCompatTargetProfile.INVENTORY;
                return null;
            }

            if (matches(name, "getSlotLimit")) {
                profileMask |= DynamicCompatTargetProfile.ITEM_HANDLER;
                return null;
            }

            if (matches(name, "getItemStackLimit", "func_178170_b", "getSlotStackLimit", "func_75219_a")) {
                profileMask |= DynamicCompatTargetProfile.SLOT;
            }

            return null;
        }

        private static boolean matches(String name, String... candidates) {
            for (String candidate : candidates) {
                if (candidate.equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }
}
