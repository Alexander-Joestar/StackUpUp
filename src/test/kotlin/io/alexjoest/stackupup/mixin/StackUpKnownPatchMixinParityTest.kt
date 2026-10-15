package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Structural source/config mapping only; these checks do not prove third-party write-path safety. */
class StackUpKnownPatchMixinParityTest {
    private data class VanillaMapping(
        val legacyPatch: String,
        val mixinSource: String,
        val methodDescriptor: String,
        val config: String,
        val section: String,
        val sourceMarkers: List<String>,
    )

    private enum class LegacyParityStatus {
        UNKNOWN,
    }

    private data class OptionalLegacyMapping(
        val modId: String,
        val legacyTarget: String,
        val legacyMethodSelector: String,
        val currentMixin: String,
        val currentMixinClass: String,
        val currentDescriptor: String?,
        val ordinaryConfig: String,
        val capacityConfig: String,
        val parity: LegacyParityStatus,
        val unknownReason: String,
        val legacyDescriptor: String? = null,
    )

    private data class ConfiguredMixin(val className: String, val config: String, val section: String)

    private val vanillaMappings = listOf(
        VanillaMapping(
            "NetHandlerPlayServerPatch.patchCreativeInventory",
            "src/main/java/io/alexjoest/stackupup/mixin/early/NetHandlerPlayServerMixin.java",
            "processCreativeInventoryAction(Lnet/minecraft/network/play/client/CPacketCreativeInventoryAction;)V",
            "mixins.stackupup.early.json",
            "mixins",
            listOf("isValidCreativeStackPacket", "@Inject("),
        ),
        VanillaMapping(
            "MaxStackConstantPatch.patchMaxLimit(ServerRecipeBookHelper.func_194324_a)",
            "src/main/java/io/alexjoest/stackupup/mixin/early/ServerRecipeBookHelperMixin.java",
            "func_194324_a(IZ)I",
            "mixins.stackupup.capacity.early.json",
            "mixins",
            listOf("@ModifyConstant", "@Constant(intValue = 64)", "getInventoryCompatibilityStackSize"),
        ),
        VanillaMapping(
            "InventoryHelperPerformanceSplice.spawnItemStack",
            "src/main/java/io/alexjoest/stackupup/mixin/early/InventoryHelperMixin.java",
            "spawnItemStack(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)V",
            "mixins.stackupup.early.json",
            "mixins",
            listOf("MIN_SPLIT_THRESHOLD", "(itemMaxStackSize + 7) / 8", "(itemMaxStackSize + 1) / 2", "stack.splitStack("),
        ),
        VanillaMapping(
            "RenderEntityItemSplice.getModelCount",
            "src/main/java/io/alexjoest/stackupup/mixin/early/RenderEntityItemMixin.java",
            "getModelCount(Lnet/minecraft/item/ItemStack;)I",
            "mixins.stackupup.early.json",
            "client",
            listOf("StackRenderHooks.getModelCount"),
        ),
        VanillaMapping(
            "RenderEntityItemPatch.patchDistanceConstant",
            "src/main/java/io/alexjoest/stackupup/mixin/early/RenderEntityItemMixin.java",
            "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
            "mixins.stackupup.early.json",
            "client",
            listOf("getItemRenderDistanceNeg", "getItemRenderDistance(entity)"),
        ),
        VanillaMapping(
            "RenderItemPatch.patchDrawItemCount",
            "src/main/java/io/alexjoest/stackupup/mixin/early/RenderItemMixin.java",
            "renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
            "mixins.stackupup.early.json",
            "client",
            listOf("@WrapOperation", "StackRenderHooks.drawItemCountWithShadow"),
        ),
    )

