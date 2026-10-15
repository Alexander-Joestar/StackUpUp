package io.alexjoest.stackupup.dev

import com.mojang.authlib.GameProfile
import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.StackUpUpConfig
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.Items
import net.minecraft.inventory.Container
import net.minecraft.inventory.ContainerChest
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.InventoryLargeChest
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.management.PlayerInteractionManager
import net.minecraft.tileentity.TileEntity
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.ITickable
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraftforge.energy.CapabilityEnergy
import net.minecraftforge.energy.IEnergyStorage
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.wrapper.InvWrapper
import net.minecraftforge.oredict.OreDictionary
import java.io.File
import java.util.UUID

internal const val TARGETED_CONTAINER_FOCUS = "targeted_container_probe"

internal const val VANILLA_CHEST_CLASS = "net.minecraft.tileentity.TileEntityChest"
internal const val VANILLA_LARGE_CHEST_CLASS = "net.minecraft.inventory.InventoryLargeChest"
internal const val NUCLEARCRAFT_ALLOY_FURNACE_CLASS = "nc.tile.processor.TileProcessorImpl\$TileAlloyFurnace"

internal const val TARGETED_CONTAINER_MACHINE_FILE = "targeted-container-probe.jsonl"
internal const val TARGETED_CONTAINER_HUMAN_FILE = "targeted-container-probe.txt"

private const val UNKNOWN = "UNKNOWN"
private const val PASS = "PASS"
private const val FAIL = "FAIL"
private const val SERVER_STORAGE_LOSS = "SERVER_STORAGE_LOSS"
private const val SERVER_STORAGE_OK = "SERVER_STORAGE_OK"
private const val LEGITIMATE_REMAINDER = "LEGITIMATE_REMAINDER"

internal data class DevTargetedContainerSelector(val className: String?, val modId: String?, val capacityMode: String? = null) {
    init {
        require(className != null || modId != null) { "targeted_selector_empty" }
        require(className == null || className.isNotBlank()) { "targeted_selector_class_empty" }
        require(modId == null || modId.matches(MOD_ID_PATTERN)) { "targeted_selector_modid_invalid: $modId" }
        require(capacityMode == null || capacityMode in setOf("on", "off")) {
            "targeted_selector_capacity_invalid: $capacityMode"
        }
    }

    override fun toString(): String = buildString {
        className?.let { append("class=").append(it) }
        modId?.let {
            if (isNotEmpty()) append(';')
            append("modid=").append(it)
        }
        capacityMode?.let {
            if (isNotEmpty()) append(';')
            append("capacity=").append(it)
        }
    }

    companion object {
        private val MOD_ID_PATTERN = Regex("[a-z0-9_.-]+")

        fun parse(raw: String): DevTargetedContainerSelector {
            val value = raw.trim()
            require(value.isNotEmpty()) { "targeted_selector_empty" }
            val classOnly = value.removePrefix("class:").takeIf { it != value }
            if (classOnly != null) {
                require(classOnly.isNotBlank()) { "targeted_selector_class_empty" }
                return DevTargetedContainerSelector(classOnly, null)
            }
            val modOnly = value.removePrefix("modid:").takeIf { it != value }
            if (modOnly != null) {
                require(modOnly.isNotBlank()) { "targeted_selector_modid_empty" }
                return DevTargetedContainerSelector(null, modOnly.lowercase())
            }

            var className: String? = null
            var modId: String? = null
            var capacityMode: String? = null
            for (part in value.split(';', ',')) {
                val token = part.trim()
                require(token.isNotEmpty()) { "targeted_selector_empty_token" }
                val separator = token.indexOf('=')
                require(separator > 0 && separator < token.lastIndex) { "targeted_selector_token_invalid: $token" }
                val key = token.substring(0, separator).trim().lowercase()
                val content = token.substring(separator + 1).trim()
                require(content.isNotEmpty()) { "targeted_selector_value_empty: $key" }
                when (key) {
                    "class", "classname" -> {
                        require(className == null) { "targeted_selector_duplicate_class" }
                        className = content
                    }

                    "modid", "mod" -> {
                        require(modId == null) { "targeted_selector_duplicate_modid" }
                        modId = content.lowercase()
                    }

                    "capacity", "mixin" -> {
                        require(capacityMode == null) { "targeted_selector_duplicate_capacity" }
                        capacityMode = content.lowercase()
                    }

                    else -> error("targeted_selector_key_unknown: $key")
                }
            }
            return DevTargetedContainerSelector(className, modId, capacityMode)
        }
    }
}

internal data class DevTargetedContainerDefinition(val id: String, val className: String, val modId: String)

internal val targetedContainerDefinitions: List<DevTargetedContainerDefinition> = listOf(
    DevTargetedContainerDefinition("vanilla_chest", VANILLA_CHEST_CLASS, "minecraft"),
    DevTargetedContainerDefinition("vanilla_large_chest", VANILLA_LARGE_CHEST_CLASS, "minecraft"),
    DevTargetedContainerDefinition("nuclearcraft_alloy_furnace", NUCLEARCRAFT_ALLOY_FURNACE_CLASS, "nuclearcraft"),
)

internal fun resolveTargetedContainerDefinition(selector: DevTargetedContainerSelector): DevTargetedContainerDefinition {
    val matches = targetedContainerDefinitions.filter { definition ->
        (selector.className == null || selector.className == definition.className) &&
            (selector.modId == null || selector.modId == definition.modId)
    }
    return when (matches.size) {
        1 -> matches.single()
        0 -> error("unknown_targeted_selector: $selector")
        else -> error("ambiguous_targeted_selector: $selector matches=${matches.map { it.id }}")
    }
}

internal fun targetedSelectorMatches(selector: DevTargetedContainerSelector, definition: DevTargetedContainerDefinition): Boolean =
    (selector.className == null || selector.className == definition.className) &&
        (selector.modId == null || selector.modId == definition.modId)

