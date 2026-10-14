package io.alexjoest.stackupup

import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
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
        resetInventoryExpansionForTesting()
        val blacklist = StackUpUpConfig.compat.inventoryStackLimitBlacklist
        previousModIds = blacklist.modIds
        blacklist.modIds = emptyArray()
        StackLimitHooks.refreshInventoryStackLimitBlacklist(emptyList(), emptyMap())
    }

    @AfterEach
    fun restoreBlacklist() {
        StackUpUpConfig.compat.inventoryStackLimitBlacklist.modIds = previousModIds
        resetInventoryExpansionForTesting()
        StackLimitHooks.refreshInventoryStackLimitBlacklist(emptyList(), emptyMap())
    }

    @Test
    fun emptyBlacklist_shouldExpandUnlistedInventory() {
        io.alexjoest.stackupup.core.InventoryStackLimitHook.configureExpansionEnabled(true)
        io.alexjoest.stackupup.core.InventoryStackLimitHook.prepare(256, null)

        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(TestInventory()))
        assertEquals(
            256,
            io.alexjoest.stackupup.core.InventoryStackLimitHook.resolveInventoryStackLimit(
                TestInventory::class.java.name,
                64,
            ),
        )
    }

    @Test
    fun cachedOwnedPackage_shouldBypassMatchingInventoryOnly() {
        val packageName = TestInventory::class.java.name.substringBeforeLast('.')
        StackLimitHooks.installInventoryStackLimitBlacklistForTesting(arrayOf(packageName))

        assertTrue(StackLimitHooks.shouldBypassInventoryStackLimit(TestInventory()))
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit("not.blacklisted.Inventory"))
    }

    @Test
    fun ownedPackageMatching_requiresANameBoundary() {
        io.alexjoest.stackupup.core.InventoryStackLimitHook.configureExpansionEnabled(true)
        io.alexjoest.stackupup.core.InventoryStackLimitHook.prepare(256, arrayOf("listed.inventory"))
        assertEquals(64, io.alexjoest.stackupup.core.InventoryStackLimitHook.resolveInventoryStackLimit("listed.inventory.Storage", 64))
        assertEquals(64, io.alexjoest.stackupup.core.InventoryStackLimitHook.resolveInventoryStackLimit("listed.inventory.Storage\$Inner", 64))
        assertEquals(256, io.alexjoest.stackupup.core.InventoryStackLimitHook.resolveInventoryStackLimit("listed.inventoryaddon.Storage", 64))
        assertEquals(256, io.alexjoest.stackupup.core.InventoryStackLimitHook.resolveInventoryStackLimit("other.listed.inventory.Storage", 64))
    }

    @Test
    fun receiverlessClassLookup_doesNotBecomeBlacklisted() {
        val packageName = TestInventory::class.java.name.substringBeforeLast('.')
        StackLimitHooks.installInventoryStackLimitBlacklistForTesting(arrayOf(packageName))

        assertFalse(io.alexjoest.stackupup.core.InventoryStackLimitHook.isBlacklisted(null))
    }

    @Test
    fun configRefresh_shouldInvalidateClassResultCache() {
        val packageName = TestInventory::class.java.name.substringBeforeLast('.')
        StackLimitHooks.installInventoryStackLimitBlacklistForTesting(arrayOf(packageName))
        assertTrue(StackLimitHooks.shouldBypassInventoryStackLimit(TestInventory()))

        StackUpUpConfig.compat.inventoryStackLimitBlacklist.modIds = emptyArray()
        StackLimitHooks.refreshInventoryStackLimitBlacklist(emptyList(), emptyMap())

        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(TestInventory()))
    }

    @Test
    fun selectedModIds_shouldBypassOnlyTheirOwnedPackages() {
        val previousMaxStackSize = StackUpUpConfig.maxStackSize
        try {
            StackUpUpConfig.maxStackSize = 256
            io.alexjoest.stackupup.core.InventoryStackLimitHook.configureExpansionEnabled(true)
            val packageName = TestInventory::class.java.name.substringBeforeLast('.')
            StackLimitHooks.refreshInventoryStackLimitBlacklist(
                listOf("listed_mod"),
                mapOf("listed_mod" to setOf(packageName), "unlisted_mod" to setOf("unlisted")),
            )

            assertTrue(io.alexjoest.stackupup.core.InventoryStackLimitHook.isExpansionEnabled())
            assertEquals(
                64,
                io.alexjoest.stackupup.core.InventoryStackLimitHook.resolveInventoryStackLimit(
                    TestInventory::class.java.name,
                    64,
                ),
            )
            assertEquals(
                256,
                io.alexjoest.stackupup.core.InventoryStackLimitHook.resolveInventoryStackLimit(
                    "unlisted.Inventory",
                    64,
                ),
            )
            assertEquals(256, StackLimitHooks.getInventoryCompatibilityStackSize())
        } finally {
            StackUpUpConfig.maxStackSize = previousMaxStackSize
        }
    }

    private class TestInventory
}
