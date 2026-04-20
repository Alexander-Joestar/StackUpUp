package io.alexjoest.stackupup.core

import net.minecraft.launchwrapper.IClassTransformer
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.util.function.Consumer

/**
 * 当前保留的最小动态兼容层。
 *
 * 这层只负责：
 * 1. 过滤显然无关的基础运行时类
 * 2. 为运行时发现目标生成补丁计划
 * 3. 把补丁计划应用到字节码
 */
class DynamicCompatTransformer : IClassTransformer {
    override fun transform(name: String?, transformedNameIn: String?, basicClass: ByteArray?): ByteArray? {
        if (basicClass == null) {
            return null
        }

        val internalName = transformedNameIn ?: name
        if (internalName.isNullOrEmpty() || CoremodClassFilter.shouldSkip(internalName)) {
            return basicClass
        }

        val transformedName = toDotName(internalName)
        val patches = DynamicCompatPlanBuilder.build(transformedName, basicClass)
        return if (patches.isNotEmpty()) {
            applyPatches(basicClass, patches)
        } else {
            basicClass
        }
    }

    private fun applyPatches(data: ByteArray, patches: List<Consumer<ClassNode>>): ByteArray {
        val reader = ClassReader(data)
        val originalNode = ClassNode()
        reader.accept(originalNode, 0)
        for (patch in patches) {
            patch.accept(originalNode)
        }
        val writer = ClassWriter(0)
        originalNode.accept(writer)
        return writer.toByteArray()
    }

    private fun toDotName(name: String): String {
        val builder = StringBuilder(name.length)
        for (char in name) {
            builder.append(if (char == '/') '.' else char)
        }
        return builder.toString()
    }
}
