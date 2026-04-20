package io.alexjoest.stackupup.core

import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.InputStream
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap

private fun toSlashName(name: String): String {
    val builder = StringBuilder(name.length)
    for (c in name) {
        builder.append(if (c == '.') '/' else c)
    }
    return builder.toString()
}

internal object ClassHierarchyRepository {
    private val cache = ConcurrentHashMap<String, ClassHierarchyMetadata>()

    fun get(className: String): ClassHierarchyMetadata {
        val existing = cache[className]
        if (existing != null) {
            return existing
        }

        val loaded = loadMetadata(className)
        val previous = cache.putIfAbsent(className, loaded)
        return previous ?: loaded
    }

    private fun loadMetadata(className: String): ClassHierarchyMetadata {
        var fileName = FMLDeobfuscatingRemapper.INSTANCE.unmap(toSlashName(className))
        fileName = toSlashName(fileName) + ".class"
        val stream: InputStream = ClassHierarchyRepository::class.java.classLoader.getResourceAsStream(fileName)
            ?: return ClassHierarchyMetadata(superClass = null, interfaces = Collections.emptySet())

        try {
            val collector = HierarchySignatureCollector()
            ClassReader(stream).accept(
                collector,
                ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
            )
            return collector.toMetadata()
        } finally {
            stream.close()
        }
    }

    /**
     * 这里只提取父类与接口签名，避免在 coremod 早期路径为层级判断构建完整的 ClassNode。
     */
    private class HierarchySignatureCollector : ClassVisitor(Opcodes.ASM5) {
        private var rawSuperClass: String? = null
        private var rawInterfaces: Array<out String> = emptyArray()

        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>
        ) {
            rawSuperClass = superName
            rawInterfaces = interfaces
        }

        fun toMetadata(): ClassHierarchyMetadata {
            val superClass = rawSuperClass?.let {
                toDotClassName(FMLDeobfuscatingRemapper.INSTANCE.map(it))
            }
            val interfaces = LinkedHashSet<String>(rawInterfaces.size)
            for (rawInterface in rawInterfaces) {
                interfaces += toDotClassName(FMLDeobfuscatingRemapper.INSTANCE.map(rawInterface))
            }
            return ClassHierarchyMetadata(superClass = superClass, interfaces = interfaces)
        }
    }
}
