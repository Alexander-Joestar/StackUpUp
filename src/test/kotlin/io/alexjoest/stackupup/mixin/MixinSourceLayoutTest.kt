package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.streams.asSequence

class MixinSourceLayoutTest {
    @Test
    fun `mixinSources_shouldBeInSrcMainJava`() {
        val kotlinMixinRoot = Paths.get("src", "main", "kotlin", "io", "alexjoest", "stackupup", "mixin")
        val kotlinMixinFiles = kotlinMixinRoot.walkRegularFiles()
            .filter { it.toString().endsWith(".kt") }
            .map(Path::toString)
            .sorted()
            .toList()

        assertEquals(emptyList<String>(), kotlinMixinFiles)
    }

    @Test
    fun `mixinPackage_shouldNotContainRuntimeHelpers`() {
        val mixinRoot = Paths.get("src", "main", "java", "io", "alexjoest", "stackupup", "mixin")
        val nonMixinFiles = mixinRoot.walkRegularFiles()
            .filter { it.toString().endsWith(".java") }
            .map { mixinRoot.relativize(it).toString().replace('\\', '/') }
            .filterNot { it.endsWith("Mixin.java") }
            .sorted()
            .toList()

        assertEquals(emptyList<String>(), nonMixinFiles)
    }

    private fun Path.walkRegularFiles(): Sequence<Path> {
        if (!exists()) {
            return emptySequence()
        }
        return Files.walk(this).use { stream ->
            stream
                .filter(Files::isRegularFile)
                .collect(java.util.stream.Collectors.toList())
                .asSequence()
        }
    }
}