    private val optionalLegacyMappings = listOf(
        OptionalLegacyMapping(
            "refinedstorage",
            "com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler*",
            "onExtract",
            "src/main/java/io/alexjoest/stackupup/mixin/late/refinedstorage/ItemGridHandlerMixin.java",
            "ItemGridHandlerMixin",
            "onExtract(Lnet/minecraft/entity/player/EntityPlayerMP;Ljava/util/UUID;II)V",
            "mixins.stackupup.late.refinedstorage.json",
            "mixins.stackupup.capacity.late.refinedstorage.json",
            LegacyParityStatus.UNKNOWN,
            "Legacy StackUp selected a class-name prefix and method name without a descriptor; no pinned 1.12 target source is available to prove this overload is equivalent.",
        ),
        OptionalLegacyMapping(
            "mantle",
            "slimeknights.mantle.tileentity.TileInventory",
            "<init>",
            "src/main/java/io/alexjoest/stackupup/mixin/late/mantle/TileInventoryMixin.java",
            "TileInventoryMixin",
            null,
            "mixins.stackupup.late.mantle.json",
            "mixins.stackupup.capacity.late.mantle.json",
            LegacyParityStatus.UNKNOWN,
            "Legacy StackUp selected constructors by name only; the target dependency/source is absent, so no exact constructor descriptor can be established.",
        ),
        OptionalLegacyMapping(
            "ic2",
            "ic2.core.block.invslot.InvSlot",
            "<init>",
            "src/main/java/io/alexjoest/stackupup/mixin/late/ic2/InvSlotMixin.java",
            "InvSlotMixin",
            null,
            "mixins.stackupup.late.ic2.json",
            "mixins.stackupup.capacity.late.ic2.json",
            LegacyParityStatus.UNKNOWN,
            "Legacy StackUp selected constructors by name only; this workspace has IC2 Classic rather than the original IC2 target source.",
        ),
        OptionalLegacyMapping(
            "appliedenergistics2",
            "appeng.tile.inventory.AppEngInternalInventory",
            "<init>",
            "src/main/java/io/alexjoest/stackupup/mixin/late/ae2/AppEngInternalInventoryMixin.java",
            "AppEngInternalInventoryMixin",
            "getSlotLimit(I)I",
            "mixins.stackupup.late.ae2.json",
            "mixins.stackupup.capacity.late.ae2.json",
            LegacyParityStatus.UNKNOWN,
            "Legacy StackUp patched constructor constants; current AE2 UEL source is version 0.56.7 and cannot establish the old 1.12 constructor/write path or parity of this getter route.",
        ),
        OptionalLegacyMapping(
            "appliedenergistics2",
            "appeng.tile.inventory.AppEngInternalAEInventory",
            "<init>",
            "src/main/java/io/alexjoest/stackupup/mixin/late/ae2/AppEngInternalAEInventoryMixin.java",
            "AppEngInternalAEInventoryMixin",
            "getSlotLimit(I)I",
            "mixins.stackupup.late.ae2.json",
            "mixins.stackupup.capacity.late.ae2.json",
            LegacyParityStatus.UNKNOWN,
            "Legacy StackUp patched constructor constants; current AE2 UEL source is version 0.56.7 and cannot establish the old 1.12 constructor/write path or parity of this getter route.",
        ),
        OptionalLegacyMapping(
            "actuallyadditions",
            "de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase",
            "getMaxStackSize",
            "src/main/java/io/alexjoest/stackupup/mixin/late/actuallyadditions/TileEntityInventoryBaseMixin.java",
            "TileEntityInventoryBaseMixin",
            null,
            "mixins.stackupup.late.actuallyadditions.json",
            "mixins.stackupup.capacity.late.actuallyadditions.json",
            LegacyParityStatus.UNKNOWN,
            "Legacy StackUp selected the method by name only; the Actually Additions target dependency/source is absent, so its descriptor and actual write path are unverified.",
        ),
    )

    @BeforeEach
    fun enableCapacityProfilesForConnectorTests() {
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
    }

    @AfterEach
    fun resetCapacityProfilesForConnectorTests() {
        resetInventoryExpansionForTesting()
    }