internal data class DevTargetedContainerRecord(
    val selector: String,
    val targetId: String,
    val targetClass: String,
    val targetModId: String,
    val operation: String,
    val slot: Int?,
    val offeredCount: Int?,
    val preStackCount: Int?,
    val postStackCount: Int?,
    val actualStoredDelta: Int? = null,
    val predictedAcceptedCount: Int? = null,
    val inventoryLimit: Int?,
    val slotLimit: Int?,
    val itemLimit: Int?,
    val setterResult: String?,
    val insertResult: String?,
    val remainderCount: Int?,
    val remainderSource: String,
    val conservationDelta: Int?,
    val serverStorageStatus: String,
    val clientSyncDisplayStatus: String,
    val classification: String,
    val status: String,
    val offeredIdentity: String? = null,
    val preIdentity: String? = null,
    val postIdentity: String? = null,
    val remainderIdentity: String? = null,
    val resultIdentity: String? = null,
    val operationId: String = "",
    val sourceSlot: Int? = null,
    val destinationSlot: Int? = null,
    val logicalSide: String = "UNKNOWN",
    val capacityMixinRequested: String = "UNKNOWN",
    val capacityMixinStatus: String = "UNKNOWN",
    val mixinCoverage: String = "UNKNOWN",
    val detail: String? = null,
) {
    fun withMixinState(state: DevTargetedContainerMixinState): DevTargetedContainerRecord = copy(
        capacityMixinRequested = state.requested,
        capacityMixinStatus = state.status,
        mixinCoverage = state.coverage,
    )

    fun toMachineJson(): String = buildString {
        append('{')
        appendJsonField("selector", selector)
        append(',')
        appendJsonField("targetId", targetId)
        append(',')
        appendJsonField("targetClass", targetClass)
        append(',')
        appendJsonField("targetModId", targetModId)
        append(',')
        appendJsonField("operation", operation)
        append(',')
        appendJsonNumber("slot", slot)
        append(',')
        appendJsonNumber("offeredCount", offeredCount)
        append(',')
        appendJsonNumber("preStackCount", preStackCount)
        append(',')
        appendJsonNumber("postStackCount", postStackCount)
        append(',')
        appendJsonNumber("actualStoredDelta", actualStoredDelta)
        append(',')
        appendJsonNumber("predictedAcceptedCount", predictedAcceptedCount)
        append(',')
        appendJsonNumber("inventoryLimit", inventoryLimit)
        append(',')
        appendJsonNumber("slotLimit", slotLimit)
        append(',')
        appendJsonNumber("itemLimit", itemLimit)
        append(',')
        appendJsonField("setterResult", setterResult)
        append(',')
        appendJsonField("insertResult", insertResult)
        append(',')
        appendJsonNumber("remainderCount", remainderCount)
        append(',')
        appendJsonField("remainderSource", remainderSource)
        append(',')
        appendJsonNumber("conservationDelta", conservationDelta)
        append(',')
        appendJsonField("serverStorageStatus", serverStorageStatus)
        append(',')
        appendJsonField("clientSyncDisplayStatus", clientSyncDisplayStatus)
        append(',')
        appendJsonField("classification", classification)
        append(',')
        appendJsonField("status", status)
        append(',')
        appendJsonField("offeredIdentity", offeredIdentity)
        append(',')
        appendJsonField("preIdentity", preIdentity)
        append(',')
        appendJsonField("postIdentity", postIdentity)
        append(',')
        appendJsonField("remainderIdentity", remainderIdentity)
        append(',')
        appendJsonField("resultIdentity", resultIdentity)
        append(',')
        appendJsonField("operationId", operationId)
        append(',')
        appendJsonNumber("sourceSlot", sourceSlot)
        append(',')
        appendJsonNumber("destinationSlot", destinationSlot)
        append(',')
        appendJsonField("logicalSide", logicalSide)
        append(',')
        appendJsonField("capacityMixinRequested", capacityMixinRequested)
        append(',')
        appendJsonField("capacityMixinStatus", capacityMixinStatus)
        append(',')
        appendJsonField("mixinCoverage", mixinCoverage)
        append(',')
        appendJsonField("detail", detail)
        append('}')
    }
}

internal fun renderTargetedContainerRecord(record: DevTargetedContainerRecord): String = buildString {
    append("targeted-container")
    append(" selector=").append(record.selector)
    append(" target=").append(record.targetId).append('@').append(record.targetClass)
    append(" modid=").append(record.targetModId)
    append(" operation=").append(record.operation)
    append(" slot=").append(record.slot ?: UNKNOWN)
    append(" offered=").append(record.offeredCount ?: UNKNOWN)
    append(" pre=").append(record.preStackCount ?: UNKNOWN)
    append(" post=").append(record.postStackCount ?: UNKNOWN)
    append(" actualStoredDelta=").append(record.actualStoredDelta ?: UNKNOWN)
    append(" predictedAccepted=").append(record.predictedAcceptedCount ?: UNKNOWN)
    append(" inventoryLimit=").append(record.inventoryLimit ?: UNKNOWN)
    append(" slotLimit=").append(record.slotLimit ?: UNKNOWN)
    append(" itemLimit=").append(record.itemLimit ?: UNKNOWN)
    append(" setterResult=").append(record.setterResult ?: UNKNOWN)
    append(" insertResult=").append(record.insertResult ?: UNKNOWN)
    append(" remainder=").append(record.remainderCount ?: UNKNOWN)
    append(" remainderSource=").append(record.remainderSource)
    append(" conservationDelta=").append(record.conservationDelta ?: UNKNOWN)
    append(" serverStorage=").append(record.serverStorageStatus)
    append(" clientSyncDisplay=").append(record.clientSyncDisplayStatus)
    append(" classification=").append(record.classification)
    append(" status=").append(record.status)
    append(" offeredIdentity=").append(record.offeredIdentity ?: UNKNOWN)
    append(" preIdentity=").append(record.preIdentity ?: UNKNOWN)
    append(" postIdentity=").append(record.postIdentity ?: UNKNOWN)
    append(" remainderIdentity=").append(record.remainderIdentity ?: UNKNOWN)
    append(" resultIdentity=").append(record.resultIdentity ?: UNKNOWN)
    append(" operationId=").append(record.operationId.ifEmpty { UNKNOWN })
    append(" sourceSlot=").append(record.sourceSlot ?: UNKNOWN)
    append(" destinationSlot=").append(record.destinationSlot ?: UNKNOWN)
    append(" logicalSide=").append(record.logicalSide)
    append(" capacityMixinRequested=").append(record.capacityMixinRequested)
    append(" capacityMixinStatus=").append(record.capacityMixinStatus)
    append(" mixinCoverage=").append(record.mixinCoverage)
    record.detail?.let { append(" detail=").append(it) }
}

