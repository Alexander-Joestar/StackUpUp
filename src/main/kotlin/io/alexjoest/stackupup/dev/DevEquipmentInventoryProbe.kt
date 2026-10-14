package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.passive.EntityPig
import net.minecraft.init.Items
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.world.World
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.items.wrapper.EntityArmorInvWrapper
import net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper
import net.minecraftforge.items.wrapper.EntityHandsInvWrapper

internal class DevEquipmentHandsFixture(entity: EntityLivingBase) : EntityHandsInvWrapper(entity)

internal class DevEquipmentEntityFixture(world: World) : EntityPig(world)

internal class DevEquipmentInventoryProbe(override val id: String, private val expansionEnabled: Boolean) : DevCompatProbe {
    override fun run(server: MinecraftServer): DevCompatProbeResult {
        check(StackUpUpConfig.maxStackSize == 256) { "$id requires general.maxStackSize=256" }
        check(StackUpUpConfig.compat.inventoryExpansion.enabled == expansionEnabled) { "$id requires inventoryExpansion.enabled=$expansionEnabled" }
        val expectedBlacklist = if (expansionEnabled) listOf(StackUpUpIds.MOD_ID) else emptyList()
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
        when (val injection = DevRuleInjector.ensureInjected(listOf("item = minecraft:stick -> 128", "item = minecraft:diamond_boots -> 128"))) {
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
        val world = server.getWorld(0)
        val native = EntityPig(world)
        scenario("native_wrapper/native_entity", native, EntityHandsInvWrapper(native), 128, EntityEquipmentSlot.MAINHAND, ItemStack(Items.STICK, 129))
        val wrapperOnly = EntityPig(world)
        scenario("dev_wrapper/native_entity", wrapperOnly, DevEquipmentHandsFixture(wrapperOnly), 64, EntityEquipmentSlot.MAINHAND, ItemStack(Items.STICK, 129))
        val entityOnly = DevEquipmentEntityFixture(world)
        scenario("native_wrapper/dev_entity", entityOnly, EntityHandsInvWrapper(entityOnly), 64, EntityEquipmentSlot.MAINHAND, ItemStack(Items.STICK, 129))
        val both = DevEquipmentEntityFixture(world)
        scenario("dev_wrapper/dev_entity", both, DevEquipmentHandsFixture(both), 64, EntityEquipmentSlot.MAINHAND, ItemStack(Items.STICK, 129))
        val nativeArmor = EntityPig(world)
        scenario("native_armor", nativeArmor, EntityArmorInvWrapper(nativeArmor), 1, EntityEquipmentSlot.FEET, ItemStack(Items.DIAMOND_BOOTS, 2))
        val devArmor = DevEquipmentEntityFixture(world)
        scenario("dev_entity_armor", devArmor, EntityArmorInvWrapper(devArmor), 1, EntityEquipmentSlot.FEET, ItemStack(Items.DIAMOND_BOOTS, 2))
        return DevCompatProbeResult(!failed, summaries.joinToString(" | "))
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
