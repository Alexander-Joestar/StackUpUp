package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class Ae2MixinSourceTest {
    @Test
    fun ae2Mixin_shouldCoverConstructorConstantAndNotDeadInjectMissingMethod() {
        assertCoversConstructorOnly("src/main/java/io/alexjoest/stackupup/mixin/late/AppEngInternalInventoryMixin.java")
        assertCoversConstructorOnly("src/main/java/io/alexjoest/stackupup/mixin/late/AppEngInternalAEInventoryMixin.java")
    }

    @Test
    fun ae2Mixin_shouldKeepAdaptorItemHandlerAsZeroLogicFuse() {
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
    fun ae2Mixin_shouldExpandBlankPatternInputSlot() {
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

    /**
     * 仅断言构造期常量补丁：AE2 两个内部库存类的真实容量源是 getSlotLimit(int)（热路径零逻辑原则下
     * 不注入），getInventoryStackLimit 在目标类中不存在，旧 ModifyReturnValue 属死注入，必须不再出现。
     */
    private fun assertCoversConstructorOnly(path: String) {
        val source = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
        assertTrue(source.contains("@ModifyConstant"), "应继续覆盖构造期常量: $path")
        assertTrue(source.contains("<init>*"), "应继续覆盖构造器: $path")
        assertTrue(source.contains("private static int replaceCompatibilityLimit"), "构造器常量补丁必须使用 static handler: $path")
        assertTrue(source.contains("StackLimitHooks.getCompatibilityStackSize()"), "构造器常量应替换为兼容堆叠上限: $path")
        assertFalse(
            source.contains("getInventoryStackLimit"),
            "AE2 AppEngInternal( AE)Inventory 无 getInventoryStackLimit（真实容量源为 getSlotLimit(int)），不得保留死注入: $path",
        )
        assertFalse(source.contains("ModifyReturnValue"), "热路径零逻辑：不得对不存在的方法做 ModifyReturnValue 死注入: $path")
    }
}
