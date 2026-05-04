package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class EarlyMixinConfigTest {

    private fun readSource(relativePath: String) = String(
        Files.readAllBytes(Paths.get(relativePath)),
        StandardCharsets.UTF_8,
    )

    private fun readMixinSource(relativePath: String) = readSource("src/main/java/io/alexjoest/stackupup/mixin/early/$relativePath")

    @Test
    fun `earlyConfig_shouldIncludeMigratedFixedTargets`() {
        val content = readSource("src/main/resources/mixins.stackupup.early.json")

        listOf(
            "ContainerMixin", "ItemStackNbtMixin", "SlotLimitMixin",
            "VanillaInventoryLimitMixin", "EntityItemMergeMixin",
            "InventoryPlayerAddResourceMixin", "ForgeItemHandlerLimitMixin",
            "SlotItemHandlerMixin", "RenderItemMixin", "ItemStackMixin",
        ).forEach { assertTrue(content.contains(it), "$it missing from early config") }
    }

    @Test
    fun `earlyConfig_shouldSeparateClientFromServerMixins`() {
        val content = readSource("src/main/resources/mixins.stackupup.early.json")
        val mixins = content.substringAfter("\"mixins\": [").substringBefore("],")
        val client = content.substringAfter("\"client\": [").substringBefore("]")

        listOf("RenderEntityItemMixin", "RenderItemMixin", "NetHandlerPlayClientMixin").forEach { name ->
            assertTrue(client.contains(name), "$name should be in client section")
            assertFalse(mixins.contains(name), "$name should not be in mixins section")
        }
    }

    @Test
    fun `containerMixin_shouldHaveCorrectWrapperSignatures`() {
        val source = readMixinSource("ContainerMixin.java")

        // setItemStack wrapper must carry InventoryPlayer receiver
        assertTrue(source.contains("InventoryPlayer inventory,"))
        assertTrue(source.contains("original.call(inventory, cursorStack)"))

        // dropItem wrapper must carry EntityPlayer receiver
        assertTrue(source.contains("droppingPlayer.dropItem(copy, dropAround)"))
        assertTrue(source.contains("original.call(droppingPlayer, stack, dropAround)"))

        // ordinal coverage for remainder restoration
        assertTrue(source.contains("restoreRemainderToCursor"))
        assertTrue(source.contains("pendingSwapRemainder"))

        // merge shrink/grow pair
        assertTrue(source.contains("delayCursorShrinkUntilSlotGrowth"))
        assertTrue(source.contains("shrinkCursorByAcceptedSlotGrowth"))

        // ThreadLocal state cleanup
        assertTrue(source.contains("clearPendingSlotClickStateBefore"))
        assertTrue(source.contains("clearPendingSlotClickStateAfter"))
        assertTrue(source.contains("ContainerState.clear()"))

        // helper delegation to runtime package, not mixin-owned
        assertTrue(source.contains("ContainerMergeShrink"))
        assertFalse(source.contains("private static final class StackUpUpMergeShrink"))
    }

    @Test
    fun `containerState_shouldLiveOutsideMixinPackage`() {
        val stateSource = readSource("src/main/java/io/alexjoest/stackupup/ContainerState.java")
        assertTrue(stateSource.contains("ThreadLocal<ContainerMergeShrink>"))
    }

    @Test
    fun `playerPickup_shouldClampWriteBySourceStack`() {
        val source = readMixinSource("InventoryPlayerAddResourceMixin.java")

        assertTrue(source.contains("canMergeStacks"))
        assertTrue(source.contains("addResource"))
        assertTrue(source.contains("getMaxStackSize()I"))
        assertTrue(source.contains("getInventoryStackLimit()I"))
        assertTrue(source.contains("resolveInventoryClampLimit"))
    }

    @Test
    fun `entityMerge_shouldUseLargerDynamicLimit`() {
        val source = readMixinSource("EntityItemMergeMixin.java")

        assertTrue(source.contains("combineItems"))
        assertTrue(source.contains("getMaxStackSize()I"))
        assertTrue(source.contains("Math.max"))
    }

    @Test
    fun `clientSlotSyncHooks_shouldRestoreEmptySlotsFromTransmittedStacks`() {
        val source = readSource("src/main/kotlin/io/alexjoest/stackupup/ClientSlotSyncHooks.kt")

        assertTrue(source.contains("currentStack.isEmpty"))
        assertTrue(source.contains("putStack(restored)"))
    }

    @Test
    fun `clientSlotSyncMixin_shouldRepairPacketsOnly`() {
        val source = readMixinSource("NetHandlerPlayClientMixin.java")

        assertTrue(source.contains("handleSetSlot"))
        assertTrue(source.contains("handleWindowItems"))
        assertTrue(source.contains("restoreContainerSlotStackCount"))
    }
}
