package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun ae2CapacityMixin_shouldKeepOnlyTypedInternalInventoryPaths() {
        val config = String(
            Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.capacity.late.ae2.json")),
            Charsets.UTF_8,
        )

        assertTrue(config.contains("AppEngInternalAEInventoryMixin"), "AE2 capacity config 应保留 typed AE inventory getter")
        assertTrue(config.contains("AppEngInternalInventoryMixin"), "AE2 capacity config 应保留 typed internal inventory getter")
        assertFalse(
            config.contains("AppEngPatternTermMixin"),
            "未经 pinned typed API 证据的 PatternTerm capacity path 不得 active",
        )
        assertFalse(
            Files.exists(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/ae2/AppEngPatternTermMixin.java")),
            "未经 pinned typed API 证据的反射 PatternTerm mixin 应移除",
        )
    }

    @Test
    fun ae2Profiles_shouldKeepOrdinarySafetySeparateFromTypedCapacityAndBroadTransformer() {
        val ordinary = read("src/main/resources/mixins.stackupup.late.ae2.json")
        val capacity = read("src/main/resources/mixins.stackupup.capacity.late.ae2.json")
        val transformer = read("src/main/java/io/alexjoest/stackupup/core/InventoryStackLimitTransformer.java")
        val hierarchy = read("src/main/java/io/alexjoest/stackupup/core/InventoryClassHierarchyRepository.java")

        assertTrue(ordinary.contains("AppEngAdaptorItemHandlerMixin"))
        assertTrue(ordinary.contains("AppEngInternalAEInventoryWriterMixin"))
        assertFalse(ordinary.contains("AppEngInternalAEInventoryMixin"))
        assertFalse(ordinary.contains("AppEngInternalInventoryMixin"))
        assertTrue(capacity.contains("AppEngInternalAEInventoryMixin"))
        assertTrue(capacity.contains("AppEngInternalInventoryMixin"))
        assertFalse(capacity.contains("AppEngInternalAEInventoryWriterMixin"))
        assertFalse(capacity.contains("AppEngAdaptorItemHandlerMixin"))
        assertEquals(2, Regex("AppEng(?:InternalAEInventory|InternalInventory)Mixin").findAll(capacity).count())

        assertTrue(hierarchy.contains("net/minecraft/inventory/IInventory"))
        assertTrue(transformer.contains("getInventoryStackLimit"))
        assertTrue(transformer.contains("getSlotLimit"), "broad transformer follows StackUp's IItemHandler getter coverage")
    }

    private fun read(path: String): String = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)

    private fun assertCoversSlotLimitPath(path: String) {
        val source = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
        assertFalse(source.contains("<init>"), "保留原生容量元数据，不永久改写构造器: $path")
        assertFalse(source.contains("@ModifyConstant"))
        assertFalse(source.contains("@Inject"))
        assertTrue(source.contains("import appeng.util.inv.IAEAppEngInventory;"))
        if (path.endsWith("/AppEngInternalInventoryMixin.java")) {
            assertTrue(source.contains("@Mixin(targets = \"appeng.tile.inventory.AppEngInternalInventory\", remap = false)"))
            assertTrue(source.contains("public abstract IAEAppEngInventory getTileEntity();"))
            assertTrue(source.contains("getInventoryCompatibilityStackSize(this, this.getTileEntity(), original)"))
        } else {
            assertTrue(source.contains("@Mixin(targets = \"appeng.tile.inventory.AppEngInternalAEInventory\", remap = false)"))
            assertTrue(source.contains("@Shadow @Final private IAEAppEngInventory te;"))
            assertTrue(source.contains("getInventoryCompatibilityStackSize(this, this.te, original)"))
        }
        assertTrue(source.contains("StackLimitHooks.getInventoryCompatibilityStackSize("), "按receiver与当前owner共同判断真实容量: $path")
        assertTrue(source.contains("@ModifyReturnValue"), "应覆盖真实 getSlotLimit 返回路径: $path")
        assertTrue(source.contains("getSlotLimit(I)I"), "应使用明确的 getSlotLimit(int) descriptor: $path")
        assertTrue(source.contains("original != 64"), "显式 slot 上限必须保留非默认值: $path")
        assertFalse(
            source.contains("static final int"),
            "编译期常量会被 javac 内联；mixin 内声明字段会合入目标类，除非 @Unique，故不得声明: $path",
        )
        assertTrue(source.contains("getClass().getName()"), "继承 Oversized inventory 的直接写入路径不得被误扩容: $path")
        assertFalse(
            source.contains("getInventoryStackLimit"),
            "AE2 AppEngInternal( AE)Inventory 无 getInventoryStackLimit（真实容量源为 getSlotLimit(int)）: $path",
        )
    }
}
