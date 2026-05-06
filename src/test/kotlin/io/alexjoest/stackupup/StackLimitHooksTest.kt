package io.alexjoest.stackupup

import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StackLimitHooksTest {
    private var previousMaxStackSize: Int = 10240

    @BeforeEach
    fun setUpMaxStackSize() {
        previousMaxStackSize = StackUpUpConfig.activeMaxStackSize
        StackUpUpConfig.general.maxStackSize = 10240
        StackUpUpConfig.activeMaxStackSize = 10240
    }

    @AfterEach
    fun restoreMaxStackSize() {
        StackUpUpConfig.activeMaxStackSize = previousMaxStackSize
    }

    fun `getCompatibilityStackSize_shouldReturnGlobalMax`() {
        StackUpUpConfig.general.maxStackSize = 10240
        StackUpUpConfig.activeMaxStackSize = 10240
        assertEquals(10240, StackLimitHooks.getCompatibilityStackSize())
    }

    @Test
    fun `applyDynamicStackLimit_shouldDelegateToCurrentSnapshot`() {
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 2L,
                rules = listOf(
                    RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                    RuleCompiler.compileLine("ore = ingotSteel -> *2", 2),
                ),
            ),
        )

        val result = StackLimitHooks.applyDynamicStackLimit(
            itemId = "gregtech:gt.metaitem.01",
            modId = "gregtech",
            meta = 11305,
            type = "item",
            baseLimit = 64,
            oreNames = setOf("ingotSteel"),
        )

        assertEquals(1024, result)
    }

    @Test
    fun `applyDynamicStackLimit_shouldResolveFromItemStack`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 3L,
                rules = listOf(
                    RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                    RuleCompiler.compileLine("ore = ingotSteel -> *2", 2),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { setOf("ingotSteel") })
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_ingot"))

        val result = StackLimitHooks.applyDynamicStackLimit(
            stack = ItemStack(item, 1, 324),
            baseLimit = 64,
        )

        assertEquals(1024, result)
    }

    @Test
    fun `originalBaseline_shouldNotBePollutedByRuleLimit`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 14L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:baseline_item -> 128", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "baseline_item"))
        val stack = ItemStack(item, 1, 0)

        assertEquals(64, StackLimitHooks.resolveOriginalBaseline(stack))
        assertEquals(128, StackLimitHooks.applyDynamicStackLimit(stack, 64))
    }

    @Test
    fun `dynamicRuleEvaluation_shouldStartFromOriginalBaseline`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 15L,
                rules = listOf(
                    RuleCompiler.compileLine("size > 1 -> +2", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 64
        }.setRegistryName(ResourceLocation("stackupup_test", "baseline_rule_item"))
        val stack = ItemStack(item, 1, 0)

        assertEquals(64, StackLimitHooks.resolveOriginalBaseline(stack))
        assertEquals(66, StackLimitHooks.applyDynamicStackLimit(stack, 1024))
    }

    @Test
    fun `noRules_shouldReturnVanillaBaselineWithoutOreDict`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(RuleSnapshot(version = 4L, rules = emptyList()))
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { error("ore dict must not be queried when no rules exist") })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        val result = StackLimitHooks.applyDynamicStackLimit(
            stack = ItemStack(item, 1, 0),
            baseLimit = 64,
        )

        assertEquals(64, result)
    }

    @Test
    fun `noOreDepRule_shouldSkipOreDictQuery`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 5L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:dummy_item -> 256", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { error("ore dict must not be queried when no ore-dependent rule") })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        val result = StackLimitHooks.applyDynamicStackLimit(
            stack = ItemStack(item, 1, 0),
            baseLimit = 64,
        )

        assertEquals(256, result)
    }

    @Test
    fun `normalSlot_shouldAllowLimitExceedingCompatConstant`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = ItemStack(item, 1, 0),
            slotLimit = StackLimitHooks.getCompatibilityStackSize(),
        )

        assertEquals(10240, result)
    }

    @Test
    fun `emptyContainerMergeSlot_shouldClampDeclaredLimitToInventoryCapacity`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))
        val inventory = object : InventoryBasic("test", false, 1) {
            override fun getInventoryStackLimit(): Int = 64
        }
        val slot = Slot(inventory, 0, 0, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, ItemStack(item, 1, 0), 128)

        assertEquals(64, result)
    }

    @Test
    fun `nonEmptyContainerMergeSlot_shouldUseDynamicItemAwareLimit`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))
        val inventory = object : InventoryBasic("test", false, 1) {
            override fun getInventoryStackLimit(): Int = 10240
        }
        val slot = Slot(inventory, 0, 0, 0)
        slot.putStack(ItemStack(item, 1, 0))

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, ItemStack(item, 1, 0), 64)

        assertEquals(10240, result)
    }

    @Test
    fun `smallSlot_shouldNotBeAmplifiedByRuleLimit`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 7L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:dummy_item -> 10240", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = ItemStack(item, 1, 0),
            slotLimit = 1,
        )

        assertEquals(1, result)
    }

    @Test
    fun `slotAtDynamicLimit_shouldNotDoubleAmplify`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 8L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:dummy_item -> 10240", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))
        val stack = ItemStack(item, 1, 0)
        val dynamicLimit = StackLimitHooks.applyDynamicStackLimit(stack, 1024)

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = stack,
            slotLimit = dynamicLimit,
        )

        assertEquals(dynamicLimit, result)
    }

    @Test
    fun `compatLimitEqualsDynamicLimit_shouldNotDoubleAmplify`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = stack,
            slotLimit = StackLimitHooks.getCompatibilityStackSize(),
        )

        assertEquals(10240, result)
    }

    @Test
    fun `multiplicativeRule_shouldNotReMultiplyAtSlotLevel`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 10L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:dummy_item -> *160", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))
        val stack = ItemStack(item, 1, 0)
        val dynamicLimit = StackLimitHooks.applyDynamicStackLimit(stack, 64)

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = stack,
            slotLimit = dynamicLimit,
        )

        assertEquals(10240, dynamicLimit)
        assertEquals(dynamicLimit, result)
    }

    @Test
    fun `itemHandlerSlot_shouldClampToRealSlotLimit`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = if (stack.count > 2) 102400 else 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "dynamic_item_handler_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveItemHandlerSlotLimit(
            stack = stack,
            simulatedLimit = 102400,
            slotLimit = 10240,
        )

        assertEquals(10240, result)
    }

    @Test
    fun `itemHandler64_shouldRaiseToCompatLimit`() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "vanilla_item_handler_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveItemHandlerSlotLimit(
            stack = stack,
            simulatedLimit = 64,
            slotLimit = 64,
        )

        assertEquals(64, result)
    }

    @Test
    fun `itemHandlerDefault_shouldAllowAtLeastDynamicLimit`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "default_item_handler_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveItemHandlerSlotLimit(
            stack = stack,
            simulatedLimit = 1024,
            slotLimit = 10240,
        )

        assertEquals(10240, result)
    }

    @Test
    fun `itemHandlerSmallLimit_shouldNotBeAmplified`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 16
        }.setRegistryName(ResourceLocation("stackupup_test", "small_item_handler_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveItemHandlerSlotLimit(
            stack = stack,
            simulatedLimit = 16,
            slotLimit = 10240,
        )

        assertEquals(16, result)
    }

    @Test
    fun `inventoryClamp_shouldAllowCompatFallbackToDynamic`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "inventory_clamp_item"))
        val stack = ItemStack(item, 10240, 0)

        val result = StackLimitHooks.resolveInventoryClampLimit(
            stack = stack,
            inventoryLimit = StackLimitHooks.getCompatibilityStackSize(),
        )

        assertEquals(10240, result)
    }

    @Test
    fun `inventoryClamp_shouldNotAmplifySmallLimit`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "small_inventory_clamp_item"))
        val stack = ItemStack(item, 10240, 0)

        val result = StackLimitHooks.resolveInventoryClampLimit(
            stack = stack,
            inventoryLimit = 1,
        )

        assertEquals(1, result)
    }

    @Test
    fun `creativePacket_shouldAllowDynamicAboveCompat`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 80000
        }.setRegistryName(ResourceLocation("stackupup_test", "creative_packet_item"))
        val stack = ItemStack(item, 80000, 0)

        assertEquals(true, StackLimitHooks.isValidCreativeStackPacket(stack))
    }

    @Test
    fun `creativePacket_shouldRejectAboveRealDynamicLimit`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 12L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:creative_packet_item -> 128", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "creative_packet_item"))
        val stack = ItemStack(item, 256, 0)

        assertEquals(false, StackLimitHooks.isValidCreativeStackPacket(stack))
    }

    @Test
    fun `creativeLimit_shouldNotReapplyRelativeToDynamic`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 66
        }.setRegistryName(ResourceLocation("stackupup_test", "creative_limit_item"))
        val stack = ItemStack(item, 1, 0)

        assertEquals(66, StackLimitHooks.resolveCreativeStackLimit(stack))
    }

    @Test
    fun `nestedItemLimit_shouldNotReapplyRules`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 13L,
                rules = listOf(
                    RuleCompiler.compileLine("size > 1 -> +2", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "nested_item_limit"))
        val stack = ItemStack(item, 1, 0)

        val firstPass = StackLimitHooks.applyDynamicStackLimit(stack, 64)

        assertEquals(66, firstPass)
    }

    @Test
    fun `normalSlot_shouldNotReapplyRelativeToDynamicItem`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 66
        }.setRegistryName(ResourceLocation("stackupup_test", "dynamic_slot_item"))
        val stack = ItemStack(item, 1, 0)

        assertEquals(
            66,
            StackLimitHooks.resolveDynamicSlotLimit(
                stack = stack,
                slotLimit = 66,
            ),
        )
    }
}