    @Test
    fun vanillaTargets_shouldMapToExplicitDescriptorsAndExpectedConfigLanes() {
        for (mapping in vanillaMappings) {
            val source = read(mapping.mixinSource)
            assertTrue(
                source.contains("method = \"${mapping.methodDescriptor}\""),
                "${mapping.legacyPatch} must retain the pinned target descriptor ${mapping.methodDescriptor}",
            )
            mapping.sourceMarkers.forEach { marker ->
                assertTrue(source.contains(marker), "${mapping.legacyPatch} is missing $marker")
            }
            assertFalse(source.contains("@Redirect"), "${mapping.legacyPatch} must not use @Redirect")
            assertFalse(source.contains("import kotlin."), "${mapping.legacyPatch} handler must remain pure Java")
            assertConfigured(mapping.config, mapping.section, mapping.mixinSource.substringAfterLast('/').removeSuffix(".java"))
        }

        assertFalse(configMixins("mixins.stackupup.early.json").contains("ServerRecipeBookHelperMixin"))
        assertFalse(configMixins("mixins.stackupup.baseline.early.json").contains("ServerRecipeBookHelperMixin"))
    }

    @Test
    fun creativeCountCheck_shouldNotRegisterASecondConstantPatchBesideRuleAwareHandler() {
        val source = read("src/main/java/io/alexjoest/stackupup/mixin/early/NetHandlerPlayServerMixin.java")
        assertTrue(source.contains("isValidCreativeStackPacket"))
        assertFalse(source.contains("@ModifyConstant"), "the existing method replacement already owns this count check")
        assertEquals(
            1,
            allConfiguredMixins().count { it.className == "io.alexjoest.stackupup.mixin.early.NetHandlerPlayServerMixin" },
            "creative count validation must have a single configured handler",
        )
    }

    @Test
    fun knownModTargets_shouldRemainUnknownAndCapacityOnlyUntilLegacyDescriptorsAreAvailable() {
        val baselineMixins = configMixins("mixins.stackupup.baseline.early.json")
        for (mapping in optionalLegacyMappings) {
            assertEquals(LegacyParityStatus.UNKNOWN, mapping.parity, mapping.legacyTarget)
            assertTrue(mapping.legacyMethodSelector.isNotBlank(), "legacy selector must be recorded: ${mapping.legacyTarget}")
            assertNull(mapping.legacyDescriptor, "legacy matcher has no evidenced descriptor: ${mapping.legacyTarget}")
            assertTrue(mapping.unknownReason.isNotBlank(), "UNKNOWN reason must be actionable: ${mapping.legacyTarget}")

            val source = read(mapping.currentMixin)
            assertTrue(source.contains("@Pseudo"), "optional target must not be loaded as a required class: ${mapping.currentMixin}")
            if (mapping.currentDescriptor != null) {
                assertTrue(source.contains(mapping.currentDescriptor), "current descriptor changed: ${mapping.currentMixin}")
            }
            assertTrue(configMixins(mapping.capacityConfig).contains(mapping.currentMixinClass))
            assertFalse(configMixins(mapping.ordinaryConfig).contains(mapping.currentMixinClass))
            assertFalse(baselineMixins.contains(mapping.currentMixinClass))
        }
    }

    @Test
    fun optionalKnownTargetConfigs_shouldBeSkippedWhenModIsAbsentAndQueuedOnceWhenPresent() {
        val absentQueue = queueLateConfigurations(emptySet())
        optionalLegacyMappings.map { it.capacityConfig }.distinct().forEach { config ->
            assertFalse(absentQueue.contains(config), "missing optional mod must not queue $config")
        }

        for (mapping in optionalLegacyMappings) {
            val presentQueue = queueLateConfigurations(setOf(mapping.modId))
            assertTrue(presentQueue.contains(mapping.capacityConfig), "${mapping.modId} should queue ${mapping.capacityConfig}")
            assertEquals(presentQueue.size, presentQueue.toSet().size, "configuration must not be registered twice")
        }
    }

