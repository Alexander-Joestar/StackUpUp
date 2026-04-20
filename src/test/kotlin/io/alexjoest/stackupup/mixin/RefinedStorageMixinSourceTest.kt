package io.alexjoest.stackupup.mixin

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RefinedStorageMixinSourceTest {
    @Test
    fun `RS 提取 mixin 应优先使用 WrapOperation`() {
        assertUsesWrapOperation("src/main/java/io/alexjoest/stackupup/mixin/late/ItemGridHandlerMixin.java")
        assertUsesWrapOperation("src/main/java/io/alexjoest/stackupup/mixin/late/ItemGridHandlerPortableMixin.java")
    }

    private fun assertUsesWrapOperation(path: String) {
        val source = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
        assertTrue(source.contains("WrapOperation"), "应使用 WrapOperation: $path")
        assertFalse(source.contains("@Redirect"), "不应回退到 Redirect: $path")
    }
}
