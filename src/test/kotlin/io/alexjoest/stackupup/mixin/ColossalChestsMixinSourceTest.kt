package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class ColossalChestsMixinSourceTest {
    @Test
    fun `colossalChestsTileMixin_shouldPatchInventoryConstructionStackLimit`() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.colossalchests.json")), Charsets.UTF_8)
        val mixin = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/ColossalChestsTileMixin.java")),
            Charsets.UTF_8,
        )

        // 字段级源头修正：constructInventory/constructInventoryDebug 的 4 处 64 字面量全部是
        // `new IndexedInventory/LargeInventory(size, name, 64)` 的第三参（栈上限），一个 handler 覆盖两方法。
        assertTrue(config.contains("ColossalChestsTileMixin"), "late config 应登记 ColossalChestsTileMixin")
        assertTrue(mixin.contains("@ModifyConstant"), "应使用 @ModifyConstant 替换构造参数字面量")
        assertTrue(mixin.contains("constructInventory"), "应覆盖 constructInventory")
        assertTrue(mixin.contains("constructInventoryDebug"), "应覆盖 constructInventoryDebug")
        assertTrue(mixin.contains("intValue = 64"), "源码声明了 intValue = 64（结构断言，不证明运行时命中范围）")
        assertFalse(mixin.contains("intValue = 0"), "源码未声明 intValue = 0（结构断言）")
        assertTrue(mixin.contains("StackLimitHooks.getCompatibilityStackSize()"), "64 应替换为兼容堆叠上限")
        assertTrue(mixin.contains("org.cyclops.colossalchests.tileentity.TileColossalChest"), "目标为 TileColossalChest")
        assertTrue(mixin.contains("@Pseudo"), "mod 未加载时目标类不在编译类路径，必须 Pseudo 跳过")
        assertTrue(mixin.contains("remap = false"), "mod 类成员不做映射重写")
        assertTrue(mixin.contains("require = 0"), "目标缺失时必须静默跳过")
        assertFalse(mixin.contains("@Redirect"), "不得新增 @Redirect")
        assertFalse(mixin.contains("@Overwrite"), "不得使用 @Overwrite")

        // 字节码结构检查：编译产物必须真实携带 @Mixin/@ModifyConstant 注解与目标方法名
        // （注解值以 UTF8 存于常量池，containsAscii 可证），防止源文件与编译产物脱节。
        val mixinClass = Class.forName("io.alexjoest.stackupup.mixin.late.ColossalChestsTileMixin")
        val classBytes = requireNotNull(mixinClass.getResourceAsStream("ColossalChestsTileMixin.class")) {
            "无法读取 ColossalChestsTileMixin.class"
        }.use { it.readBytes() }

        assertTrue(classBytes.containsAscii("Lorg/spongepowered/asm/mixin/Mixin;"), "编译产物应携带 @Mixin 注解")
        assertTrue(classBytes.containsAscii("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;"), "编译产物应携带 @ModifyConstant 注入器")
        assertTrue(classBytes.containsAscii("constructInventory"), "编译产物应携带 constructInventory 目标")
        assertTrue(classBytes.containsAscii("constructInventoryDebug"), "编译产物应携带 constructInventoryDebug 目标")
        assertTrue(classBytes.containsAscii("io/alexjoest/stackupup/StackLimitHooks"), "编译产物应引用 StackLimitHooks")
    }

    @Test
    fun `colossalChestsLateMixin_shouldBeRegisteredInLoaderAndToggled`() {
        assertTrue(
            StackUpUpMixinConnector().modules.any { it.config.contains("colossalchests") },
            "colossalchests late config 必须登记在 connector 模块表",
        )
        assertTrue(
            StackUpUpIds.LATE_COLOSSALCHESTS_MIXIN_CONFIG == "mixins.stackupup.late.colossalchests.json",
            "配置名常量必须与资源一致",
        )
        assertTrue(
            StackUpUpMixinConnector().shouldQueue(StackUpUpIds.LATE_COLOSSALCHESTS_MIXIN_CONFIG) { it == "colossalchests" },
            "modid=colossalchests 存在时配置应入队",
        )
        assertFalse(
            StackUpUpMixinConnector().shouldQueue(StackUpUpIds.LATE_COLOSSALCHESTS_MIXIN_CONFIG) { false },
            "modid 缺失时配置应跳过",
        )
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
