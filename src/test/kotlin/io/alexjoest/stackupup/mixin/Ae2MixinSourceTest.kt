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

    @Test
    fun `ae2Mixin_shouldGuardAdaptorItemHandlerInsertCalls`() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.ae2.json")), Charsets.UTF_8)
        assertTrue(config.contains("AppEngAdaptorItemHandlerMixin"), "AE2 late config 应加载 AdaptorItemHandler 入口保险丝")

        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/AppEngAdaptorItemHandlerMixin.java")),
            Charsets.UTF_8,
        )
        assertTrue(source.contains("@Pseudo"), "AE2 AdaptorItemHandler mixin 应允许目标类不在编译类路径")
        assertTrue(source.contains("appeng.util.inv.AdaptorItemHandler"), "应优先覆盖 AE2 AdaptorItemHandler")
        assertTrue(source.contains("WrapOperation"), "应包裹 insertItem 调用以控制传入数量")
        assertTrue(source.contains("IItemHandler;insertItem(ILnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;"), "应只包裹 Forge insertItem")
        assertTrue(source.contains("Ae2ItemHandlerInsertLimiter"), "mixin 应委托 helper 统一处理 simulate/real 分片")
        assertTrue(source.contains("insertCapped"), "mixin 应委托 helper 统一处理 simulate/real 分片")
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
