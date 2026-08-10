package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class GregTechMixinSourceTest {
    @Test
    fun gregTechMetaItemMixin_shouldNormalizeDirectCallSurfaceLikeItemMixin() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.gregtech.json")), Charsets.UTF_8)
        val mixin = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/GregTechMetaItemMixin.java")),
            Charsets.UTF_8,
        )

        // 直呼面归一：early ItemMixin 只改写基类 Item 方法体，MetaItem 覆写（独立字节码）需独立注入。
        assertTrue(config.contains("GregTechMetaItemMixin"), "late config 应登记 GregTechMetaItemMixin")
        assertTrue(config.contains("GregTechMetaPrefixItemMixin"), "late config 应登记 GregTechMetaPrefixItemMixin")
        assertTrue(mixin.contains("@ModifyReturnValue"), "应使用 @ModifyReturnValue")
        assertTrue(mixin.contains("getItemStackLimit(Lnet/minecraft/item/ItemStack;)I"), "应按完整 descriptor 定位目标方法")
        assertTrue(mixin.contains("gregtech.api.items.metaitem.MetaItem"), "目标为 MetaItem")
        assertTrue(mixin.contains("lookupResolvedItemLimit"), "应先查已解析缓存（与 ItemStack 层一致）")
        assertTrue(mixin.contains("applyDynamicStackLimit"), "缓存未命中时按规则解析")
        assertTrue(mixin.contains("cacheResolvedItemLimit"), "解析结果应回写缓存")
        assertTrue(mixin.contains("shouldBypassDynamicItemRules"), "基线解析期间必须直接返回 original")
        assertTrue(mixin.contains("@Pseudo"), "GT 未加载时目标类不在编译类路径，必须 Pseudo 跳过")
        assertTrue(mixin.contains("remap = false"), "mod 类成员不做映射重写")
        assertTrue(mixin.contains("require = 0"), "目标缺失时必须静默跳过")
        assertFalse(mixin.contains("@Redirect"), "不得新增 @Redirect")
        assertFalse(mixin.contains("@Overwrite"), "不得使用 @Overwrite")

        // 字节码结构检查：编译产物必须真实携带 @Mixin/@ModifyReturnValue 注解与目标 descriptor。
        val classBytes = compiledBytes("GregTechMetaItemMixin")
        assertTrue(classBytes.containsAscii("Lorg/spongepowered/asm/mixin/Mixin;"), "编译产物应携带 @Mixin 注解")
        assertTrue(classBytes.containsAscii("Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;"), "编译产物应携带 @ModifyReturnValue")
        assertTrue(classBytes.containsAscii("getItemStackLimit(Lnet/minecraft/item/ItemStack;)I"), "编译产物应携带目标 descriptor")
    }

    @Test
    fun gregTechMetaPrefixItemMixin_shouldNormalizeDirectCallSurfaceLikeItemMixin() {
        val mixin = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/GregTechMetaPrefixItemMixin.java")),
            Charsets.UTF_8,
        )

        assertTrue(mixin.contains("@ModifyReturnValue"), "应使用 @ModifyReturnValue")
        assertTrue(mixin.contains("getItemStackLimit(Lnet/minecraft/item/ItemStack;)I"), "应按完整 descriptor 定位目标方法")
        assertTrue(mixin.contains("gregtech.api.items.materialitem.MetaPrefixItem"), "目标为 MetaPrefixItem")
        assertTrue(mixin.contains("lookupResolvedItemLimit"), "应先查已解析缓存（与 ItemStack 层一致）")
        assertTrue(mixin.contains("applyDynamicStackLimit"), "缓存未命中时按规则解析")
        assertTrue(mixin.contains("cacheResolvedItemLimit"), "解析结果应回写缓存")
        assertTrue(mixin.contains("shouldBypassDynamicItemRules"), "基线解析期间必须直接返回 original")
        assertTrue(mixin.contains("@Pseudo"), "GT 未加载时目标类不在编译类路径，必须 Pseudo 跳过")
        assertTrue(mixin.contains("remap = false"), "mod 类成员不做映射重写")
        assertTrue(mixin.contains("require = 0"), "目标缺失时必须静默跳过")
        assertFalse(mixin.contains("@Redirect"), "不得新增 @Redirect")
        assertFalse(mixin.contains("@Overwrite"), "不得使用 @Overwrite")

        val classBytes = compiledBytes("GregTechMetaPrefixItemMixin")
        assertTrue(classBytes.containsAscii("Lorg/spongepowered/asm/mixin/Mixin;"), "编译产物应携带 @Mixin 注解")
        assertTrue(classBytes.containsAscii("Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;"), "编译产物应携带 @ModifyReturnValue")
        assertTrue(classBytes.containsAscii("getItemStackLimit(Lnet/minecraft/item/ItemStack;)I"), "编译产物应携带目标 descriptor")
    }

    @Test
    fun gregTechLateMixin_shouldBeRegisteredInLoaderAndToggled() {
        assertTrue(
            StackUpUpMixinConnector().modules.any { it.config.contains("gregtech") },
            "gregtech late config 必须登记在 connector 模块表",
        )
        assertTrue(
            StackUpUpIds.LATE_GREGTECH_MIXIN_CONFIG == "mixins.stackupup.late.gregtech.json",
            "配置名常量必须与资源一致",
        )
        assertTrue(
            StackUpUpMixinConnector().shouldQueue(StackUpUpIds.LATE_GREGTECH_MIXIN_CONFIG) { it == "gregtech" },
            "modid=gregtech 存在时配置应入队",
        )
        assertFalse(
            StackUpUpMixinConnector().shouldQueue(StackUpUpIds.LATE_GREGTECH_MIXIN_CONFIG) { false },
            "modid 缺失时配置应跳过",
        )
    }

    private fun compiledBytes(className: String): ByteArray {
        val mixinClass = Class.forName("io.alexjoest.stackupup.mixin.late.$className")
        return requireNotNull(mixinClass.getResourceAsStream("$className.class")) {
            "无法读取 $className.class"
        }.use { it.readBytes() }
    }

    private fun ByteArray.containsAscii(value: String): Boolean {
        if (isEmpty()) {
            return false
        }

        val target = value.encodeToByteArray()
        val lastIndex = size - target.size
        if (lastIndex < 0) {
            return false
        }

        for (index in 0..lastIndex) {
            var matches = true
            for (offset in target.indices) {
                if (this[index + offset] != target[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                return true
            }
        }
        return false
    }
}
