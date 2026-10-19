package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackLimitHooks
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.profiler.Profiler
import net.minecraft.util.ResourceLocation
import net.minecraft.world.World
import net.minecraft.world.WorldProviderSurface
import net.minecraft.world.chunk.IChunkProvider
import net.minecraft.world.storage.WorldInfo
import net.minecraftforge.items.wrapper.EntityArmorInvWrapper
import net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper
import net.minecraftforge.items.wrapper.EntityHandsInvWrapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Hook-adapted subclasses exercise Forge writes; native Mixin installation requires the runtime probe. */
class EquipmentCapacityReceiverTest {
    private var previousMax = 64

    private open class HookedHands(entity: EntityLivingBase) : EntityHandsInvWrapper(entity) {
        override fun getSlotLimit(slot: Int): Int = StackLimitHooks.getEquipmentInventoryStackSize(this, entity, super.getSlotLimit(slot))
    }

    private class ListedHands(entity: EntityLivingBase) : HookedHands(entity)
    private class UnlistedHands(entity: EntityLivingBase) : HookedHands(entity)
    private class ListedEntity : EntityArmorStand(TestWorld())

    private class TestWorld : World(null, WorldInfo(NBTTagCompound()), WorldProviderSurface(), Profiler(), false) {
        override fun createChunkProvider(): IChunkProvider? = null
        override fun isChunkLoaded(x: Int, z: Int, allowEmpty: Boolean): Boolean = false
    }

    @BeforeEach
    fun prepare() {
        Bootstrap.register()
        previousMax = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 256
        resetInventoryExpansionForTesting()
    }

    @AfterEach
    fun restore() {
        StackUpUpConfig.maxStackSize = previousMax
        resetInventoryExpansionForTesting()
    }

    private fun publish(enabled: Boolean, vararg excluded: Class<*>) {
        InventoryStackLimitHook.configureExpansionEnabled(enabled)
        InventoryStackLimitHook.prepare(256, excluded.map { it.name }.toTypedArray())
    }

    private fun stack(count: Int): ItemStack = ItemStack(
        Item().setMaxStackSize(256).setRegistryName(ResourceLocation("stackupup_test", "equipment_receiver")),
        count,
    )

    private fun assertConserved(wrapper: EntityEquipmentInvWrapper, expected: Int) {
        val offered = stack(300)
        val simulated = wrapper.insertItem(0, offered, true)
        assertTrue(wrapper.getStackInSlot(0).isEmpty)
        val remainder = wrapper.insertItem(0, offered, false)
        assertEquals(expected, wrapper.getStackInSlot(0).count)
        assertEquals(300 - expected, remainder.count)
        assertEquals(simulated.count, remainder.count)
        assertEquals(300, wrapper.getStackInSlot(0).count + remainder.count)
        assertEquals(expected, wrapper.extractItem(0, 300, true).count)
        assertEquals(expected, wrapper.getStackInSlot(0).count)
        assertEquals(expected, wrapper.extractItem(0, 300, false).count)
        assertTrue(wrapper.getStackInSlot(0).isEmpty)
    }

    @Test
    fun onPolicy_listedWrapperAndUnlistedWrapperUsingSameVanillaEntityTypeDiffer() {
        publish(true, ListedHands::class.java)
        val listed = ListedHands(EntityArmorStand(TestWorld()))
        val unlisted = UnlistedHands(EntityArmorStand(TestWorld()))
        assertEquals(64, listed.getSlotLimit(0))
        assertEquals(256, unlisted.getSlotLimit(0))
        assertConserved(listed, 64)
        assertConserved(unlisted, 256)
    }

    @Test
    fun offPolicy_unknownWrapperCannotBorrowVanillaEntityEligibility() {
        publish(false)
        val entity = EntityArmorStand(TestWorld())
        val native = EntityHandsInvWrapper(entity)
        assertEquals(256, StackLimitHooks.getEquipmentInventoryStackSize(native, entity, native.getSlotLimit(0)))
        val unknown = UnlistedHands(EntityArmorStand(TestWorld()))
        assertEquals(64, unknown.getSlotLimit(0))
        assertConserved(unknown, 64)
    }

    @Test
    fun onPolicy_listedEntityVetoesNativeAndUnlistedWrappers() {
        publish(true, ListedEntity::class.java)
        val entity = ListedEntity()
        val native = EntityHandsInvWrapper(entity)
        assertEquals(64, StackLimitHooks.getEquipmentInventoryStackSize(native, entity, native.getSlotLimit(0)))
        val unlisted = UnlistedHands(ListedEntity())
        assertEquals(64, unlisted.getSlotLimit(0))
        assertConserved(unlisted, 64)
    }

    @Test
    fun offPolicy_unknownEntityAndNon64CapacitiesRemainOriginal() {
        publish(false)
        val entity = ListedEntity()
        val hands = EntityHandsInvWrapper(entity)
        assertEquals(64, StackLimitHooks.getEquipmentInventoryStackSize(hands, entity, 64))
        assertEquals(16, StackLimitHooks.getEquipmentInventoryStackSize(hands, entity, 16))
        val armor = EntityArmorInvWrapper(EntityArmorStand(TestWorld()))
        assertEquals(1, armor.getSlotLimit(0))
        assertConserved(armor, 1)
    }
}
