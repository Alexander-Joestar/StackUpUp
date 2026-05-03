package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class EarlyMixinConfigTest {
    @Test
    fun `earlyConfig_shouldIncludeMigratedFixedTargets`() {
        val content = String(
            Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.early.json")),
            StandardCharsets.UTF_8,
        )
        assertTrue(content.contains("ContainerMixin"))
        assertTrue(content.contains("ItemStackNbtMixin"))
        assertTrue(content.contains("SlotLimitMixin"))
        assertTrue(content.contains("VanillaInventoryLimitMixin"))
        assertTrue(content.contains("EntityItemMergeMixin"))
        assertTrue(content.contains("InventoryPlayerAddResourceMixin"))
        assertTrue(content.contains("ForgeItemHandlerLimitMixin"))
        assertTrue(content.contains("SlotItemHandlerMixin"))
        assertTrue(content.contains("RenderItemMixin"))
        assertTrue(content.contains("ItemStackMixin"))
    }

    @Test
    fun `clientMixin_shouldBeInClientSection`() {
        val content = String(
            Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.early.json")),
            StandardCharsets.UTF_8,
        )
        val mixinsSection = content.substringAfter("\"mixins\": [").substringBefore("],")
        val clientSection = content.substringAfter("\"client\": [").substringBefore("]")
        assertTrue(clientSection.contains("RenderEntityItemMixin"))
        assertTrue(clientSection.contains("RenderItemMixin"))
        assertTrue(!mixinsSection.contains("RenderEntityItemMixin"))
        assertTrue(!mixinsSection.contains("RenderItemMixin"))
    }

    @Test
    fun `playerPickup_shouldClampWriteBySourceStack`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/early/InventoryPlayerAddResourceMixin.java"),
            ),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("method = \"canMergeStacks(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z\""))
        assertTrue(source.contains("method = \"addResource(ILnet/minecraft/item/ItemStack;)I\""))
        assertTrue(source.contains("target = \"Lnet/minecraft/item/ItemStack;getMaxStackSize()I\""))
        assertTrue(source.contains("target = \"Lnet/minecraft/entity/player/InventoryPlayer;getInventoryStackLimit()I\""))
        assertTrue(source.contains("resolveInventoryClampLimit(incoming, inventory.getInventoryStackLimit())"))
        assertTrue(source.contains("return source.getMaxStackSize()"))
        assertTrue(source.contains("resolveInventoryClampLimit(source, inventory.getInventoryStackLimit())"))
    }

    @Test
    fun `entityMerge_shouldUseLargerDynamicLimit`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/early/EntityItemMergeMixin.java"),
            ),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("method = \"combineItems(Lnet/minecraft/entity/item/EntityItem;)Z\""))
        assertTrue(source.contains("target = \"Lnet/minecraft/item/ItemStack;getMaxStackSize()I\""))
        assertTrue(source.contains("Math.max(candidate.getMaxStackSize(), current.getMaxStackSize())"))
    }

    @Test
    fun `clientSlotSyncMixin_shouldStayClientOnly`() {
        val content = String(
            Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.early.json")),
            StandardCharsets.UTF_8,
        )
        val mixinsSection = content.substringAfter("\"mixins\": [").substringBefore("],")
        val clientSection = content.substringAfter("\"client\": [").substringBefore("]")
        assertTrue(clientSection.contains("NetHandlerPlayClientMixin"))
        assertTrue(!mixinsSection.contains("NetHandlerPlayClientMixin"))
    }

    @Test
    fun `clientSlotSyncMixin_shouldRepairWindowAndSlotPacketsOnly`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/early/NetHandlerPlayClientMixin.java"),
            ),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("handleSetSlot(Lnet/minecraft/network/play/server/SPacketSetSlot;)V"))
        assertTrue(source.contains("handleWindowItems(Lnet/minecraft/network/play/server/SPacketWindowItems;)V"))
        assertTrue(source.contains("restoreContainerSlotStackCount"))
        assertTrue(source.contains("restoreContainerSlotStackCounts"))
    }

    @Test
    fun `clientSlotSyncHooks_shouldRestoreEmptySlotsFromTransmittedStacks`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/io/alexjoest/stackupup/ClientSlotSyncHooks.kt"),
            ),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("if (currentStack.isEmpty)"))
        assertTrue(source.contains("container.getSlot(slotId).putStack(restored)"))
    }

    @Test
    fun `containerClickRemainder_shouldCoverAllSlotClickPaths`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/early/ContainerMixin.java"),
            ),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("restoreRemainderToCursor"))
        assertTrue(source.contains("ordinal = 0"))
        assertTrue(source.contains("ordinal = 1"))
        assertTrue(source.contains("ordinal = 4"))
        assertTrue(source.contains("ordinal = 7"))
        assertTrue(source.contains("pendingSwapRemainder"))
    }

    @Test
    fun `containerClickMerge_shouldShrinkCursorAfterAcceptedSlotGrowth`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/early/ContainerMixin.java"),
            ),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("delayCursorShrinkUntilSlotGrowth"))
        assertTrue(source.contains("shrinkCursorByAcceptedSlotGrowth"))
        assertTrue(source.contains("slotStack.getCount() - beforeCount"))
    }

    @Test
    fun `containerClickRemainder_shouldClearThreadLocalState`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/early/ContainerMixin.java"),
            ),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("clearPendingSlotClickStateBefore"))
        assertTrue(source.contains("clearPendingSlotClickStateAfter"))
        assertTrue(source.contains("ContainerState.clear()"))
    }
}
