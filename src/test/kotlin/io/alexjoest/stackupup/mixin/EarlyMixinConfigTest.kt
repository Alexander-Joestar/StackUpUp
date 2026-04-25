package io.alexjoest.stackupup.mixin

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EarlyMixinConfigTest {
    @Test
    fun `早期 mixin 配置应包含已迁移的固定目标`() {
        val content = String(
            Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.early.json")),
            StandardCharsets.UTF_8
        )
        assertTrue(content.contains("ContainerMixin"))
        assertTrue(content.contains("ItemStackNbtMixin"))
        assertTrue(content.contains("SlotLimitMixin"))
        assertTrue(content.contains("VanillaInventoryLimitMixin"))
        assertTrue(content.contains("InventoryPlayerAddResourceMixin"))
        assertTrue(content.contains("ForgeItemHandlerLimitMixin"))
        assertTrue(content.contains("SlotItemHandlerMixin"))
        assertTrue(content.contains("RenderItemMixin"))
        assertTrue(content.contains("ItemStackMixin"))
    }

    @Test
    fun `客户端专用早期 mixin 应放在 client 段`() {
        val content = String(
            Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.early.json")),
            StandardCharsets.UTF_8
        )
        val mixinsSection = content.substringAfter("\"mixins\": [").substringBefore("],")
        val clientSection = content.substringAfter("\"client\": [").substringBefore("]")
        assertTrue(clientSection.contains("RenderEntityItemMixin"))
        assertTrue(clientSection.contains("RenderItemMixin"))
        assertTrue(!mixinsSection.contains("RenderEntityItemMixin"))
        assertTrue(!mixinsSection.contains("RenderItemMixin"))
    }

    @Test
    fun `玩家拾取路径应按源物品栈修正库存写入上限`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/early/InventoryPlayerAddResourceMixin.java")
            ),
            StandardCharsets.UTF_8
        )

        assertTrue(source.contains("method = \"addResource(ILnet/minecraft/item/ItemStack;)I\""))
        assertTrue(source.contains("target = \"Lnet/minecraft/item/ItemStack;getMaxStackSize()I\""))
        assertTrue(source.contains("target = \"Lnet/minecraft/entity/player/InventoryPlayer;getInventoryStackLimit()I\""))
        assertTrue(source.contains("return source.getMaxStackSize()"))
        assertTrue(source.contains("resolveInventoryClampLimit(source, inventory.getInventoryStackLimit())"))
    }
}
