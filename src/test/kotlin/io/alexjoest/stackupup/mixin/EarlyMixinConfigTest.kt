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
    fun `containerMixin_shouldHaveOnlyMergeLimitWrapper`() {
        val source = readMixinSource("ContainerMixin.java")

        // merge limit wrapper is the only remaining logic
        assertTrue(source.contains("ContainerInsertHooks.resolveMergeSlotLimit"))
        assertTrue(source.contains("useItemAwareMergeLimit"))
        assertFalse(source.contains("putStack"))
        assertFalse(source.contains("shrinkCursorByAcceptedSlotGrowth"))
        assertFalse(source.contains("InventoryPlayer"))
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
