package io.alexjoest.stackupup

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InventoryStackLimitBlacklistTest {
    private lateinit var previousModIds: Array<String>

    @BeforeEach
    fun saveBlacklist() {
        val blacklist = StackUpUpConfig.compat.inventoryStackLimitBlacklist
        previousModIds = blacklist.modIds
        blacklist.modIds = emptyArray()
        StackLimitHooks.refreshInventoryStackLimitBlacklist()
    }

    @AfterEach
    fun restoreBlacklist() {
        StackUpUpConfig.compat.inventoryStackLimitBlacklist.modIds = previousModIds
        StackLimitHooks.refreshInventoryStackLimitBlacklist()
    }

    @Test
    fun emptyBlacklist_shouldKeepCompatibilityPatchEnabled() {
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(TestInventory()))
    }

    @Test
    fun cachedOwnedPackage_shouldBypassMatchingInventoryOnly() {
        val packageName = TestInventory::class.java.name.substringBeforeLast('.')
        StackLimitHooks.installInventoryStackLimitBlacklistForTesting(arrayOf(packageName))

        assertTrue(StackLimitHooks.shouldBypassInventoryStackLimit(TestInventory()))
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit("not.blacklisted.Inventory"))
    }

    @Test
    fun configRefresh_shouldInvalidateClassResultCache() {
        val packageName = TestInventory::class.java.name.substringBeforeLast('.')
        StackLimitHooks.installInventoryStackLimitBlacklistForTesting(arrayOf(packageName))
        assertTrue(StackLimitHooks.shouldBypassInventoryStackLimit(TestInventory()))

        StackUpUpConfig.compat.inventoryStackLimitBlacklist.modIds = emptyArray()
        StackLimitHooks.refreshInventoryStackLimitBlacklist()

        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(TestInventory()))
    }

    @Test
    fun liveNonEmptyModIds_shouldFailClosedAndCannotReenableExpansion() {
        io.alexjoest.stackupup.core.InventoryStackLimitHook.configureExpansionEnabled(true)
        io.alexjoest.stackupup.core.InventoryStackLimitHook.prepare(256, null)
        assertTrue(io.alexjoest.stackupup.core.InventoryStackLimitHook.isExpansionEnabled())

        StackUpUpConfig.compat.inventoryStackLimitBlacklist.modIds = arrayOf("live_mod")
        StackLimitHooks.refreshInventoryStackLimitBlacklist()

        assertFalse(io.alexjoest.stackupup.core.InventoryStackLimitHook.isExpansionEnabled())
        assertEquals(
            64,
            io.alexjoest.stackupup.core.InventoryStackLimitHook.resolveInventoryStackLimit(
                "example.Inventory",
                64,
            ),
        )

        StackUpUpConfig.compat.inventoryStackLimitBlacklist.modIds = emptyArray()
        StackLimitHooks.refreshInventoryStackLimitBlacklist()
        assertFalse(io.alexjoest.stackupup.core.InventoryStackLimitHook.isExpansionEnabled())
    }

    private class TestInventory
}
