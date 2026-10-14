package io.alexjoest.stackupup.dev

import com.mojang.authlib.GameProfile
import io.alexjoest.stackupup.StackLimitHooks
import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.passive.EntityPig
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.Items
import net.minecraft.inventory.Container
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.world.World
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.items.wrapper.EntityArmorInvWrapper
import net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper
import net.minecraftforge.items.wrapper.EntityHandsInvWrapper

internal class DevEquipmentHandsFixture(entity: EntityLivingBase) : EntityHandsInvWrapper(entity)

internal class DevEquipmentEntityFixture(world: World) : EntityPig(world)

private class DevBlacklistedChest : TileEntityChest()

private class DevBlacklistedPlayer(server: MinecraftServer, world: net.minecraft.world.WorldServer) :
    EntityPlayerMP(
        server,
        world,
        GameProfile(java.util.UUID(0L, 5L), "StackUpUpBlacklistedPlayer"),
        net.minecraft.server.management.PlayerInteractionManager(world),
    )

private class ConsumerMergeContainer(inventory: TileEntityChest) : Container() {
    init {
        addSlotToContainer(Slot(inventory, 0, 0, 0))
    }

    override fun canInteractWith(player: net.minecraft.entity.player.EntityPlayer): Boolean = true

    fun merge(stack: ItemStack): Boolean = mergeItemStack(stack, 0, 1, false)
}

