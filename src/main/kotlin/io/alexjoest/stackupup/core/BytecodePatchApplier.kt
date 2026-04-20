package io.alexjoest.stackupup.core

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.util.function.Consumer

internal object BytecodePatchApplier {
    fun apply(data: ByteArray, patches: List<Consumer<ClassNode>>): ByteArray {
        if (patches.isEmpty()) {
            return data
        }

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
}
