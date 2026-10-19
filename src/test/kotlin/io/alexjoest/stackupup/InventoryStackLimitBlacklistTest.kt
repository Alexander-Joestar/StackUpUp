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
    fun disabledPolicy_shouldKeepOriginalAndRejectClassLoadPatch() {
        io.alexjoest.stackupup.core.InventoryStackLimitHook.configureExpansionEnabled(false)
        io.alexjoest.stackupup.core.InventoryStackLimitHook.prepare(256, null)

        val policy = io.alexjoest.stackupup.core.InventoryStackLimitHook.classLoadPolicy()
        assertFalse(policy.isExpansionEnabled)
        assertTrue(policy.isPrepared)
        assertEquals(256, policy.compatibilityLimit)
        assertFalse(policy.shouldPatch("example.Inventory"))
        assertEquals(64, policy.resolveInventoryStackLimit("example.Inventory", 64))
    }

    @Test
    fun classLoadPolicy_shouldSnapshotLimitAndBlacklist() {
        io.alexjoest.stackupup.core.InventoryStackLimitHook.configureExpansionEnabled(true)
        io.alexjoest.stackupup.core.InventoryStackLimitHook.prepare(256, arrayOf("listed.inventory"))
        val captured = io.alexjoest.stackupup.core.InventoryStackLimitHook.classLoadPolicy()

        io.alexjoest.stackupup.core.InventoryStackLimitHook.prepare(128, emptyArray())

        assertEquals(256, captured.compatibilityLimit)
        assertTrue(captured.isBlacklisted("listed.inventory.Storage"))
        assertFalse(captured.isBlacklisted("listed.inventoryaddon.Storage"))
        assertFalse(captured.shouldPatch("listed.inventory.Storage"))
        assertTrue(captured.shouldPatch("other.inventory.Storage"))
        assertEquals(256, captured.resolveInventoryStackLimit("other.inventory.Storage", 64))
    }

    @Test
    fun inheritedReceiver_shouldNotInheritExactBlacklistBypass() {
        io.alexjoest.stackupup.core.InventoryStackLimitHook.configureExpansionEnabled(true)
        io.alexjoest.stackupup.core.InventoryStackLimitHook.prepare(
            256,
            arrayOf(BlacklistedParent::class.java.name),
        )

        assertTrue(io.alexjoest.stackupup.core.InventoryStackLimitHook.isBlacklisted(BlacklistedParent::class.java))
        assertFalse(io.alexjoest.stackupup.core.InventoryStackLimitHook.isBlacklisted(AddonChild::class.java))
        assertEquals(
            256,
            io.alexjoest.stackupup.core.InventoryStackLimitHook.resolveInventoryStackLimit(
                AddonChild::class.java,
                64,
            ),
        )
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

    private open class BlacklistedParent

    private class AddonChild : BlacklistedParent()
}