internal data class DevTargetedContainerMixinState(val requested: String, val status: String, val coverage: String)

internal data class DevSimulationEvidence(val actualStoredDelta: Int, val predictedAcceptedCount: Int, val conservationDelta: Int?)

internal fun evaluateSimulationEvidence(offeredCount: Int, remainderCount: Int, preStackCount: Int, postStackCount: Int): DevSimulationEvidence {
    require(offeredCount >= 0) { "simulation offered count must be non-negative" }
    require(remainderCount in 0..offeredCount) { "simulation remainder outside offered range" }
    return DevSimulationEvidence(
        actualStoredDelta = postStackCount - preStackCount,
        predictedAcceptedCount = offeredCount - remainderCount,
        conservationDelta = null,
    )
}

internal fun renderTargetedContainerMachineEvidence(records: List<DevTargetedContainerRecord>): String = buildString {
    records.forEach { append(it.toMachineJson()).append('\n') }
}

internal fun renderTargetedContainerHumanEvidence(records: List<DevTargetedContainerRecord>): String = buildString {
    records.forEach { append(renderTargetedContainerRecord(it)).append('\n') }
}

internal fun targetedStackIdentity(stack: ItemStack?): String? {
    if (stack == null) return null
    if (stack.isEmpty) return "EMPTY"
    val itemId = stack.item.registryName?.toString() ?: stack.item.javaClass.name
    val tag = if (stack.hasTagCompound()) stack.tagCompound.toString() else "-"
    return "$itemId@${stack.metadata}#$tag"
}

internal fun capacityMixinState(selector: DevTargetedContainerSelector, definition: DevTargetedContainerDefinition): DevTargetedContainerMixinState {
    val requested = selector.capacityMode ?: UNKNOWN
    if (definition.modId != "nuclearcraft") {
        return DevTargetedContainerMixinState(requested, UNKNOWN, "not_applicable_non_nuclearcraft")
    }
    val enabled = StackUpUpConfig.compat.inventoryExpansion.enabled
    val status = when (selector.capacityMode) {
        "on" -> if (enabled) "ENABLED_BY_CONFIG" else "CONFIG_MISMATCH_OFF"
        "off" -> if (!enabled) "DISABLED_BY_CONFIG" else "CONFIG_MISMATCH_ON"
        null -> "UNKNOWN_REQUEST"
        else -> "UNKNOWN_REQUEST"
    }
    return DevTargetedContainerMixinState(
        requested,
        status,
        "capacity-late.nuclearcraft.json:NuclearCraftTileInventoryLimitMixin;ordinary-late=separate",
    )
}

internal object DevTargetedContainerProbe : DevCompatProbe {
    override val id: String = TARGETED_CONTAINER_FOCUS

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val selector = DevTargetedContainerSelector.parse(DevAutomationConfig.targetedContainerSelector)
        val definition = resolveTargetedContainerDefinition(selector)
        val mixinState = capacityMixinState(selector, definition)
        val records = ArrayList<DevTargetedContainerRecord>()
        if (!targetAvailable(definition)) {
            records += unknownTargetRecord(selector, definition, "target class or required mod is unavailable")
            return finish(selector, definition, records.map { it.withMixinState(mixinState) })
        }

