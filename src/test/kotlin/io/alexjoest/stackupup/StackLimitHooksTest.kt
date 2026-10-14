package io.alexjoest.stackupup

import io.alexjoest.stackupup.limit.GregTechMaterialResolver
import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

class StackLimitHooksTest {
    private var previousMaxStackSize: Int = 10240

    @BeforeEach
    fun setUpMaxStackSize() {
        previousMaxStackSize = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 10240
    }

    @AfterEach
    fun restoreMaxStackSize() {
        StackUpUpConfig.maxStackSize = previousMaxStackSize
        RuleRuntime.replaceSnapshot(RuleSnapshot(version = 0L, rules = emptyList()))
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        GregTechMaterialResolver.resetResolverForTesting()
    }

    @Test
    fun getCompatibilityStackSize_shouldReturnGlobalMax() {
        StackUpUpConfig.maxStackSize = 10240
        assertEquals(10240, StackLimitHooks.getCompatibilityStackSize())
    }

    @Test
    fun applyDynamicStackLimit_shouldDelegateToCurrentSnapshot() {
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
    fun applyDynamicStackLimit_shouldResolveFromItemStack() {
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
    fun applyDynamicStackLimit_shouldMatchCreativeTabFromItemStack() {
        Bootstrap.register()
        val item = Item()
            .setCreativeTab(CreativeTabs.MATERIALS)
            .setRegistryName(ResourceLocation("stackupup_test", "tabbed_item"))
        val tabLabel = CreativeTabs.MATERIALS.tabLabel
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 18L,
                rules = listOf(
                    RuleCompiler.compileLine("tab = $tabLabel -> 128", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })

        val result = StackLimitHooks.applyDynamicStackLimit(
            stack = ItemStack(item, 1, 0),
            baseLimit = 64,
        )

        assertEquals(128, result)
    }

    @Test
    fun originalBaseline_shouldNotBePollutedByRuleLimit() {
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
    fun dynamicRuleEvaluation_shouldStartFromOriginalBaseline() {
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
    fun noRules_shouldReturnVanillaBaselineWithoutOreDict() {
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
    fun noOreDepRule_shouldSkipOreDictQuery() {
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
    fun noMaterialRule_shouldSkipMaterialResolver() {
        Bootstrap.register()
        var calls = 0
        val restoreResolver = GregTechMaterialResolver.installResolverForTesting {
            calls++
            "steel"
        }
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 16L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:dummy_item -> 256", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        try {
            val result = StackLimitHooks.applyDynamicStackLimit(
                stack = ItemStack(item, 1, 0),
                baseLimit = 64,
            )

            assertEquals(256, result)
            assertEquals(0, calls)
        } finally {
            restoreResolver()
        }
    }

    @Test
    fun materialRule_shouldCallMaterialResolver() {
        Bootstrap.register()
        var calls = 0
        val restoreResolver = GregTechMaterialResolver.installResolverForTesting {
            calls++
            "steel"
        }
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 17L,
                rules = listOf(
                    RuleCompiler.compileLine("material = steel -> 256", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "material_item"))

        try {
            val result = StackLimitHooks.applyDynamicStackLimit(
                stack = ItemStack(item, 1, 0),
                baseLimit = 64,
            )

            assertEquals(256, result)
            assertEquals(1, calls)
        } finally {
            restoreResolver()
        }
    }

    @Test
    fun materialRule_shouldNotMatchWhenResolverReturnsEmpty() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 16L,
                rules = listOf(
                    RuleCompiler.compileLine("material = steel -> 256", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "non_material_item"))

        val result = StackLimitHooks.applyDynamicStackLimit(
            stack = ItemStack(item, 1, 0),
            baseLimit = 64,
        )

        assertEquals(64, result)
    }

    @Test
    fun normalSlot_shouldAllowLimitExceedingCompatConstant() {
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
    fun emptyContainerMergeSlot_shouldClampDeclaredLimitToInventoryCapacity() {
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
    fun nonEmptyContainerMergeSlot_shouldUseDynamicItemAwareLimit() {
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
    fun smallSlot_shouldNotBeAmplifiedByRuleLimit() {
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
    fun slotAtDynamicLimit_shouldNotDoubleAmplify() {
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
    fun compatLimitEqualsDynamicLimit_shouldNotDoubleAmplify() {
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
    fun multiplicativeRule_shouldNotReMultiplyAtSlotLevel() {
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
    fun itemHandlerSlot_shouldClampToRealSlotLimit() {
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
    fun itemHandler64_shouldRaiseToCompatLimit() {
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
    fun itemHandlerDefault_shouldAllowAtLeastDynamicLimit() {
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
    fun itemHandlerSmallLimit_shouldNotBeAmplified() {
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
    fun useMergeLimit_shouldAllowCompatFallbackToDynamicItemLimit() {
        // 覆盖 InventoryPlayerAddResourceMixin.stackupup$useMergeLimit（canMergeStacks 路径）
        // 对 resolveInventoryClampLimit 的调用语义：合并时按 incoming 堆叠的动态上限放行。
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
    fun usePickedStackLimit_shouldNotAmplifySmallInventoryLimit() {
        // 覆盖 InventoryPlayerAddResourceMixin.stackupup$usePickedStackLimit（addResource 路径）
        // 对 resolveInventoryClampLimit 的调用语义：拾取时按库存真实容量收紧，不做放大。
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
    fun inventoryLimitQuery_shouldBeStableBeforeDuringAndAfterWrite() {
        // inventory-write 通道已删除：库存上限查询不再依赖写入线程状态，
        // 同一对象在真实写入前、写入中和写入后读取 clamp 结果必须一致。
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "write_stability_item"))
        val stack = ItemStack(item, 1, 0)
        val inventory = InventoryBasic("test", false, 1)

        val before = StackLimitHooks.resolveInventoryClampLimit(stack, StackLimitHooks.getCompatibilityStackSize())
        inventory.setInventorySlotContents(0, stack)
        val during = StackLimitHooks.resolveInventoryClampLimit(stack, StackLimitHooks.getCompatibilityStackSize())
        inventory.setInventorySlotContents(0, ItemStack.EMPTY)
        val after = StackLimitHooks.resolveInventoryClampLimit(stack, StackLimitHooks.getCompatibilityStackSize())

        assertEquals(before, during)
        assertEquals(during, after)
        assertEquals(10240, before)
    }

    @Test
    fun creativePacket_shouldAllowDynamicAboveCompat() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 80000
        }.setRegistryName(ResourceLocation("stackupup_test", "creative_packet_item"))
        val stack = ItemStack(item, 80000, 0)

        assertEquals(true, StackLimitHooks.isValidCreativeStackPacket(stack))
    }

    @Test
    fun creativePacket_shouldRejectAboveRealDynamicLimit() {
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
    fun creativeLimit_shouldNotReapplyRelativeToDynamic() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 66
        }.setRegistryName(ResourceLocation("stackupup_test", "creative_limit_item"))
        val stack = ItemStack(item, 1, 0)

        assertEquals(66, StackLimitHooks.resolveCreativeStackLimit(stack))
    }

    @Test
    fun nestedItemLimit_shouldNotReapplyRules() {
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
    fun normalSlot_shouldNotReapplyRelativeToDynamicItem() {
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

    // ---- T4b：内容键缓存（替代 mark/consume 实例身份 ThreadLocal） ----

    @Test
    fun contentKeyCache_shouldReturnCachedValueForUnchangedContent() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "cache_hit_item"))
        val stack = ItemStack(item, 1, 0)

        StackLimitHooks.cacheResolvedItemLimit(stack, 512)

        assertEquals(512, StackLimitHooks.lookupResolvedItemLimit(stack))
    }

    @Test
    fun resolutionResult_shouldBeReusableViaContentKeyCache() {
        // 模拟 ItemMixin(写) + ItemStackMixin(读) 的 getMaxStackSize 契约：
        // 内层 getItemStackLimit 已按规则解析并写入内容键缓存，外层直接复用，不得在 128 上再乘 2。
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 42L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:reuse_item -> *2", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "reuse_item"))
        val stack = ItemStack(item, 1, 0)

        val inner = StackLimitHooks.applyDynamicStackLimit(stack, 64)
        StackLimitHooks.cacheResolvedItemLimit(stack, inner)

        assertEquals(128, StackLimitHooks.lookupResolvedItemLimit(stack))
        assertNotEquals(256, StackLimitHooks.lookupResolvedItemLimit(stack))
    }

    @Test
    fun sameInstanceMetaMutation_shouldResolveNewValueByContent() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 30L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:mut_item@0 -> 128", 1),
                    RuleCompiler.compileLine("item = stackupup_test:mut_item@1 -> 256", 2),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "mut_item"))
        val stack = ItemStack(item, 1, 0)

        val first = StackLimitHooks.applyDynamicStackLimit(stack, 64)
        StackLimitHooks.cacheResolvedItemLimit(stack, first)
        assertEquals(128, first)

        stack.setItemDamage(1)

        // 同一实例变更 meta 后，旧内容键不得复用旧值；重新解析得到新内容的值。
        assertNull(StackLimitHooks.lookupResolvedItemLimit(stack))
        val second = StackLimitHooks.applyDynamicStackLimit(stack, 64)
        assertEquals(256, second)
        assertNotEquals(first, second)
    }

    @Test
    fun sameInstanceNbtMutation_shouldResolveNewValueByContent() {
        // 基线随 NBT 变化（模拟 modded item 的 getItemStackLimit 读取 NBT）。
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = if (stack.tagCompound?.getBoolean("large") == true) 512 else 64
        }.setRegistryName(ResourceLocation("stackupup_test", "nbt_mut_item"))
        val stack = ItemStack(item, 1, 0)

        val first = StackLimitHooks.applyDynamicStackLimit(stack, 64)
        StackLimitHooks.cacheResolvedItemLimit(stack, first)
        assertEquals(64, first)

        val tag = NBTTagCompound()
        tag.setBoolean("large", true)
        stack.setTagCompound(tag)

        assertNull(StackLimitHooks.lookupResolvedItemLimit(stack))
        val second = StackLimitHooks.applyDynamicStackLimit(stack, 64)
        assertEquals(512, second)
        assertNotEquals(first, second)
    }

    @Test
    fun sameInstanceInPlaceNbtMutation_shouldNotReuseOldValue() {
        // 原地变异同一 NBT 实例：内容变化后旧条目不可达，读取必须 miss 并重新解析。
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = if (stack.tagCompound?.getBoolean("large") == true) 512 else 64
        }.setRegistryName(ResourceLocation("stackupup_test", "nbt_inplace_mut_item"))
        val stack = ItemStack(item, 1, 0)
        val tag = NBTTagCompound()
        tag.setBoolean("large", true)
        stack.setTagCompound(tag)

        val first = StackLimitHooks.applyDynamicStackLimit(stack, 64)
        StackLimitHooks.cacheResolvedItemLimit(stack, first)
        assertEquals(512, first)

        stack.tagCompound?.setBoolean("large", false)

        assertNull(StackLimitHooks.lookupResolvedItemLimit(stack))
        assertEquals(64, StackLimitHooks.applyDynamicStackLimit(stack, 64))
    }

    @Test
    fun writeOnlyCacheCall_shouldNotRetainStackInstance() {
        // 模拟 isEnchantable / LootEntryItem.addLoot 的只写调用：
        // 只调用 getItemStackLimit（写缓存）而不经 getMaxStackSize 读取，
        // 内容键缓存不得留下对 ItemStack 实例的强引用。
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "weak_ref_item"))

        val weak = cacheAndDropStack(item)

        assertNull(awaitCollection(weak))
    }

    @Test
    fun writeOnlyCalls_shouldNotGrowCacheForSameContent() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "count_item"))
        val stack = ItemStack(item, 1, 0)

        repeat(50) { index -> StackLimitHooks.cacheResolvedItemLimit(stack, index) }

        assertEquals(1, StackLimitHooks.debugResolvedContentCacheSize())
    }

    @Test
    fun contentKeyCache_shouldBeInvalidatedOnSnapshotReplacement() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "epoch_item"))
        val stack = ItemStack(item, 1, 0)
        StackLimitHooks.cacheResolvedItemLimit(stack, 512)

        RuleRuntime.replaceSnapshot(RuleSnapshot(version = 40L, rules = emptyList()))

        assertNull(StackLimitHooks.lookupResolvedItemLimit(stack))
        assertEquals(0, StackLimitHooks.debugResolvedContentCacheSize())
    }

    @Test
    fun cachedLimit_shouldNotBeServedInsideOriginalBaselineBypass() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = StackLimitHooks.lookupResolvedItemLimit(stack) ?: 64
        }.setRegistryName(ResourceLocation("stackupup_test", "bypass_cache_item"))
        val stack = ItemStack(item, 1, 0)
        StackLimitHooks.cacheResolvedItemLimit(stack, 512)

        // 基线解析在 bypass 中运行：不得把规则化缓存值泄漏进原始基线。
        assertEquals(64, StackLimitHooks.resolveOriginalBaseline(stack))
    }

    @Test
    fun reentrantResolution_shouldShortCircuitWhenEnteringItemMixin() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 41L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:reentry_item -> 1024", 1),
                ),
            ),
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "reentry_item"))

        StackLimitHooks.enteringItemMixin.set(true)
        try {
            // 重入：enteringItemMixin 置位时不得再次应用规则，基线按原样返回。
            val result = StackLimitHooks.applyDynamicStackLimit(ItemStack(item, 1, 0), 1024)
            assertEquals(1024, result)
        } finally {
            StackLimitHooks.enteringItemMixin.remove()
        }
    }

    private fun cacheAndDropStack(item: Item): WeakReference<ItemStack> {
        val stack = ItemStack(item, 1, 0)
        val weak = WeakReference(stack)
        StackLimitHooks.cacheResolvedItemLimit(stack, 512)
        return weak
    }

    private fun awaitCollection(weak: WeakReference<ItemStack>): ItemStack? {
        repeat(20) {
            if (weak.get() == null) {
                return null
            }
            System.gc()
            Thread.sleep(5)
        }
        return weak.get()
    }
}