internal class DevEquipmentInventoryProbe(
    override val id: String,
    private val expansionEnabled: Boolean,
    private val blacklistEnabled: Boolean = expansionEnabled,
) : DevCompatProbe {
    override fun run(server: MinecraftServer): DevCompatProbeResult {
        check(StackUpUpConfig.maxStackSize == 256) { "$id requires general.maxStackSize=256" }
        check(StackUpUpConfig.compat.inventoryExpansion.enabled == expansionEnabled) { "$id requires inventoryExpansion.enabled=$expansionEnabled" }
        val expectedBlacklist = if (blacklistEnabled) listOf(StackUpUpIds.MOD_ID) else emptyList()
        val actualBlacklist = StackUpUpConfig.compat.inventoryStackLimitBlacklist.modIds.toList()
        check(actualBlacklist == expectedBlacklist) { "$id requires blacklist=$expectedBlacklist, actual=$actualBlacklist" }
        val mod = Loader.instance().indexedModList[StackUpUpIds.MOD_ID] ?: error("fixture mod is not loaded: ${StackUpUpIds.MOD_ID}")
        val packages = mod.ownedPackages.toList()
        check(DevEquipmentHandsFixture::class.java.`package`.name in packages) { "Loader does not own dev wrapper package: $packages" }
        check(DevEquipmentEntityFixture::class.java.`package`.name in packages) { "Loader does not own dev entity package: $packages" }
        check(!ownsFixtureClass(packages, EntityHandsInvWrapper::class.java.name)) { "native Forge wrapper belongs to fixture mod: $packages" }
        check(!ownsFixtureClass(packages, EntityPig::class.java.name)) { "native entity belongs to fixture mod: $packages" }
        val ownership = "fixture_mod=${mod.modId} Loader.ownedPackages=$packages blacklist=$actualBlacklist expansion=$expansionEnabled; " +
            "constructor-only dev subclasses, not third-party compatibility proof"
        StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, ownership)
        when (
            val injection = DevRuleInjector.ensureInjected(
                listOf("item = minecraft:stick -> 128", "item = minecraft:diamond_boots -> 128", "item = minecraft:dye && meta = 4 -> 128"),
            )
        ) {
            is DevRuleInjectionResult.Failed -> error("equipment rule injection failed: ${injection.errors}")
            else -> Unit
        }
        check(ItemStack(Items.STICK).maxStackSize == 128) { "equipment stick rule is not active" }
        check(ItemStack(Items.DIAMOND_BOOTS).maxStackSize == 128) { "equipment boots rule is not active" }
        val summaries = arrayListOf(ownership)
        var failed = false
        fun scenario(name: String, entity: EntityLivingBase, wrapper: EntityEquipmentInvWrapper, expected: Int, slot: EntityEquipmentSlot, stack: ItemStack) {
            val result = runCatching { verifyEquipment(name, entity, wrapper, expected, slot, stack) }
            val summary = "$name: ${result.getOrElse { "FAIL ${formatProbeThrowable(it)}" }}"
            summaries += summary
            if (result.isFailure) {
                failed = true
                StackUpUp.logger?.error("开发自动验收[focused:{}] {}", id, summary)
            } else {
                StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, summary)
            }
        }
        if (expansionEnabled) {
            val result = runCatching {
                DevCoreInventoryProbe.verifyInventoryBasicContainerMerge(
                    expectedGetter = StackUpUpConfig.maxStackSize,
                    expectedStored = 128,
                    expectedRemainder = 1,
                )
            }
            val mode = if (blacklistEnabled) "unrelated_blacklist" else "on"
            val summary = "real_mixin_server_exact_inventory_basic_merge_$mode: ${result.getOrElse { "FAIL ${formatProbeThrowable(it)}" }}"
            summaries += summary
            if (result.isFailure) {
                failed = true
                StackUpUp.logger?.error("开发自动验收[focused:{}] {}", id, summary)
            } else {
                StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, summary)
            }
        }
        val world = server.getWorld(0)
        val native = EntityPig(world)
        scenario("native_wrapper/native_entity", native, EntityHandsInvWrapper(native), 128, EntityEquipmentSlot.MAINHAND, ItemStack(Items.STICK, 129))
        val wrapperOnly = EntityPig(world)
        scenario(
            "dev_wrapper/native_entity",
            wrapperOnly,
            DevEquipmentHandsFixture(wrapperOnly),
            if (expansionEnabled &&
                !blacklistEnabled
            ) {
                128
            } else {
                64
            },
            EntityEquipmentSlot.MAINHAND,
            ItemStack(Items.STICK, 129),
        )
        val entityOnly = DevEquipmentEntityFixture(world)
        scenario(
            "native_wrapper/dev_entity",
            entityOnly,
            EntityHandsInvWrapper(entityOnly),
            if (expansionEnabled &&
                !blacklistEnabled
            ) {
                128
            } else {
                64
            },
            EntityEquipmentSlot.MAINHAND,
            ItemStack(Items.STICK, 129),
        )
        val both = DevEquipmentEntityFixture(world)
        scenario(
            "dev_wrapper/dev_entity",
            both,
            DevEquipmentHandsFixture(both),
            if (expansionEnabled &&
                !blacklistEnabled
            ) {
                128
            } else {
                64
            },
            EntityEquipmentSlot.MAINHAND,
            ItemStack(Items.STICK, 129),
        )
        val nativeArmor = EntityPig(world)
        scenario("native_armor", nativeArmor, EntityArmorInvWrapper(nativeArmor), 1, EntityEquipmentSlot.FEET, ItemStack(Items.DIAMOND_BOOTS, 2))
        val devArmor = DevEquipmentEntityFixture(world)
        scenario("dev_entity_armor", devArmor, EntityArmorInvWrapper(devArmor), 1, EntityEquipmentSlot.FEET, ItemStack(Items.DIAMOND_BOOTS, 2))
        if (blacklistEnabled) {
            val consumerResult = runCatching { verifyBlacklistedBaselineConsumers() }
            val consumerSummary = "baseline_consumers: ${consumerResult.getOrElse { "FAIL ${formatProbeThrowable(it)}" }}"
            summaries += consumerSummary
            if (consumerResult.isFailure) {
                failed = true
                StackUpUp.logger?.error("开发自动验收[focused:{}] {}", id, consumerSummary)
            } else {
                StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, consumerSummary)
            }
            val playerResult = runCatching { verifyBlacklistedPlayerOwner(server) }
            val playerSummary = "inventory_player_owner: ${playerResult.getOrElse { "FAIL ${formatProbeThrowable(it)}" }}"
            summaries += playerSummary
            if (playerResult.isFailure) {
                failed = true
                StackUpUp.logger?.error("开发自动验收[focused:{}] {}", id, playerSummary)
            } else {
                StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, playerSummary)
            }
        } else {
            val consumerSummary = "baseline_consumers: not requested outside blacklist focus"
            summaries += consumerSummary
            StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, consumerSummary)
        }
        val enchantingResult = runCatching { DevCoreInventoryProbe.verifyEnchantingNativeBound(server) }
        val enchantingSummary = "enchanting_native_bound: ${enchantingResult.getOrElse { "FAIL ${formatProbeThrowable(it)}" }}"
        summaries += enchantingSummary
        if (enchantingResult.isFailure) {
            failed = true
            StackUpUp.logger?.error("开发自动验收[focused:{}] {}", id, enchantingSummary)
        } else {
            StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, enchantingSummary)
        }
        return DevCompatProbeResult(!failed, summaries.joinToString(" | "))
    }

    private fun verifyBlacklistedPlayerOwner(server: MinecraftServer): String {
        val world = server.getWorld(0)
        val listed = DevBlacklistedPlayer(server, world)
        val unlisted = EntityPlayerMP(
            server,
            world,
            GameProfile(java.util.UUID(0L, 6L), "StackUpUpUnlistedPlayer"),
            net.minecraft.server.management.PlayerInteractionManager(world),
        )
        check(StackUpUpConfig.maxStackSize == 256) { "player owner probe requires maxStackSize=256" }
        check(StackUpUpConfig.compat.inventoryExpansion.enabled == expansionEnabled) {
            "player owner probe requires inventoryExpansion.enabled=$expansionEnabled"
        }
        check(StackLimitHooks.shouldBypassInventoryStackLimit(listed.inventory)) { "listed player owner was not blacklisted" }
        check(!StackLimitHooks.shouldBypassInventoryStackLimit(unlisted.inventory)) { "unlisted player owner was blacklisted" }
        check(listed.inventory.inventoryStackLimit == 64) { "listed player inventory getter=${listed.inventory.inventoryStackLimit}" }
        val expectedUnlistedGetter = if (expansionEnabled) StackUpUpConfig.maxStackSize else 64
        check(unlisted.inventory.inventoryStackLimit == expectedUnlistedGetter) {
            "unlisted player inventory getter=${unlisted.inventory.inventoryStackLimit} expected=$expectedUnlistedGetter"
        }

        for (slot in 1 until listed.inventory.mainInventory.size) {
            listed.inventory.setInventorySlotContents(slot, ItemStack(net.minecraft.init.Blocks.STONE, 64))
            unlisted.inventory.setInventorySlotContents(slot, ItemStack(net.minecraft.init.Blocks.STONE, 64))
        }
        val listedSource = ItemStack(Items.STICK, 129)
        val unlistedSource = ItemStack(Items.STICK, 129)
        check(!listed.inventory.addItemStackToInventory(listedSource)) { "listed player inventory unexpectedly accepted all source" }
        check(!unlisted.inventory.addItemStackToInventory(unlistedSource)) { "unlisted player inventory unexpectedly accepted all source" }
        val listedStored = listed.inventory.getStackInSlot(0).count
        val unlistedStored = unlisted.inventory.getStackInSlot(0).count
        check(listedStored == 64 && listedSource.count == 65) {
            "listed player stored=$listedStored remainder=${listedSource.count}"
        }
        check(unlistedStored == 128 && unlistedSource.count == 1) {
            "unlisted player stored=$unlistedStored remainder=${unlistedSource.count}"
        }
        check(listedStored + listedSource.count == 129) { "listed player inventory lost items" }
        check(unlistedStored + unlistedSource.count == 129) { "unlisted player inventory lost items" }
        return "listed=$listedStored+${listedSource.count} unlisted=$unlistedStored+${unlistedSource.count} " +
            "listedGetter=${listed.inventory.inventoryStackLimit} unlistedGetter=${unlisted.inventory.inventoryStackLimit}"
    }

    private fun verifyBlacklistedBaselineConsumers(): String {
        val listedExpected = if (blacklistEnabled) 64 else 128
        val listed = DevBlacklistedChest()
        val unlisted = TileEntityChest()
        val directOffered = ItemStack(Items.STICK, 129)
        listed.setInventorySlotContents(0, directOffered)
        val listedDirect = listed.getStackInSlot(0).count
        check(listedDirect == listedExpected) { "listed direct write accepted $listedDirect expected=$listedExpected" }
        val unlistedOffered = ItemStack(Items.STICK, 129)
        unlisted.setInventorySlotContents(0, unlistedOffered)
        val unlistedDirect = unlisted.getStackInSlot(0).count
        check(unlistedDirect == 128) { "unlisted direct write stored $unlistedDirect" }

        listed.setInventorySlotContents(0, ItemStack.EMPTY)
        unlisted.setInventorySlotContents(0, ItemStack.EMPTY)
        val listedContainer = ConsumerMergeContainer(listed)
        val unlistedContainer = ConsumerMergeContainer(unlisted)
        val listedSource = ItemStack(Items.STICK, 129)
        val unlistedSource = ItemStack(Items.STICK, 129)
        listedContainer.merge(listedSource)
        unlistedContainer.merge(unlistedSource)
        check(listed.getStackInSlot(0).count == listedExpected && listedSource.count == 129 - listedExpected) {
            "listed container stored=${listed.getStackInSlot(0).count} remainder=${listedSource.count} expected=$listedExpected"
        }
        check(unlisted.getStackInSlot(0).count == 128 && unlistedSource.count == 1) {
            "unlisted container stored=${unlisted.getStackInSlot(0).count} remainder=${unlistedSource.count}"
        }
        return "listed direct=$listedDirect container=${listed.getStackInSlot(0).count}+${listedSource.count}; " +
            "unlisted direct=$unlistedDirect container=${unlisted.getStackInSlot(0).count}+${unlistedSource.count}"
    }

    private fun verifyEquipment(
        name: String,
        entity: EntityLivingBase,
        wrapper: EntityEquipmentInvWrapper,
        expected: Int,
        equipmentSlot: EntityEquipmentSlot,
        offered: ItemStack,
    ): String {
        check(entityState(entity).all(ItemStack::isEmpty)) { "$name fixture equipment must start empty" }
        val advertised = wrapper.getSlotLimit(0)
        if (expected == 128) {
            check(advertised in 128..256) { "$name native capacity must support128 without exceeding256, advertised=$advertised" }
        } else {
            check(advertised == expected) { "$name advertised=$advertised expected=$expected" }
        }
        val offeredCount = offered.count
        val beforeSimulation = entityState(entity)
        val simulated = wrapper.insertItem(0, offered.copy(), true)
        checkStateUnchanged(entity, beforeSimulation, "$name insert simulate")
        checkCounts("$name simulated", offeredCount, expected, simulated.count, expected)
        val remainder = wrapper.insertItem(0, offered.copy(), false)
        val stored = entity.getItemStackFromSlot(equipmentSlot).count
        check(wrapper.getStackInSlot(0).count == stored) { "$name wrapper/delegate counts differ" }
        check(entityState(entity).sumOf(ItemStack::getCount) == stored) { "$name wrote to another equipment slot" }
        val insertion = checkCounts("$name actual", offeredCount, stored, remainder.count, expected)
        val extra = wrapper.insertItem(0, offered.copy().also { it.count = 1 }, false)
        val afterExtra = entity.getItemStackFromSlot(equipmentSlot).count
        checkCounts("$name saturation", 1, afterExtra - stored, extra.count, 0)
        val beforeExtractionSimulation = entityState(entity)
        val simulatedExtraction = wrapper.extractItem(0, offeredCount, true)
        checkStateUnchanged(entity, beforeExtractionSimulation, "$name extract simulate")
        check(simulatedExtraction.count == stored) { "$name simulated extraction=${simulatedExtraction.count}, stored=$stored" }
        val extracted = wrapper.extractItem(0, offeredCount, false)
        val remaining = entity.getItemStackFromSlot(equipmentSlot).count
        val extraction = checkCounts("$name extract", stored, extracted.count, remaining, expected)
        check(entityState(entity).sumOf(ItemStack::getCount) == remaining) { "$name extraction left items in another equipment slot" }
        return "wrapper=${wrapper.javaClass.name} entity=${entity.javaClass.name} advertised=$advertised target=$stored; " +
            "$insertion; saturationDelta=${afterExtra - stored} saturationRemainder=${extra.count}; $extraction; simulations unchanged"
    }

    private fun entityState(entity: EntityLivingBase): List<ItemStack> = EntityEquipmentSlot.values().map { entity.getItemStackFromSlot(it).copy() }

    private fun checkStateUnchanged(entity: EntityLivingBase, before: List<ItemStack>, operation: String) {
        check(
            before.zip(entityState(entity)).all { (old, current) ->
                ItemStack.areItemStacksEqual(old, current)
            },
        ) { "$operation changed real entity equipment" }
    }
}

internal fun ownsFixtureClass(packages: List<String>, className: String): Boolean = packages.any { className.startsWith("$it.") }