        records += runOperation(definition, selector, "setter", server) { target, inventory, handler, slot ->
            setterRecord(selector, definition, target, inventory, handler, slot)
        }
        records += runOperation(definition, selector, "ordinary_insert_simulate", server) { target, inventory, handler, slot ->
            insertRecord(selector, definition, target, inventory, handler, slot, simulate = true)
        }
        records += runOperation(definition, selector, "ordinary_insert", server) { target, inventory, handler, slot ->
            insertRecord(selector, definition, target, inventory, handler, slot, simulate = false)
        }
        records += runOperation(definition, selector, "merge", server) { target, inventory, handler, slot ->
            mergeRecord(selector, definition, target, inventory, handler, slot)
        }
        records += runOperation(definition, selector, "shift_transfer", server) { target, inventory, handler, slot ->
            shiftTransferRecord(selector, definition, target, inventory, handler, slot, server)
        }
        records += runOperation(definition, selector, "output", server) { target, inventory, handler, slot ->
            outputRecord(selector, definition, target, inventory, handler, slot)
        }
        if (definition.id == "nuclearcraft_alloy_furnace") {
            records += runAlloyRecipePath(selector, definition, server)
        }
        return finish(selector, definition, records.map { it.withMixinState(mixinState) })
    }

    private fun runAlloyRecipePath(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        server: MinecraftServer,
    ): List<DevTargetedContainerRecord> {
        val world = server.getWorld(0)
        val worldName = runCatching { world.saveHandler.worldDirectory.name }.getOrDefault("")
        if (!worldName.startsWith("stackupup_dev_")) {
            return listOf(
                unknownRecord(
                    selector,
                    definition,
                    "recipe_conversion",
                    null,
                    "recipe fixture refused outside isolated automation world: world=$worldName",
                ),
            )
        }

        val copper = OreDictionary.getOres("ingotCopper").firstOrNull()?.copy()
        val tin = OreDictionary.getOres("ingotTin").firstOrNull()?.copy()
        val outputItem = ForgeRegistries.ITEMS.getValue(ResourceLocation("nuclearcraft", "alloy"))
        if (copper == null || tin == null || outputItem == null) {
            return listOf(
                unknownRecord(
                    selector,
                    definition,
                    "recipe_conversion",
                    null,
                    "known NC 2o.9.7 recipe unavailable: ingotCopper x3 + ingotTin x1 -> nuclearcraft:alloy@0 x4; " +
                        "oreCopper=${copper != null} oreTin=${tin != null} outputItem=${outputItem != null}",
                ),
            )
        }

        copper.count = 3
        tin.count = 1
        val expectedOutput = ItemStack(outputItem, 4, 0)
        val block = ForgeRegistries.BLOCKS.getValue(ResourceLocation("nuclearcraft", "alloy_furnace"))
            ?: return listOf(unknownRecord(selector, definition, "recipe_conversion", null, "registered NC alloy_furnace block unavailable"))
        val position = BlockPos(32, 200, 32)
        val previousState = world.getBlockState(position)
        val previousTile = world.getTileEntity(position)
        var records: List<DevTargetedContainerRecord> = emptyList()
        try {
            check(world.setBlockState(position, block.defaultState, 3)) { "cannot place isolated NC alloy furnace fixture at $position" }
            val tile = world.getTileEntity(position) ?: error("registered NC block produced no tile at $position")
            check(tile.javaClass.name == definition.className) {
                "registered NC block produced ${tile.javaClass.name}, expected ${definition.className}"
            }
            val inventory = tile as? IInventory ?: error("NC alloy furnace tile does not expose IInventory")
            check(inventory.sizeInventory >= 3) { "NC alloy furnace inventory size=${inventory.sizeInventory}, expected at least 3" }
            inventory.setInventorySlotContents(0, copper.copy())
            inventory.setInventorySlotContents(1, tin.copy())
            inventory.setInventorySlotContents(2, ItemStack.EMPTY)

            val energy: IEnergyStorage? = tile.getCapability(CapabilityEnergy.ENERGY, null)
            val acceptedEnergy = energy?.receiveEnergy(1_000_000, false) ?: 0
            val tickable = tile as? ITickable
            check(energy != null && acceptedEnergy > 0) { "NC alloy furnace Forge Energy capability unavailable or rejected energy" }
            check(tickable != null) { "NC alloy furnace does not expose public ITickable" }
            var completed = false
            repeat(400) {
                tickable.update()
                val output = inventory.getStackInSlot(2)
                if (output.isItemEqual(expectedOutput) &&
                    output.count >= expectedOutput.count &&
                    inventory.getStackInSlot(0).isEmpty &&
                    inventory.getStackInSlot(1).isEmpty
                ) {
                    completed = true
                    return@repeat
                }
            }
            val actualInputs = listOf(inventory.getStackInSlot(0).copy(), inventory.getStackInSlot(1).copy())
            val actualOutput = inventory.getStackInSlot(2).copy()
            records = alloyRecipeRecords(
                selector = selector,
                definition = definition,
                inventory = inventory,
                inputStacks = listOf(copper, tin),
                actualInputs = actualInputs,
                expectedOutput = expectedOutput,
                actualOutput = actualOutput,
                completed = completed,
                energyAccepted = acceptedEnergy,
            )
        } catch (throwable: Throwable) {
            records = listOf(
                unknownRecord(
                    selector,
                    definition,
                    "recipe_conversion",
                    null,
                    "recipe fixture unavailable: ${formatProbeThrowable(throwable)}; " +
                        "recipe=ingotCopper@${copper.metadata} x3 + ingotTin@${tin.metadata} x1 -> " +
                        "nuclearcraft:alloy@0 x4",
                ),
            )
        } finally {
            world.removeTileEntity(position)
            world.setBlockState(position, previousState, 3)
            if (previousTile != null) world.setTileEntity(position, previousTile)
        }
        return records
    }

    private fun alloyRecipeRecords(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        inventory: IInventory,
        inputStacks: List<ItemStack>,
        actualInputs: List<ItemStack>,
        expectedOutput: ItemStack,
        actualOutput: ItemStack,
        completed: Boolean,
        energyAccepted: Int,
    ): List<DevTargetedContainerRecord> {
        val recipeDetail = "recipe=ingotCopper x3 + ingotTin x1 -> nuclearcraft:alloy@0 x4 " +
            "energyAccepted=$energyAccepted completed=$completed; identities are item/meta/tag, not count-only"
        val status = if (completed) PASS else UNKNOWN
        val storage = if (completed) PASS else UNKNOWN
        val classification = if (completed) SERVER_STORAGE_OK else UNKNOWN
        val inputRecords = inputStacks.mapIndexed { index, offered ->
            val actual = actualInputs[index]
            DevTargetedContainerRecord(
                selector = selector.toString(),
                targetId = definition.id,
                targetClass = definition.className,
                targetModId = definition.modId,
                operation = "recipe_input",
                slot = index,
                offeredCount = offered.count,
                preStackCount = 0,
                postStackCount = actual.count,
                inventoryLimit = inventory.inventoryStackLimit,
                slotLimit = Slot(inventory, index, 0, 0).slotStackLimit,
                itemLimit = offered.maxStackSize,
                setterResult = "recipe_input_setter_void",
                insertResult = null,
                remainderCount = null,
                remainderSource = "NOT_APPLICABLE_RECIPE_INPUT",
                conservationDelta = if (completed) -actual.count else null,
                serverStorageStatus = storage,
                clientSyncDisplayStatus = UNKNOWN,
                classification = classification,
                status = status,
                offeredIdentity = targetedStackIdentity(offered),
                preIdentity = "EMPTY",
                postIdentity = targetedStackIdentity(actual),
                remainderIdentity = if (completed) targetedStackIdentity(actual) else null,
                operationId = "${definition.id}/recipe_input/slot$index",
                destinationSlot = index,
                logicalSide = "SERVER_STORAGE",
                detail = recipeDetail,
            )
        }
        val outputDelta = if (completed) actualOutput.count - expectedOutput.count else null
        val outputRecord = DevTargetedContainerRecord(
            selector = selector.toString(),
            targetId = definition.id,
            targetClass = definition.className,
            targetModId = definition.modId,
            operation = "recipe_output",
            slot = 2,
            offeredCount = 0,
            preStackCount = 0,
            postStackCount = actualOutput.count,
            inventoryLimit = inventory.inventoryStackLimit,
            slotLimit = Slot(inventory, 2, 0, 0).slotStackLimit,
            itemLimit = expectedOutput.maxStackSize,
            setterResult = null,
            insertResult = "expected_output_count=${expectedOutput.count};actual_output_count=${actualOutput.count}",
            remainderCount = null,
            remainderSource = "NOT_APPLICABLE_RECIPE_OUTPUT",
            conservationDelta = outputDelta,
            serverStorageStatus = storage,
            clientSyncDisplayStatus = UNKNOWN,
            classification = classification,
            status = status,
            postIdentity = targetedStackIdentity(actualOutput),
            resultIdentity = targetedStackIdentity(expectedOutput),
            operationId = "${definition.id}/recipe_output/slot2",
            sourceSlot = 2,
            logicalSide = "SERVER_STORAGE",
            detail = recipeDetail,
        )
        return inputRecords + outputRecord
    }

    private fun targetAvailable(definition: DevTargetedContainerDefinition): Boolean {
        if (definition.modId == "minecraft") return true
        if (!Loader.isModLoaded(definition.modId)) return false
        return ForgeRegistries.BLOCKS.getValue(ResourceLocation(definition.modId, "alloy_furnace")) != null
    }

    private fun finish(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        records: List<DevTargetedContainerRecord>,
    ): DevCompatProbeResult {
        writeEvidence(records)
        records.forEach { record ->
            if (record.status == FAIL) {
                StackUpUp.logger?.error("开发自动验收[focused:{}] {}", id, renderTargetedContainerRecord(record))
            } else {
                StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, renderTargetedContainerRecord(record))
            }
        }
        val failures = records.count { it.status == FAIL }
        val unknown = records.count { it.status == UNKNOWN }
        val summary = "selector=$selector target=${definition.id} records=${records.size} failures=$failures unknown=$unknown " +
            "machine=logs/$TARGETED_CONTAINER_MACHINE_FILE human=logs/$TARGETED_CONTAINER_HUMAN_FILE " +
            "clientSyncDisplay=UNKNOWN"
        return DevCompatProbeResult(failures == 0, summary)
    }

    private fun writeEvidence(records: List<DevTargetedContainerRecord>) {
        val machine = File("logs", TARGETED_CONTAINER_MACHINE_FILE)
        val human = File("logs", TARGETED_CONTAINER_HUMAN_FILE)
        machine.parentFile?.mkdirs()
        machine.writeText(renderTargetedContainerMachineEvidence(records), Charsets.UTF_8)
        human.writeText(renderTargetedContainerHumanEvidence(records), Charsets.UTF_8)
    }

    private fun runOperation(
        definition: DevTargetedContainerDefinition,
        selector: DevTargetedContainerSelector,
        operation: String,
        server: MinecraftServer,
        action: (Any, IInventory?, IItemHandler?, Int) -> DevTargetedContainerRecord,
    ): List<DevTargetedContainerRecord> {
        val initial = runCatching { newTarget(definition, server) }
        if (initial.isFailure) {
            return listOf(
                unknownTargetRecord(selector, definition, "$operation target construction failed: ${formatProbeThrowable(initial.exceptionOrNull()!!)}"),
            )
        }
        val target = initial.getOrThrow()
        val inventory = target as? IInventory
        val handler = itemHandler(target, inventory)
        val size = inventory?.sizeInventory ?: handler?.slots ?: 0
        if (size <= 0) {
            return listOf(unknownTargetRecord(selector, definition, "$operation has no discoverable slots"))
        }

        return (0 until size).map { slot ->
            runCatching { action(target, inventory, handler, slot) }
                .getOrElse { throwable ->
                    val prefix = if (operation == "shift_transfer" && definition.modId == "minecraft") {
                        "real ContainerChest setup failed: "
                    } else {
                        ""
                    }
                    unknownRecord(
                        selector,
                        definition,
                        operation,
                        slot,
                        "$operation unavailable: $prefix${formatProbeThrowable(throwable)}",
                    )
                }
        }
    }

    private fun newTarget(definition: DevTargetedContainerDefinition, server: MinecraftServer): Any = when (definition.id) {
        "vanilla_chest" -> TileEntityChest()
        "vanilla_large_chest" -> InventoryLargeChest("StackUpUp targeted probe", TileEntityChest(), TileEntityChest())
        "nuclearcraft_alloy_furnace" -> {
            val block = ForgeRegistries.BLOCKS.getValue(ResourceLocation(definition.modId, "alloy_furnace"))
                ?: error("registered block missing: ${definition.modId}:alloy_furnace")
            val tile = block.createTileEntity(server.getWorld(0), block.defaultState)
                ?: error("registered block did not create a tile: ${definition.modId}:alloy_furnace")
            tile.setWorld(server.getWorld(0))
            tile.setPos(BlockPos(33, 200, 33))
            tile
        }

        else -> error("unknown_targeted_definition: ${definition.id}")
    }.also { target ->
        check(target.javaClass.name == definition.className) {
            "target class mismatch: actual=${target.javaClass.name} expected=${definition.className}"
        }
    }

    private fun itemHandler(target: Any, inventory: IInventory?): IItemHandler? {
        if (target is IItemHandler) return target
        if (target is TileEntity) {
            val capability = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
            if (capability != null) return capability
        }
        return inventory?.let(::InvWrapper)
    }

    private fun setterRecord(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        target: Any,
        inventory: IInventory?,
        handler: IItemHandler?,
        slot: Int,
    ): DevTargetedContainerRecord {
        if (inventory == null) return unknownRecord(selector, definition, "setter", slot, "target does not implement IInventory")
        val offeredCount = 129
        val offered = stack(offeredCount)
        val beforeStack = inventory.getStackInSlot(slot).copy()
        val before = beforeStack.count
        val inventoryLimit = inventory.inventoryStackLimit
        val slotLimit = Slot(inventory, slot, 0, 0).slotStackLimit
        inventory.setInventorySlotContents(slot, offered.copy())
        val afterStack = inventory.getStackInSlot(slot).copy()
        val after = afterStack.count
        return DevTargetedContainerRecord(
            selector = selector.toString(),
            targetId = definition.id,
            targetClass = definition.className,
            targetModId = definition.modId,
            operation = "setter",
            slot = slot,
            offeredCount = offeredCount,
            preStackCount = before,
            postStackCount = after,
            actualStoredDelta = after - before,
            inventoryLimit = inventoryLimit,
            slotLimit = slotLimit,
            itemLimit = offered.maxStackSize,
            setterResult = "void_return",
            insertResult = null,
            remainderCount = null,
            remainderSource = "NOT_RETURNED_BY_SETTER",
            conservationDelta = null,
            serverStorageStatus = UNKNOWN,
            clientSyncDisplayStatus = UNKNOWN,
            classification = UNKNOWN,
            status = UNKNOWN,
            offeredIdentity = targetedStackIdentity(offered),
            preIdentity = targetedStackIdentity(beforeStack),
            postIdentity = targetedStackIdentity(afterStack),
            operationId = "${definition.id}/direct_setter/slot$slot",
            destinationSlot = slot,
            logicalSide = "SERVER_STORAGE",
            detail = "direct setter has no returned remainder or conservation proof; observedStoredDelta=${after - before}",
        )
    }

    private fun insertRecord(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        target: Any,
        inventory: IInventory?,
        handler: IItemHandler?,
        slot: Int,
        simulate: Boolean,
    ): DevTargetedContainerRecord {
        val operation = if (simulate) "ordinary_insert_simulate" else "ordinary_insert"
        if (handler == null) return unknownRecord(selector, definition, operation, slot, "target exposes neither IItemHandler nor IInventory")
        val offeredCount = 129
        val offered = stack(offeredCount)
        val beforeStack = handler.getStackInSlot(slot).copy()
        val before = beforeStack.count
        val remainder = handler.insertItem(slot, offered, simulate).copy()
        val afterStack = handler.getStackInSlot(slot).copy()
        val after = afterStack.count
        val inventoryLimit = inventory?.inventoryStackLimit
        val slotLimit = handler.getSlotLimit(slot)
        val simulationEvidence = if (simulate) {
            evaluateSimulationEvidence(offeredCount, remainder.count, before, after)
        } else {
            null
        }
        return insertionRecord(
            selector = selector,
            definition = definition,
            operation = operation,
            slot = slot,
            offeredCount = offeredCount,
            preCount = before,
            postCount = after,
            inventoryLimit = inventoryLimit,
            slotLimit = slotLimit,
            itemLimit = offered.maxStackSize,
            setterResult = null,
            insertResult = "remainder_stack_count=${remainder.count};simulate=$simulate",
            remainderCount = remainder.count,
            remainderSource = "RETURNED_BY_INSERT",
            conservationDelta = simulationEvidence?.conservationDelta ?: offeredCount - (after - before) - remainder.count,
            detail = if (simulate) {
                val evidence = requireNotNull(simulationEvidence)
                "simulate=true actualStoredDelta=${evidence.actualStoredDelta} predictedAccepted=${evidence.predictedAcceptedCount}"
            } else {
                null
            },
            simulation = simulate,
            actualStoredDelta = simulationEvidence?.actualStoredDelta ?: after - before,
            predictedAcceptedCount = simulationEvidence?.predictedAcceptedCount,
            offeredIdentity = targetedStackIdentity(offered),
            preIdentity = targetedStackIdentity(beforeStack),
            postIdentity = targetedStackIdentity(afterStack),
            remainderIdentity = targetedStackIdentity(remainder),
        )
    }

    private fun mergeRecord(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        target: Any,
        inventory: IInventory?,
        handler: IItemHandler?,
        slot: Int,
    ): DevTargetedContainerRecord {
        if (inventory == null) return unknownRecord(selector, definition, "merge", slot, "target does not implement IInventory")
        val offeredCount = 129
        val offered = stack(offeredCount)
        val offeredOriginal = offered.copy()
        val beforeStack = inventory.getStackInSlot(slot).copy()
        val before = beforeStack.count
        val container = SingleTargetMergeContainer(inventory, slot)
        val moved = container.merge(offered)
        val afterStack = inventory.getStackInSlot(slot).copy()
        val after = afterStack.count
        val remainder = offered.count
        val remainderStack = offered.copy()
        return insertionRecord(
            selector = selector,
            definition = definition,
            operation = "merge",
            slot = slot,
            offeredCount = offeredCount,
            preCount = before,
            postCount = after,
            inventoryLimit = inventory.inventoryStackLimit,
            slotLimit = container.getSlot(0).slotStackLimit,
            itemLimit = offered.maxStackSize,
            setterResult = null,
            insertResult = "container.mergeItemStack=$moved;source_count=${offered.count}",
            remainderCount = remainder,
            remainderSource = "MUTATED_SOURCE_STACK",
            conservationDelta = offeredCount - (after - before) - remainder,
            detail = null,
            offeredIdentity = targetedStackIdentity(offeredOriginal),
            preIdentity = targetedStackIdentity(beforeStack),
            postIdentity = targetedStackIdentity(afterStack),
            remainderIdentity = targetedStackIdentity(remainderStack),
        )
    }

    private fun shiftTransferRecord(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        target: Any,
        inventory: IInventory?,
        handler: IItemHandler?,
        slot: Int,
        server: MinecraftServer,
    ): DevTargetedContainerRecord {
        if (inventory == null) return unknownRecord(selector, definition, "shift_transfer", slot, "target does not implement IInventory")
        if (target is TileEntityChest || target is InventoryLargeChest) {
            return vanillaChestShiftRecord(selector, definition, inventory, slot, server)
        }
        val offeredCount = 129
        val source = InventoryBasic("StackUpUp targeted shift source", false, 1)
        source.setInventorySlotContents(0, stack(offeredCount))
        val offeredOriginal = source.getStackInSlot(0).copy()
        val container = SingleTargetShiftContainer(source, inventory, slot)
        val player = probePlayer(server)
        val targetBeforeStack = inventory.getStackInSlot(slot).copy()
        val targetBefore = targetBeforeStack.count
        val sourceBefore = source.getStackInSlot(0).count
        val moved = container.transferStackInSlot(player, 0)
        val targetAfterStack = inventory.getStackInSlot(slot).copy()
        val targetAfter = targetAfterStack.count
        val sourceAfterStack = source.getStackInSlot(0).copy()
        val sourceAfter = sourceAfterStack.count
        return insertionRecord(
            selector = selector,
            definition = definition,
            operation = "shift_transfer",
            slot = slot,
            offeredCount = offeredCount,
            preCount = targetBefore,
            postCount = targetAfter,
            inventoryLimit = inventory.inventoryStackLimit,
            slotLimit = container.getSlot(1).slotStackLimit,
            itemLimit = stack(offeredCount).maxStackSize,
            setterResult = null,
            insertResult = "transferStackInSlot=${!moved.isEmpty};source_count=$sourceAfter",
            remainderCount = sourceAfter,
            remainderSource = "MUTATED_SOURCE_INVENTORY",
            conservationDelta = sourceBefore - (targetAfter - targetBefore) - sourceAfter,
            detail = null,
            offeredIdentity = targetedStackIdentity(offeredOriginal),
            preIdentity = targetedStackIdentity(targetBeforeStack),
            postIdentity = targetedStackIdentity(targetAfterStack),
            remainderIdentity = targetedStackIdentity(sourceAfterStack),
            resultIdentity = targetedStackIdentity(moved),
            sourceSlot = 0,
            logicalSide = "SERVER_STORAGE",
        )
    }

    private fun vanillaChestShiftRecord(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        inventory: IInventory,
        slot: Int,
        server: MinecraftServer,
    ): DevTargetedContainerRecord {
        val chestInventory: IInventory = inventory
        val player = probePlayer(server)
        for (index in 0 until player.inventory.sizeInventory) {
            player.inventory.setInventorySlotContents(index, ItemStack.EMPTY)
        }
        for (index in 0 until chestInventory.sizeInventory) {
            chestInventory.setInventorySlotContents(index, if (index == slot) ItemStack.EMPTY else ItemStack(net.minecraft.init.Blocks.STONE, 64))
        }
        val offered = stack(129)
        player.inventory.setInventorySlotContents(0, offered.copy())
        val container = ContainerChest(player.inventory, chestInventory, player)
        val sourceIndex = container.inventorySlots.indexOfFirst { it.inventory === player.inventory && it.slotIndex == 0 }
        check(sourceIndex >= 0) { "ContainerChest source player slot was not found" }
        val targetBeforeStack = chestInventory.getStackInSlot(slot).copy()
        val sourceBeforeStack = player.inventory.getStackInSlot(0).copy()
        val moved = container.transferStackInSlot(player, sourceIndex).copy()
        val targetAfterStack = chestInventory.getStackInSlot(slot).copy()
        val sourceAfterStack = player.inventory.getStackInSlot(0).copy()
        val storedDelta = targetAfterStack.count - targetBeforeStack.count
        val remainder = sourceAfterStack.count
        return insertionRecord(
            selector = selector,
            definition = definition,
            operation = "container_chest_shift_transfer",
            slot = slot,
            offeredCount = offered.count,
            preCount = targetBeforeStack.count,
            postCount = targetAfterStack.count,
            inventoryLimit = chestInventory.inventoryStackLimit,
            slotLimit = container.getSlot(slot).slotStackLimit,
            itemLimit = offered.maxStackSize,
            setterResult = null,
            insertResult = "ContainerChest.transferStackInSlot=$moved;source_container_slot=$sourceIndex",
            remainderCount = remainder,
            remainderSource = "PLAYER_INVENTORY_SLOT_0",
            conservationDelta = offered.count - storedDelta - remainder,
            detail = "real vanilla ContainerChest shift path; direct setter and generic merge are separate operations",
            offeredIdentity = targetedStackIdentity(offered),
            preIdentity = targetedStackIdentity(targetBeforeStack),
            postIdentity = targetedStackIdentity(targetAfterStack),
            remainderIdentity = targetedStackIdentity(sourceAfterStack),
            resultIdentity = targetedStackIdentity(moved),
            sourceSlot = sourceIndex,
            logicalSide = "SERVER_STORAGE",
        )
    }

    private fun outputRecord(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        target: Any,
        inventory: IInventory?,
        handler: IItemHandler?,
        slot: Int,
    ): DevTargetedContainerRecord {
        if (definition.id == "nuclearcraft_alloy_furnace") {
            return unknownRecord(
                selector,
                definition,
                "output",
                slot,
                "NC machine output requires recipe processing or an output-to-receiver transfer; direct setter/decr is not output evidence",
            )
        }
        if (inventory ==
            null
        ) {
            return unknownRecord(selector, definition, "output", slot, "target does not implement IInventory; machine output path is UNKNOWN")
        }
        val seed = stack(1)
        inventory.setInventorySlotContents(slot, seed.copy())
        val beforeStack = inventory.getStackInSlot(slot).copy()
        val before = beforeStack.count
        val requested = 1
        val returned = inventory.decrStackSize(slot, requested).copy()
        val afterStack = inventory.getStackInSlot(slot).copy()
        val after = afterStack.count
        val conservationDelta = before - after - returned.count
        return DevTargetedContainerRecord(
            selector = selector.toString(),
            targetId = definition.id,
            targetClass = definition.className,
            targetModId = definition.modId,
            operation = "output",
            slot = slot,
            offeredCount = requested,
            preStackCount = before,
            postStackCount = after,
            inventoryLimit = inventory.inventoryStackLimit,
            slotLimit = Slot(inventory, slot, 0, 0).slotStackLimit,
            itemLimit = seed.maxStackSize,
            setterResult = "decrStackSize_return_count=${returned.count}",
            insertResult = null,
            remainderCount = null,
            remainderSource = "NOT_APPLICABLE_OUTPUT",
            conservationDelta = conservationDelta,
            serverStorageStatus = if (conservationDelta == 0) PASS else "LOSS",
            clientSyncDisplayStatus = UNKNOWN,
            classification = if (conservationDelta == 0) SERVER_STORAGE_OK else SERVER_STORAGE_LOSS,
            status = if (conservationDelta == 0) PASS else FAIL,
            offeredIdentity = targetedStackIdentity(seed),
            preIdentity = targetedStackIdentity(beforeStack),
            postIdentity = targetedStackIdentity(afterStack),
            resultIdentity = targetedStackIdentity(returned),
            operationId = "${definition.id}/output/slot$slot",
            sourceSlot = slot,
            logicalSide = "SERVER_STORAGE",
            detail = "isolated setter/decrStackSize output path; machine processing/client display not exercised",
        )
    }

    private fun insertionRecord(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        operation: String,
        slot: Int,
        offeredCount: Int,
        preCount: Int,
        postCount: Int,
        inventoryLimit: Int?,
        slotLimit: Int?,
        itemLimit: Int?,
        setterResult: String?,
        insertResult: String?,
        remainderCount: Int?,
        remainderSource: String,
        conservationDelta: Int?,
        detail: String?,
        simulation: Boolean = false,
        actualStoredDelta: Int? = postCount - preCount,
        predictedAcceptedCount: Int? = null,
        offeredIdentity: String? = null,
        preIdentity: String? = null,
        postIdentity: String? = null,
        remainderIdentity: String? = null,
        resultIdentity: String? = null,
        storageStatusOverride: String? = null,
        classificationOverride: String? = null,
        statusOverride: String? = null,
        sourceSlot: Int? = null,
        destinationSlot: Int? = null,
        logicalSide: String = "SERVER_STORAGE",
    ): DevTargetedContainerRecord {
        val hasLoss = conservationDelta != null && conservationDelta != 0
        val simulationMutation = simulation && actualStoredDelta != 0
        val hasRemainder = remainderCount != null && remainderCount > 0
        return DevTargetedContainerRecord(
            selector = selector.toString(),
            targetId = definition.id,
            targetClass = definition.className,
            targetModId = definition.modId,
            operation = operation,
            slot = slot,
            offeredCount = offeredCount,
            preStackCount = preCount,
            postStackCount = postCount,
            actualStoredDelta = actualStoredDelta,
            predictedAcceptedCount = predictedAcceptedCount,
            inventoryLimit = inventoryLimit,
            slotLimit = slotLimit,
            itemLimit = itemLimit,
            setterResult = setterResult,
            insertResult = insertResult,
            remainderCount = remainderCount,
            remainderSource = remainderSource,
            conservationDelta = conservationDelta,
            serverStorageStatus = storageStatusOverride ?: if (simulationMutation || hasLoss) "LOSS" else PASS,
            clientSyncDisplayStatus = UNKNOWN,
            classification = classificationOverride ?: when {
                simulation -> if (simulationMutation) SERVER_STORAGE_LOSS else "SIMULATION_PREDICTION"
                hasLoss -> SERVER_STORAGE_LOSS
                hasRemainder -> LEGITIMATE_REMAINDER
                else -> SERVER_STORAGE_OK
            },
            status = statusOverride ?: if (simulationMutation || hasLoss) FAIL else PASS,
            offeredIdentity = offeredIdentity,
            preIdentity = preIdentity,
            postIdentity = postIdentity,
            remainderIdentity = remainderIdentity,
            resultIdentity = resultIdentity,
            operationId = "${definition.id}/$operation/slot$slot",
            sourceSlot = sourceSlot,
            destinationSlot = destinationSlot ?: slot,
            logicalSide = logicalSide,
            detail = detail,
        )
    }

    private fun unknownTargetRecord(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        detail: String,
    ): DevTargetedContainerRecord = unknownRecord(selector, definition, "target_resolution", null, detail)

    private fun unknownRecord(
        selector: DevTargetedContainerSelector,
        definition: DevTargetedContainerDefinition,
        operation: String,
        slot: Int?,
        detail: String,
    ): DevTargetedContainerRecord = DevTargetedContainerRecord(
        selector = selector.toString(),
        targetId = definition.id,
        targetClass = definition.className,
        targetModId = definition.modId,
        operation = operation,
        slot = slot,
        offeredCount = null,
        preStackCount = null,
        postStackCount = null,
        inventoryLimit = null,
        slotLimit = null,
        itemLimit = null,
        setterResult = null,
        insertResult = null,
        remainderCount = null,
        remainderSource = UNKNOWN,
        conservationDelta = null,
        serverStorageStatus = UNKNOWN,
        clientSyncDisplayStatus = UNKNOWN,
        classification = UNKNOWN,
        status = UNKNOWN,
        operationId = "${definition.id}/$operation/${slot ?: "none"}",
        destinationSlot = slot,
        logicalSide = UNKNOWN,
        detail = detail,
    )

    private fun stack(count: Int): ItemStack = ItemStack(Items.STICK, count)

    private fun probePlayer(server: MinecraftServer): EntityPlayerMP = EntityPlayerMP(
        server,
        server.getWorld(0),
        GameProfile(UUID.randomUUID(), "StackUpUpTargetedProbe"),
        PlayerInteractionManager(server.getWorld(0)),
    )
}

