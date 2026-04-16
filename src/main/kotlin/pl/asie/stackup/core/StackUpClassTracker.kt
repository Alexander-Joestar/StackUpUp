package pl.asie.stackup.core

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.InputStream

private fun toDotName(name: String): String {
    val builder = StringBuilder(name.length)
    for (c in name) {
        builder.append(if (c == '/') '.' else c)
    }
    return builder.toString()
}

private fun toSlashName(name: String): String {
    val builder = StringBuilder(name.length)
    for (c in name) {
        builder.append(if (c == '.') '/' else c)
    }
    return builder.toString()
}

// 类层级探测也处在 coremod 启动期，避免使用字符串扩展，防止再次拉起 kotlin.text。
object StackUpClassTracker {
    private val superclassMap: MutableMap<String, String?> = HashMap()
    private val interfaceMap: Multimap<String, String> = HashMultimap.create()

    @JvmStatic
    fun addClass(currC: String) {
        if (!superclassMap.containsKey(currC)) {
            var filename = FMLDeobfuscatingRemapper.INSTANCE.unmap(toSlashName(currC))
            filename = toSlashName(filename) + ".class"
            val stream: InputStream? = StackUpClassTracker::class.java.classLoader.getResourceAsStream(filename)
            if (stream != null) {
                try {
                    val reader = ClassNode()
                    ClassReader(stream).accept(reader, 0)
                    var newC = reader.superName
                    if (newC != null) {
                        newC = toDotName(FMLDeobfuscatingRemapper.INSTANCE.map(newC))
                        superclassMap[currC] = newC
                    }
                    for (sObj in reader.interfaces) {
                        val s = sObj as String
                        val newI = toDotName(FMLDeobfuscatingRemapper.INSTANCE.map(s))
                        interfaceMap.put(currC, newI)
                    }
                } catch (e: java.io.IOException) {
                    e.printStackTrace()
                    superclassMap[currC] = null
                } finally {
                    stream.close()
                }
            }
        }
    }

    @JvmStatic
    fun isImplements(c: String, sc: String): Boolean {
        var currC: String? = c
        while (currC != null && currC.length > 0) {
            if (currC == sc) {
                return true
            }
            addClass(currC)
            for (s in interfaceMap.get(currC)) {
                if (isImplements(s, sc)) {
                    return true
                }
            }
            currC = superclassMap[currC]
        }

        return false
    }

    @JvmStatic
    fun isExtends(c: String, sc: String): Boolean {
        var currC: String? = c
        while (currC != null && currC.length > 0) {
            if (currC == sc) {
                return true
            }
            addClass(currC)
            currC = superclassMap[currC]
        }

        return false
    }
}
