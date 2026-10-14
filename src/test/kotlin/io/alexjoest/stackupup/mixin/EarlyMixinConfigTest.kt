package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 结构检查（非行为验证）：核对 early mixin 配置与注入形态的静态不变量。
 * 行为等价由 StackLimitHooksTest / VanillaInventoryConservationTest 等行为测试承担。
 */
class EarlyMixinConfigTest {

    private fun readSource(relativePath: String) = String(
        Files.readAllBytes(Paths.get(relativePath)),
        StandardCharsets.UTF_8,
    )

    private fun readMixinSource(relativePath: String) = readSource("src/main/java/io/alexjoest/stackupup/mixin/early/$relativePath")

    @Test
    fun earlyConfig_shouldIncludeMigratedFixedTargets() {
        val content = readSource("src/main/resources/mixins.stackupup.early.json")
        val capacityContent = readSource("src/main/resources/mixins.stackupup.capacity.early.json")

        listOf("ItemStackNbtMixin", "EntityItemMergeMixin", "RenderItemMixin").forEach {
            assertTrue(content.contains(it), "$it missing from early config")
        }
        val baseline = readSource("src/main/resources/mixins.stackupup.baseline.early.json")
        listOf(
            "ContainerMixin",
            "SlotLimitMixin",
            "InventoryPlayerAddResourceMixin",
            "InventoryWriteLimitMixin",
            "SlotItemHandlerMixin",
            "VanillaInventoryLimitMixin",
            "EntityEquipmentInventoryLimitMixin",
        ).forEach {
            assertTrue(baseline.contains(it), "$it missing from baseline early config")
            assertFalse(capacityContent.contains("\"$it\""), "$it must not depend on compatibility expansion")
        }
        assertTrue(capacityContent.contains("ForgeItemHandlerLimitMixin"))
    }

    @Test
    fun inventoryWriteLimit_shouldStayOnBaselineAndUseItemAwareWriteLimit() {
        val source = readMixinSource("InventoryWriteLimitMixin.java")

        assertTrue(source.contains("TileEntityLockableLoot.class"))
        assertTrue(source.contains("setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V"))
        assertTrue(source.contains("resolveInventoryWriteLimit"))
        assertFalse(source.contains("isExpansionEnabled"))
    }

    @Test
    fun earlyConfig_shouldSeparateClientFromServerMixins() {
        val content = readSource("src/main/resources/mixins.stackupup.early.json")
        val mixins = content.substringAfter("\"mixins\": [").substringBefore("],")
        val client = content.substringAfter("\"client\": [").substringBefore("]")

        listOf("RenderEntityItemMixin", "RenderItemMixin", "NetHandlerPlayClientMixin").forEach { name ->
            assertTrue(client.contains(name), "$name should be in client section")
            assertFalse(mixins.contains(name), "$name should not be in mixins section")
        }
    }

    @Test
    fun containerMixin_shouldHaveOnlyMergeLimitWrapper() {
        val source = readMixinSource("ContainerMixin.java")

        // merge limit wrapper is the only remaining logic
        assertTrue(source.contains("ContainerInsertHooks.resolveMergeSlotLimit"))
        assertTrue(source.contains("useItemAwareMergeLimit"))
        assertFalse(source.contains("putStack"))
        assertFalse(source.contains("shrinkCursorByAcceptedSlotGrowth"))
        assertFalse(source.contains("InventoryPlayer"))
    }

    @Test
    fun playerPickup_shouldClampWriteBySourceStack() {
        val source = readMixinSource("InventoryPlayerAddResourceMixin.java")

        assertTrue(source.contains("canMergeStacks"))
        assertTrue(source.contains("addResource"))
        assertTrue(source.contains("getMaxStackSize()I"))
        assertTrue(source.contains("getInventoryStackLimit()I"))
        assertTrue(source.contains("resolveInventoryClampLimit"))
        assertTrue(source.contains("(InventoryPlayer) (Object) this"))
    }

    @Test
    fun entityMerge_shouldUseLargerDynamicLimit() {
        val source = readMixinSource("EntityItemMergeMixin.java")

        assertTrue(source.contains("combineItems"))
        assertTrue(source.contains("getMaxStackSize()I"))
        assertTrue(source.contains("Math.max"))
    }

    @Test
    fun resolveInventoryClampLimit_shouldKeepTwoCallSites() {
        // P0 事实 c：迁移后 resolveInventoryClampLimit 调用方仍为 2（useMergeLimit + usePickedStackLimit）。
        val source = readMixinSource("InventoryPlayerAddResourceMixin.java")

        assertEquals(2, source.split("resolveInventoryClampLimit").size - 1, "resolveInventoryClampLimit 调用次数应保持 2")
        assertEquals(2, source.split("(InventoryPlayer) (Object) this").size - 1, "两个生产 clamp 调用都必须传入 InventoryPlayer receiver")
    }

    @Test
    fun migratedMixin_shouldNotUseRedirectOrOverwrite() {
        // T14.7 M1/M2：@Redirect -> @ModifyExpressionValue 后不得回退到 @Redirect/@Overwrite。
        listOf("EntityItemMergeMixin.java", "InventoryPlayerAddResourceMixin.java").forEach { name ->
            val source = readMixinSource(name)

            assertFalse(source.contains("@Redirect"), "$name 不应使用 @Redirect")
            assertFalse(source.contains("@Overwrite"), "$name 不应使用 @Overwrite")
        }
    }

    @Test
    fun clientSlotSyncHooks_shouldRestoreEmptySlotsFromTransmittedStacks() {
        val source = readSource("src/main/kotlin/io/alexjoest/stackupup/ClientSlotSyncHooks.kt")

        assertTrue(source.contains("currentStack.isEmpty"))
        assertTrue(source.contains("putStack(restored)"))
    }

    @Test
    fun clientSlotSyncMixin_shouldRepairPacketsOnly() {
        val source = readMixinSource("NetHandlerPlayClientMixin.java")

        assertTrue(source.contains("handleSetSlot"))
        assertTrue(source.contains("handleWindowItems"))
        assertTrue(source.contains("restoreContainerSlotStackCount"))
    }
}
