package io.alexjoest.stackupup.mixin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MixinSourceLayoutTest {
    @Test
    fun `mixin 源文件应全部位于 src main java`() {
        val kotlinMixinRoot = Paths.get("src", "main", "kotlin", "io", "alexjoest", "stackupup", "mixin")
        val kotlinMixinFiles = kotlinMixinRoot.walkRegularFiles()
            .filter { it.toString().endsWith(".kt") }
            .map(Path::toString)
            .sorted()
            .toList()

        assertEquals(emptyList<String>(), kotlinMixinFiles)
    }

    private fun Path.walkRegularFiles(): Sequence<Path> {
        if (!exists()) {
            return emptySequence()
        }
        return Files.walk(this).use { stream ->
            stream
                .filter(Files::isRegularFile)
                .asSequence()
                .toList()
                .asSequence()
        }
    }
}
