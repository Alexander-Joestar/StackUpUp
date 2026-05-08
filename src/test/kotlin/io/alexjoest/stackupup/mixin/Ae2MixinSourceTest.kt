package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class Ae2MixinSourceTest {
    @Test
    fun `ae2Mixin_shouldCoverConstructorConstantAndReturnLimit`() {
        assertCoversConstructorAndReturnLimit("src/main/java/io/alexjoest/stackupup/mixin/late/AppEngInternalInventoryMixin.java")
        assertCoversConstructorAndReturnLimit("src/main/java/io/alexjoest/stackupup/mixin/late/AppEngInternalAEInventoryMixin.java")
    }

    private fun assertCoversConstructorAndReturnLimit(path: String) {
        val source = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
        assertTrue(source.contains("@ModifyConstant"), "应继续覆盖构造期常量: $path")
        assertTrue(source.contains("<init>*"), "应继续覆盖构造器: $path")
        assertTrue(source.contains("private static int replaceCompatibilityLimit"), "构造器常量补丁必须使用 static handler: $path")
        assertTrue(source.contains("getInventoryStackLimit"), "应覆盖 getInventoryStackLimit: $path")
        assertTrue(source.contains("ModifyReturnValue"), "应使用 ModifyReturnValue 收口返回值: $path")
    }
}
