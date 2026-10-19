package io.alexjoest.stackupup.dev

import appeng.api.AEApi
import appeng.hooks.TickHandler
import appeng.tile.inventory.AppEngInternalAEInventory
import appeng.tile.inventory.AppEngInternalInventory
import appeng.tile.storage.TileChest
import appeng.util.inv.IAEAppEngInventory
import appeng.util.inv.InvOperation
import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraft.world.chunk.Chunk
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.registry.GameRegistry
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.IItemHandlerModifiable

internal data class DevAe2OwnerCallback(val inventory: IItemHandler, val slot: Int, val operation: InvOperation, val removedCount: Int, val addedCount: Int)

internal class DevAe2OwnerTile :
    TileEntity(),
    IAEAppEngInventory {
    val callbacks = ArrayList<DevAe2OwnerCallback>()
    var saves = 0
        private set

    override fun saveChanges() {
        saves++
        markDirty()
    }

    override fun onChangeInventory(inv: IItemHandler, slot: Int, operation: InvOperation, removed: ItemStack, added: ItemStack) {
        callbacks += DevAe2OwnerCallback(inv, slot, operation, removed.count, added.count)
        markDirty()
    }
}

private fun stackNbt(stack: ItemStack): NBTTagCompound = stack.writeToNBT(NBTTagCompound())

internal class DevAe2OwnerInventoryProbe(private val id: String, private val expansionEnabled: Boolean) {
    private companion object {
        var ownerTileRegistered = false
    }

    private val summaries = ArrayList<String>()
    private val failures = ArrayList<String>()
    private lateinit var server: MinecraftServer
    private lateinit var complete: (DevCompatProbeResult) -> Unit
    private lateinit var ownerA: DevAe2OwnerTile
    private lateinit var ownerB: TileChest
    private lateinit var lifecycleB: TileChest
    private var endTicks = 0
    private var stage = 0
    private var queuedCount = 0
    private var queuedDirty = false
    private var lifecycleTick = -1