private class SingleTargetMergeContainer(inventory: IInventory, slot: Int) : Container() {
    init {
        addSlotToContainer(Slot(inventory, slot, 0, 0))
    }

    override fun canInteractWith(player: EntityPlayer): Boolean = true

    fun merge(stack: ItemStack): Boolean = mergeItemStack(stack, 0, 1, false)
}

private class SingleTargetShiftContainer(source: IInventory, target: IInventory, targetSlot: Int) : Container() {
    init {
        addSlotToContainer(Slot(source, 0, 0, 0))
        addSlotToContainer(Slot(target, targetSlot, 0, 0))
    }

    override fun canInteractWith(player: EntityPlayer): Boolean = true

    override fun transferStackInSlot(player: EntityPlayer, index: Int): ItemStack {
        val slot = getSlot(index)
        if (!slot.hasStack) return ItemStack.EMPTY
        val source = slot.stack
        val copy = source.copy()
        if (index == 0) {
            if (!mergeItemStack(source, 1, 2, false)) return ItemStack.EMPTY
        } else if (!mergeItemStack(source, 0, 1, false)) {
            return ItemStack.EMPTY
        }
        if (source.isEmpty) slot.putStack(ItemStack.EMPTY) else slot.onSlotChanged()
        return copy
    }
}

private fun StringBuilder.appendJsonField(name: String, value: String?) {
    append('"').append(name).append("\":")
    if (value == null) {
        append("null")
    } else {
        append('"').append(value.jsonEscaped()).append('"')
    }
}

private fun StringBuilder.appendJsonNumber(name: String, value: Int?) {
    append('"').append(name).append("\":")
    append(value ?: "null")
}

private fun String.jsonEscaped(): String = buildString {
    for (character in this@jsonEscaped) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}
