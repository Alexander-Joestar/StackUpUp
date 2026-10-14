package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class Ae2MixinSourceTest {
    @Test
    fun ae2Mixin_shouldCoverSlotLimitPathAndNotDeadInventoryGetterInject() {
        assertCoversSlotLimitPath("src/main/java/io/alexjoest/stackupup/mixin/late/ae2/AppEngInternalInventoryMixin.java")
        assertCoversSlotLimitPath("src/main/java/io/alexjoest/stackupup/mixin/late/ae2/AppEngInternalAEInventoryMixin.java")
    }

    @Test
    fun ae2WriterMixin_shouldBeAlwaysLoadedAndPersistNativeMutations() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.ae2.json")), Charsets.UTF_8)
        assertTrue(config.contains("AppEngInternalAEInventoryWriterMixin"), "AE2 writer fix must load with AE2, independent of expansion")
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/ae2/AppEngInternalAEInventoryWriterMixin.java")),
            Charsets.UTF_8,
        )
        assertTrue(source.contains("@Pseudo"))
        assertTrue(source.contains("@Shadow @Final private IAEItemStack[] inv;"))
        assertTrue(source.contains("@WrapOperation"))
        assertTrue(source.contains("ItemStack;grow(I)V"))
        assertTrue(source.contains("insertItem(ILnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;"))
        assertTrue(source.contains("extractItem(IIZ)Lnet/minecraft/item/ItemStack;"))
        assertTrue(source.contains("setStackSize(instance.getCount())"))
    }

    @Test
    fun ae2Mixin_shouldKeepAdaptorItemHandlerAsZeroLogicFuse() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.ae2.json")), Charsets.UTF_8)
        assertTrue(config.contains("AppEngAdaptorItemHandlerMixin"), "AE2 late config 应继续加载 AdaptorItemHandler 入口保险丝")

        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/ae2/AppEngAdaptorItemHandlerMixin.java")),
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
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.capacity.late.ae2.json")), Charsets.UTF_8)
        assertTrue(config.contains("AppEngPatternTermMixin"), "AE2 late config 应加载样板终端空白样板槽补丁")

        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/ae2/AppEngPatternTermMixin.java")),
            Charsets.UTF_8,
        )
        assertTrue(source.contains("@Pseudo"), "AE2 PatternEncoder mixin 应允许目标类不在编译类路径")
        assertTrue(source.contains("ContainerPatternTerm"), "应覆盖普通样板终端容器")
        assertTrue(source.contains("ContainerExpandedProcessingPatternTerm"), "应覆盖扩展处理样板终端容器")
        assertTrue(source.contains("ContainerWirelessPatternTerminal"), "应覆盖无线样板终端容器")
        assertTrue(source.contains("patternSlotIN"), "应只触达空白样板输入槽")
        assertTrue(source.contains("<init>*"), "应在构造结束后修正 AE 创建好的槽")
        assertTrue(source.contains("stackupup" + "$" + "setStackLimit"), "空白样板槽应调用 AE slot 的 setStackLimit")
        assertTrue(source.contains("StackLimitHooks.getInventoryCompatibilityStackSize(this, 64)"), "空白样板槽应使用兼容堆叠上限")
    }

    private fun assertCoversSlotLimitPath(path: String) {
        val source = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
        assertFalse(source.contains("<init>"), "保留原生容量元数据，不永久改写构造器: $path")
        assertFalse(source.contains("@ModifyConstant"))
        assertFalse(source.contains("@Inject"))
        assertTrue(source.contains("import appeng.util.inv.IAEAppEngInventory;"))
        if (path.endsWith("/AppEngInternalInventoryMixin.java")) {
            assertTrue(source.contains("public abstract IAEAppEngInventory getTileEntity();"))
            assertTrue(source.contains("getInventoryCompatibilityStackSize(this, this.getTileEntity(), original)"))
        } else {
            assertTrue(source.contains("@Shadow @Final private IAEAppEngInventory te;"))
            assertTrue(source.contains("getInventoryCompatibilityStackSize(this, this.te, original)"))
        }
        assertTrue(source.contains("StackLimitHooks.getInventoryCompatibilityStackSize("), "按receiver与当前owner共同判断真实容量: $path")
        assertTrue(source.contains("@ModifyReturnValue"), "应覆盖真实 getSlotLimit 返回路径: $path")
        assertTrue(source.contains("getSlotLimit(I)I"), "应使用明确的 getSlotLimit(int) descriptor: $path")
        assertTrue(source.contains("VANILLA_STACK_LIMIT"), "显式 slot 上限必须保留非默认值: $path")
        assertTrue(source.contains("getClass().getName()"), "继承 Oversized inventory 的直接写入路径不得被误扩容: $path")
        assertFalse(
            source.contains("getInventoryStackLimit"),
            "AE2 AppEngInternal( AE)Inventory 无 getInventoryStackLimit（真实容量源为 getSlotLimit(int)）: $path",
        )
    }
}
