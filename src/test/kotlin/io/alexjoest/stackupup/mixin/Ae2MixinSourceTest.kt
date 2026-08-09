package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test
    fun `ae2Mixin_shouldKeepAdaptorItemHandlerAsZeroLogicFuse`() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.ae2.json")), Charsets.UTF_8)
        assertTrue(config.contains("AppEngAdaptorItemHandlerMixin"), "AE2 late config 应继续加载 AdaptorItemHandler 入口保险丝")

        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/AppEngAdaptorItemHandlerMixin.java")),
            Charsets.UTF_8,
        )
        assertTrue(source.contains("@Pseudo"), "AE2 AdaptorItemHandler mixin 应允许目标类不在编译类路径")
        assertTrue(source.contains("appeng.util.inv.AdaptorItemHandler"), "应保留 AdaptorItemHandler 目标声明")
        assertFalse(source.contains("import com.llamalad7.mixinextras"), "方案 A：热路径不得持有 MixinExtras 注入器（operation.call 是 varargs，会产生 Object[] 与装箱分配）")
        assertFalse(source.contains("Ae2ItemHandlerInsertLimiter"), "热路径不得引用限流器")
        assertFalse(source.contains("insertCapped"), "热路径不得委托分片 helper")
    }

    @Test
    fun `ae2Mixin_shouldExpandBlankPatternInputSlot`() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.ae2.json")), Charsets.UTF_8)
        assertTrue(config.contains("AppEngPatternTermMixin"), "AE2 late config 应加载样板终端空白样板槽补丁")

        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/AppEngPatternTermMixin.java")),
            Charsets.UTF_8,
        )
        assertTrue(source.contains("@Pseudo"), "AE2 PatternEncoder mixin 应允许目标类不在编译类路径")
        assertTrue(source.contains("ContainerPatternTerm"), "应覆盖普通样板终端容器")
        assertTrue(source.contains("ContainerExpandedProcessingPatternTerm"), "应覆盖扩展处理样板终端容器")
        assertTrue(source.contains("ContainerWirelessPatternTerminal"), "应覆盖无线样板终端容器")
        assertTrue(source.contains("patternSlotIN"), "应只触达空白样板输入槽")
        assertTrue(source.contains("<init>*"), "应在构造结束后修正 AE 创建好的槽")
        assertTrue(source.contains("stackupup" + "$" + "setStackLimit"), "空白样板槽应调用 AE slot 的 setStackLimit")
        assertTrue(source.contains("StackLimitHooks.getCompatibilityStackSize()"), "空白样板槽应使用兼容堆叠上限")
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