    fun start(server: MinecraftServer, complete: (DevCompatProbeResult) -> Unit) {
        this.server = server
        this.complete = complete
        check(StackUpUpConfig.maxStackSize == 256) { "$id requires general.maxStackSize=256" }
        check(StackUpUpConfig.compat.inventoryExpansion.enabled == expansionEnabled) { "$id expansion mismatch" }
        val expectedBlacklist = if (expansionEnabled) listOf(StackUpUpIds.MOD_ID) else emptyList()
        check(StackUpUpConfig.compat.inventoryStackLimitBlacklist.modIds.toList() == expectedBlacklist) { "$id blacklist mismatch" }
        if (!ownerTileRegistered) {
            GameRegistry.registerTileEntity(
                DevAe2OwnerTile::class.java,
                ResourceLocation(StackUpUpIds.MOD_ID, "dev_ae2_owner"),
            )
            ownerTileRegistered = true
        }
        requireAe2ProbeRuntime(Loader.isModLoaded("appliedenergistics2"))
        when (val injection = DevRuleInjector.ensureInjected("item = minecraft:stick -> 128")) {
            is DevRuleInjectionResult.Failed -> error("AE2 rule injection failed: ${injection.errors}")
            else -> Unit
        }
        check(ItemStack(Items.STICK).maxStackSize == 128) { "AE2 stick rule is not active" }
        verifyOwnership()
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun serverTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            endTicks++
            return
        }
        try {
            when (stage) {
                0 -> {
                    val world = server.getWorld(0)
                    ownerA = createOwnerA(world, BlockPos(128, 100, 128))
                    ownerB = createOwnerB(world, BlockPos(256, 100, 128))
                    lifecycleB = createOwnerB(world, BlockPos(384, 100, 128))
                    stage = 1
                }
                1 -> if (endTicks >= 2) {
                    runCases()
                    beginNativeLifecycle()
                    lifecycleTick = endTicks
                    stage = 2
                }
                2 -> if (queuedCount > 0 || endTicks - lifecycleTick >= 20) {
                    scenario("native_B_queued_end_tick") {
                        expect(queuedCount == 1, "queued observer count=$queuedCount expected=1")
                        expect(queuedDirty, "native queued markDirty did not dirty the isolated chunk after immediate dirty was cleared")
                        log("queuedCount=$queuedCount queuedDirty=$queuedDirty endTicks=$endTicks scheduledAt=$lifecycleTick")
                    }
                    finish()
                }
            }
        } catch (throwable: Throwable) {
            fail("tick/setup exception: ${formatProbeThrowable(throwable)}")
            finish()
        }
    }

    private fun verifyOwnership() {
        val mods = Loader.instance().indexedModList
        val own = mods[StackUpUpIds.MOD_ID] ?: error("fixture Mod is not loaded")
        val ae2 = mods["appliedenergistics2"] ?: error("AE2 runtime is not loaded")
        check(ae2.version == "v0.56.7") { "AE2 focus requires v0.56.7, actual=${ae2.version}" }
        val ownPackages = own.ownedPackages.toList()
        val ae2Packages = ae2.ownedPackages.toList()
        check(ownsFixtureClass(ownPackages, DevAe2OwnerTile::class.java.name)) { "Loader does not own fixture A: $ownPackages" }
        for (type in listOf(TileChest::class.java, AppEngInternalInventory::class.java, AppEngInternalAEInventory::class.java)) {
            check(ownsFixtureClass(ae2Packages, type.name)) { "AE2 does not own ${type.name}: $ae2Packages" }
            check(!ownsFixtureClass(ownPackages, type.name)) { "fixture Mod incorrectly owns ${type.name}" }
        }
        log("Loader A=${own.modId} packages=$ownPackages; B/targets=${ae2.modId}@${ae2.version} packages=$ae2Packages; expansion=$expansionEnabled")
    }

    private fun createOwnerA(world: WorldServer, pos: BlockPos): DevAe2OwnerTile {
        check(world.setBlockState(pos, Blocks.CHEST.defaultState, 3)) { "cannot place fixture A block at $pos" }
        val owner = DevAe2OwnerTile()
        world.setTileEntity(pos, owner)
        check(world.getTileEntity(pos) === owner && owner.world === world) { "fixture A is not installed in the real world" }
        return owner
    }

    private fun createOwnerB(world: WorldServer, pos: BlockPos): TileChest {
        val block = AEApi.instance().definitions().blocks().chest().maybeBlock().orElseThrow { IllegalStateException("AE2 chest block is unavailable") }
        check(world.setBlockState(pos, block.defaultState, 3)) { "cannot place native AE2 chest at $pos" }
        val tile = world.getTileEntity(pos) ?: error("AE2 chest placement produced no tile")
        check(tile.javaClass == TileChest::class.java) { "expected exact native TileChest, actual=${tile.javaClass.name}" }
        check(tile.world === world && !tile.isInvalid) { "native B is not a valid world-installed tile" }
        return tile as TileChest
    }

    private fun runCases() {
        for (aeBacked in listOf(false, true)) {
            for ((ownerName, owner) in listOf("A" to ownerA, "B" to ownerB)) {
                val name = "${if (aeBacked) "AEInventory" else "InternalInventory"}/$ownerName"
                val limit = if (expansionEnabled && owner === ownerB) 128 else 64
                scenario("$name/empty_saturation_full") {
                    val target = target(aeBacked, owner)
                    expect(target.slotLimit() == if (limit == 128) 256 else 64, "$name default advertised=${target.slotLimit()}")
                    insert(target, owner, 129, true, limit, 129 - limit)
                    insert(target, owner, 129, false, limit, 129 - limit)
                    insert(target, owner, 1, false, limit, 1)
                    extract(target, owner, 300, true, limit, limit)
                    extract(target, owner, 300, false, 0, limit)
                }
                scenario("$name/native_lt64_occupied") {
                    val target = target(aeBacked, owner)
                    insert(target, owner, 32, false, 32, 0)
                    insert(target, owner, 8, true, 40, 0)
                    insert(target, owner, 8, false, 40, 0)
                }
                scenario("$name/native_lt64_partial") {
                    val target = target(aeBacked, owner)
                    insert(target, owner, 32, false, 32, 0)
                    extract(target, owner, 8, true, 32, 8)
                    extract(target, owner, 8, false, 24, 8)
                }
                scenario("$name/occupied_capacity") {
                    val target = target(aeBacked, owner)
                    insert(target, owner, 32, false, 32, 0)
                    insert(target, owner, 129, true, limit, 129 - (limit - 32))
                    insert(target, owner, 129, false, limit, 129 - (limit - 32))
                }
                scenario("$name/partial_capacity") {
                    val target = target(aeBacked, owner)
                    insert(target, owner, 129, false, limit, 129 - limit)
                    extract(target, owner, 17, true, limit, 17)
                    extract(target, owner, 17, false, limit - 17, 17)
                }
                for (explicit in listOf(1, 16, 256)) {
                    scenario("$name/explicit_$explicit") {
                        val target = target(aeBacked, owner, explicit)
                        val expected = minOf(explicit, 128)
                        expect(target.slotLimit() == explicit, "explicit limit=$explicit advertised=${target.slotLimit()}")
                        insert(target, owner, 129, true, expected, 129 - expected)
                        insert(target, owner, 129, false, expected, 129 - expected)
                        extract(target, owner, 300, false, 0, expected)
                    }
                }
            }
        }
        scenario("InternalInventory/current_owner_B_A_B") { ownerSwitch() }
    }

    private fun ownerSwitch() {
        val inventory = AppEngInternalInventory(ownerB, 1)
        val target = Target(inventory)
        val stored = if (expansionEnabled) 128 else 64
        val advertised = if (expansionEnabled) 256 else 64
        expect(inventory.tileEntity === ownerB && target.slotLimit() == advertised, "initial B identity/limit")
        insert(target, ownerB, 129, false, stored, 129 - stored)
        val beforeSwitch = target.snapshot()
        inventory.setTileEntity(ownerA)
        expect(inventory.tileEntity === ownerA && target.slotLimit() == 64, "current A identity/limit")
        expect(target.count() == stored && target.snapshot().nbt == beforeSwitch.nbt, "owner change truncated existing $stored")
        insert(target, ownerA, 1, false, stored, 1)
        extract(target, ownerA, stored, true, stored, stored)
        extract(target, ownerA, stored, false, 0, stored)
        inventory.setTileEntity(ownerB)
        expect(inventory.tileEntity === ownerB && target.slotLimit() == advertised, "restored B identity/limit")
        insert(target, ownerB, 129, false, stored, 129 - stored)
        log("public owner switch B->$stored/A64/B advertised=$advertised; existing stock retained and true/full extraction checked")
    }

    private fun target(aeBacked: Boolean, owner: IAEAppEngInventory, explicit: Int? = null): Target {
        val handler = if (aeBacked) {
            if (explicit == null) AppEngInternalAEInventory(owner, 1) else AppEngInternalAEInventory(owner, 1, explicit)
        } else {
            if (explicit == null) AppEngInternalInventory(owner, 1) else AppEngInternalInventory(owner, 1, explicit)
        }
        return Target(handler)
    }

    private data class Stock(val count: Int, val backing: Long, val nbt: NBTTagCompound)

    private class Target(val handler: IItemHandlerModifiable) {
        fun count(): Int = handler.getStackInSlot(0).count
        fun slotLimit(): Int = handler.getSlotLimit(0)
        fun snapshot(): Stock {
            val nbt = when (handler) {
                is AppEngInternalInventory -> handler.serializeNBT()
                is AppEngInternalAEInventory -> NBTTagCompound().also { handler.writeToNBT(it, "probe") }
                else -> error("unexpected real AE2 target ${handler.javaClass.name}")
            }
            val backing = if (handler is AppEngInternalAEInventory) handler.getAEStackInSlot(0)?.stackSize ?: 0L else count().toLong()
            return Stock(count(), backing, nbt)
        }
    }

    private fun insert(target: Target, owner: IAEAppEngInventory, offered: Int, simulate: Boolean, expectedStored: Int, expectedRemainder: Int) {
        val input = ItemStack(Items.STICK, offered)
        val inputBefore = stackNbt(input)
        val before = target.snapshot()
        val callbackBefore = ownerA.callbacks.size
        val savesBefore = ownerA.saves
        val chunk = chunk(owner)
        chunk.setModified(false)
        val remainder = target.handler.insertItem(0, input, simulate)
        val after = target.snapshot()
        expect(stackNbt(input) == inputBefore, "insert simulate=$simulate changed input offered=$offered")
        expect(after.count.toLong() == after.backing, "insert stack/backing mismatch: $after")
        expect(remainder.count == expectedRemainder, "insert offered=$offered simulate=$simulate remainder=${remainder.count} expected=$expectedRemainder")
        if (simulate) {
            expect(after == before, "insert simulation changed backing/NBT: before=$before after=$after")
        } else {
            expect(after.count == expectedStored, "insert offered=$offered stored=${after.count} expected=$expectedStored")
            expect(
                after.count - before.count + remainder.count == offered,
                "insert conservation delta=${after.count - before.count} remainder=${remainder.count} offered=$offered",
            )
        }
        val changed = !simulate && expectedRemainder < offered
        verifyCallbacks(owner, target, callbackBefore, savesBefore, changed, InvOperation.INSERT, 0, offered - expectedRemainder)
        expect(chunk.needsSaving(false) == changed, "insert simulate=$simulate native/fixture dirty=${chunk.needsSaving(false)} expected=$changed")
        log(
            "insert simulate=$simulate offered=$offered before=${before.count}/${before.backing} after=${after.count}/${after.backing} remainder=${remainder.count}",
        )
    }

    private fun extract(target: Target, owner: IAEAppEngInventory, amount: Int, simulate: Boolean, expectedStored: Int, expectedOutput: Int) {
        val before = target.snapshot()
        val sourceBefore = target.handler.getStackInSlot(0).copy()
        val callbackBefore = ownerA.callbacks.size
        val savesBefore = ownerA.saves
        val chunk = chunk(owner)
        chunk.setModified(false)
        val output = target.handler.extractItem(0, amount, simulate)
        val after = target.snapshot()
        expect(after.count.toLong() == after.backing, "extract stack/backing mismatch: $after")
        expect(output.count == expectedOutput, "extract amount=$amount simulate=$simulate output=${output.count} expected=$expectedOutput")
        if (expectedOutput > 0) {
            expect(output.isItemEqual(sourceBefore), "extract returned item/meta differs from source: output=$output source=$sourceBefore")
            expect(
                ItemStack.areItemStackTagsEqual(output, sourceBefore),
                "extract returned NBT differs from source: output=$output source=$sourceBefore",
            )
        }
        expect(after.count == expectedStored, "extract amount=$amount stored=${after.count} expected=$expectedStored")
        if (simulate) {
            expect(after == before, "extract simulation changed backing/NBT: before=$before after=$after")
        } else {
            expect(before.count == after.count + output.count, "extract conservation before=${before.count} after=${after.count} output=${output.count}")
        }
        val changed = !simulate && expectedOutput > 0
        verifyCallbacks(owner, target, callbackBefore, savesBefore, changed, InvOperation.EXTRACT, expectedOutput, 0)
        expect(chunk.needsSaving(false) == changed, "extract simulate=$simulate dirty=${chunk.needsSaving(false)} expected=$changed")
        log(
            "extract simulate=$simulate requested=$amount before=${before.count}/${before.backing} after=${after.count}/${after.backing} output=${output.count}",
        )
    }

    private fun verifyCallbacks(
        owner: IAEAppEngInventory,
        target: Target,
        before: Int,
        saves: Int,
        changed: Boolean,
        operation: InvOperation,
        removed: Int,
        added: Int,
    ) {
        if (owner !== ownerA) {
            expect(ownerA.callbacks.size == before && ownerA.saves == saves, "native B operation unexpectedly called fixture A")
            return
        }
        val delta = if (changed) 1 else 0
        expect(
            ownerA.callbacks.size == before + delta && ownerA.saves == saves + delta,
            "A callbacks=${ownerA.callbacks.size - before} saves=${ownerA.saves - saves} expected=$delta",
        )
        if (ownerA.callbacks.size > before) {
            val event = ownerA.callbacks.last()
            expect(event.inventory === target.handler && event.slot == 0 && event.operation == operation, "A callback target/slot/operation mismatch: $event")
            expect(
                event.removedCount == removed && event.addedCount == added,
                "A callback removed/added mismatch: $event expected=$removed/$added",
            )
        }
    }

    private fun chunk(owner: IAEAppEngInventory): Chunk {
        val tile = owner as TileEntity
        return server.getWorld(0).getChunk(tile.pos)
    }

    private fun beginNativeLifecycle() {
        scenario("native_B_immediate_save_and_queue") {
            val chunk = chunk(lifecycleB)
            chunk.setModified(false)
            lifecycleB.saveChanges()
            expect(chunk.needsSaving(false), "native saveChanges did not immediately mark the chunk dirty")
            chunk.setModified(false)
            TickHandler.INSTANCE.addCallable(null) {
                queuedCount++
                queuedDirty = chunk.needsSaving(false)
                null
            }
            log("native world save immediate dirty checked; dirty cleared before actual AE2 end-tick callback and observer")
        }
    }

    private fun scenario(name: String, action: () -> Unit) {
        val before = failures.size
        try {
            action()
        } catch (throwable: Throwable) {
            fail("$name exception: ${formatProbeThrowable(throwable)}")
        }
        log("$name: ${if (failures.size == before) "PASS" else "FAIL"}")
    }

    private fun expect(condition: Boolean, message: String) {
        if (!condition) fail(message)
    }

    private fun fail(message: String) {
        failures += message
        StackUpUp.logger?.error("开发自动验收[focused:{}] FAIL {}", id, message)
    }

    private fun log(message: String) {
        summaries += message
        StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, message)
    }

    private fun finish() {
        stage = 3
        MinecraftForge.EVENT_BUS.unregister(this)
        val summary = (summaries + failures.map { "FAIL $it" }).joinToString(" | ")
        complete(DevCompatProbeResult(failures.isEmpty(), summary))
    }
}
