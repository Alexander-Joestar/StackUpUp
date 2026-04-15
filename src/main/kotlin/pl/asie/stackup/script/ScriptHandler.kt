package pl.asie.stackup.script

import com.google.common.collect.Ordering
import net.minecraft.item.Item
import net.minecraftforge.registries.IForgeRegistry
import pl.asie.stackup.StackUp
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackReader
import java.util.TreeSet

class ScriptHandler {
    protected fun processFile(registry: IForgeRegistry<Item>, file: File) {
        requireNotNull(StackUp.logger).info("Parsing " + file.name)

        try {
            FileInputStream(file).use { stream: InputStream ->
                ScriptContext(registry, stream, TokenProvider).execute()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    protected fun processDirectory(registry: IForgeRegistry<Item>, file: File) {
        val files: MutableSet<File> = TreeSet(Ordering.natural())
        for (f in file.listFiles() ?: return) {
            files.add(f)
        }

        for (f in files) {
            if (f.isDirectory) {
                processDirectory(registry, f)
            } else {
                processFile(registry, f)
            }
        }
    }

    fun process(registry: IForgeRegistry<Item>, baseDir: File?) {
        if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory) {
            return
        }

        processDirectory(registry, baseDir)
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun cutWhitespace(r: PushbackReader) {
            var c: Int
            do {
                c = r.read()
            } while (Character.isWhitespace(c))
            r.unread(c)
        }
    }
}
