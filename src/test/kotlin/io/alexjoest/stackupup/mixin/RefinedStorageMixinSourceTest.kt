package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefinedStorageMixinSourceTest {
    @Test
    fun `rsExtractMixin_shouldPreferWrapOperation`() {
        assertUsesWrapOperation("src/main/java/io/alexjoest/stackupup/mixin/late/ItemGridHandlerMixin.java")
        assertUsesWrapOperation("src/main/java/io/alexjoest/stackupup/mixin/late/ItemGridHandlerPortableMixin.java")
    }

    private fun assertUsesWrapOperation(path: String) {
        val source = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
        assertTrue(source.contains("WrapOperation"), "应使用 WrapOperation: $path")
        assertFalse(source.contains("@Redirect"), "不应回退到 Redirect: $path")
    }
}