    @Test
    fun noDropFixes_shouldRemainOutsideStackUpBaselineAndCapacityProfiles() {
        val baseline = configMixins("mixins.stackupup.baseline.early.json")
        val enderIoNoDrop = "EnderIOInventoryNoDropMixin"
        val nuclearCraftNoDrop = "NuclearCraftDistributorNoDropMixin"

        assertTrue(configMixins("mixins.stackupup.late.enderio.json").contains(enderIoNoDrop))
        assertTrue(configMixins("mixins.stackupup.late.nuclearcraft.json").contains(nuclearCraftNoDrop))
        assertFalse(configMixins("mixins.stackupup.capacity.late.enderio.json").contains(enderIoNoDrop))
        assertFalse(configMixins("mixins.stackupup.capacity.late.nuclearcraft.json").contains(nuclearCraftNoDrop))
        assertFalse(baseline.contains(enderIoNoDrop))
        assertFalse(baseline.contains(nuclearCraftNoDrop))
    }

    @Test
    fun itemStackRulesAndCountProtocol_shouldHaveOneEarlyOwnerOutsideBaselineCapacityLane() {
        val mixins = allConfiguredMixins()
        val ruleMixin = "io.alexjoest.stackupup.mixin.early.ItemStackMixin"
        val nbtMixin = "io.alexjoest.stackupup.mixin.early.ItemStackNbtMixin"

        assertEquals(listOf("mixins.stackupup.early.json"), mixins.filter { it.className == ruleMixin }.map { it.config })
        assertEquals(listOf("mixins.stackupup.early.json"), mixins.filter { it.className == nbtMixin }.map { it.config })
        assertFalse(configMixins("mixins.stackupup.baseline.early.json").any { it == "ItemStackMixin" || it == "ItemStackNbtMixin" })
        assertFalse(configMixins("mixins.stackupup.capacity.early.json").any { it == "ItemStackMixin" || it == "ItemStackNbtMixin" })
    }

    @Test
    fun mixinConfigurations_shouldNotRegisterAnyMixinClassMoreThanOnce() {
        val duplicates = allConfiguredMixins()
            .groupBy { it.className }
            .filterValues { it.size > 1 }

        assertTrue(duplicates.isEmpty(), "Mixin class must have one configuration owner: $duplicates")
    }

    private fun queueLateConfigurations(presentModIds: Set<String>): List<String> {
        val queued = mutableListOf<String>()
        StackUpUpMixinConnector().connectLateWithClassicModules(
            conflicts = emptyList(),
            isModPresent = { it in presentModIds },
            queueConfiguration = { queued += it },
        )
        return queued
    }

    private fun assertConfigured(config: String, section: String, mixin: String) {
        assertTrue(configMixins(config, section).contains(mixin), "$mixin missing from $config:$section")
    }

    private fun configMixins(config: String, section: String = "mixins"): Set<String> =
        extractJsonStringArray(read("src/main/resources/$config"), section).toSet()

    private fun allConfiguredMixins(): List<ConfiguredMixin> {
        val paths: List<Path> = Files.newDirectoryStream(Paths.get("src/main/resources"), "mixins.stackupup*.json").use { it.toList() }
        return paths.flatMap { path ->
            val json = read(path.toString())
            val packageName = requireNotNull(extractJsonString(json, "package")) {
                "Mixin config has no package: $path"
            }
            listOf("mixins", "client", "server").flatMap { section ->
                extractJsonStringArray(json, section).map { mixin ->
                    ConfiguredMixin("$packageName.$mixin", path.fileName.toString(), section)
                }
            }
        }
    }

    private fun extractJsonString(json: String, key: String): String? = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)

    private fun extractJsonStringArray(json: String, key: String): List<String> {
        val body = Regex("\"$key\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            .find(json)
            ?.groupValues
            ?.get(1)
            ?: return emptyList()
        return Regex("\"([^\"]+)\"")
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun read(path: String): String = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
}
