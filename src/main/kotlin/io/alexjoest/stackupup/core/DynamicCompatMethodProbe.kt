package io.alexjoest.stackupup.core

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * 只扫描当前类直接声明的方法名。
 *
 * 动态兼容层真正需要处理的是“当前类自己把 64 写死了”的情况；
 * 如果只是继承了父类实现，就没必要继续走完整的补丁流程。
 */
internal object DynamicCompatMethodProbe {
    fun detectProfiles(data: ByteArray): Int {
        val visitor = MethodNameCollector()
        ClassReader(data).accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return visitor.profileMask
    }

    private class MethodNameCollector : ClassVisitor(Opcodes.ASM5) {
        var profileMask: Int = DynamicCompatTargetProfile.NONE
            private set

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            if (matches(name, "getInventoryStackLimit", "func_70297_j_")) {
                profileMask = profileMask or DynamicCompatTargetProfile.INVENTORY
                return null
            }

            if (matches(name, "getSlotLimit")) {
                profileMask = profileMask or DynamicCompatTargetProfile.ITEM_HANDLER
                return null
            }

            if (matches(name, "getItemStackLimit", "func_178170_b", "getSlotStackLimit", "func_75219_a")) {
                profileMask = profileMask or DynamicCompatTargetProfile.SLOT
            }

            return null
        }

        private fun matches(name: String, vararg candidates: String): Boolean {
            for (candidate in candidates) {
                if (candidate == name) {
                    return true
                }
            }
            return false
        }
    }
}
