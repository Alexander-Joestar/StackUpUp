package io.alexjoest.stackupup

import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot

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
    }

    @Test
    fun `兼容上限入口应返回全局兼容最大堆叠上限`() {
        StackUpUpConfig.maxStackSize = 10240
        assertEquals(10240, StackLimitHooks.getCompatibilityStackSize())
    }

    @Test
    fun `动态规则入口应委托给当前快照`() {
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 2L,
                rules = listOf(
                    RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                    RuleCompiler.compileLine("ore = ingotSteel -> *2", 2)
                )
            )
        )

        val result = StackLimitHooks.applyDynamicStackLimit(
            itemId = "gregtech:gt.metaitem.01",
            modId = "gregtech",
            meta = 11305,
            type = "item",
            baseLimit = 64,
            oreNames = setOf("ingotSteel")
        )

        assertEquals(1024, result)
    }

    @Test
    fun `动态规则入口应支持直接从物品栈解析`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 3L,
                rules = listOf(
                    RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                    RuleCompiler.compileLine("ore = ingotSteel -> *2", 2)
                )
            )
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { setOf("ingotSteel") })
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_ingot"))

        val result = StackLimitHooks.applyDynamicStackLimit(
            stack = ItemStack(item, 1, 324),
            baseLimit = 64
        )

        assertEquals(1024, result)
    }

    @Test
    fun `原始基线解析不应被规则后的上限污染`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 14L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:baseline_item -> 128", 1)
                )
            )
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "baseline_item"))
        val stack = ItemStack(item, 1, 0)

        assertEquals(64, StackLimitHooks.resolveOriginalBaseline(stack))
        assertEquals(128, StackLimitHooks.applyDynamicStackLimit(stack, 64))
    }

    @Test
    fun `动态规则求值应始终从原始基线开始而不是从当前上限开始`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 15L,
                rules = listOf(
                    RuleCompiler.compileLine("size > 1 -> +2", 1)
                )
            )
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
    fun `无规则时应直接返回原版基线而不触发矿辞查询`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(RuleSnapshot(version = 4L, rules = emptyList()))
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { error("无规则时不应触发矿辞查询") })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        val result = StackLimitHooks.applyDynamicStackLimit(
            stack = ItemStack(item, 1, 0),
            baseLimit = 64
        )

        assertEquals(64, result)
    }

    @Test
    fun `不依赖矿辞的规则集应跳过矿辞查询`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 5L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:dummy_item -> 256", 1)
                )
            )
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { error("不依赖矿辞的规则不应触发矿辞查询") })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        val result = StackLimitHooks.applyDynamicStackLimit(
            stack = ItemStack(item, 1, 0),
            baseLimit = 64
        )

        assertEquals(256, result)
    }

    @Test
    fun `普通槽位应允许规则上限突破兼容常量上限`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = ItemStack(item, 1, 0),
            slotLimit = StackLimitHooks.getCompatibilityStackSize()
        )

        assertEquals(10240, result)
    }

    @Test
    fun `专用小槽位不应被规则上限放大`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 7L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:dummy_item -> 10240", 1)
                )
            )
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = ItemStack(item, 1, 0),
            slotLimit = 1
        )

        assertEquals(1, result)
    }

    @Test
    fun `已达到物品动态上限的槽位返回值不应重复放大`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 8L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:dummy_item -> 10240", 1)
                )
            )
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))
        val stack = ItemStack(item, 1, 0)
        val dynamicLimit = StackLimitHooks.applyDynamicStackLimit(stack, 1024)

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = stack,
            slotLimit = dynamicLimit
        )

        assertEquals(dynamicLimit, result)
    }

    @Test
    fun `当兼容上限与物品动态上限一致时不应重复放大`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = stack,
            slotLimit = StackLimitHooks.getCompatibilityStackSize()
        )

        assertEquals(10240, result)
    }

    @Test
    fun `乘法规则下已计算出的物品上限不应在槽位层重复乘算`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 10L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:dummy_item -> *160", 1)
                )
            )
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))
        val stack = ItemStack(item, 1, 0)
        val dynamicLimit = StackLimitHooks.applyDynamicStackLimit(stack, 64)

        val result = StackLimitHooks.resolveDynamicSlotLimit(
            stack = stack,
            slotLimit = dynamicLimit
        )

        assertEquals(10240, dynamicLimit)
        assertEquals(dynamicLimit, result)
    }

    @Test
    fun `ItemHandler 槽位应被真实槽位上限钳制而不是继续放大`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int {
                return if (stack.count > 2) 102400 else 10240
            }
        }.setRegistryName(ResourceLocation("stackupup_test", "dynamic_item_handler_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveItemHandlerSlotLimit(
            stack = stack,
            simulatedLimit = 102400,
            slotLimit = 10240
        )

        assertEquals(10240, result)
    }

    @Test
    fun `ItemHandler 默认 64 物品在兼容槽位中应提升到兼容上限`() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "vanilla_item_handler_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveItemHandlerSlotLimit(
            stack = stack,
            simulatedLimit = 64,
            slotLimit = 64
        )

        assertEquals(64, result)
    }

    @Test
    fun `ItemHandler 默认槽位应至少允许放入当前输入栈的动态上限`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "default_item_handler_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveItemHandlerSlotLimit(
            stack = stack,
            simulatedLimit = 1024,
            slotLimit = 10240
        )

        assertEquals(10240, result)
    }

    @Test
    fun `ItemHandler 显式小上限物品不应被兼容槽位放大`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 16
        }.setRegistryName(ResourceLocation("stackupup_test", "small_item_handler_item"))
        val stack = ItemStack(item, 1, 0)

        val result = StackLimitHooks.resolveItemHandlerSlotLimit(
            stack = stack,
            simulatedLimit = 16,
            slotLimit = 10240
        )

        assertEquals(16, result)
    }

    @Test
    fun `库存写入钳制应允许默认兼容上限落入真实动态物品上限`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "inventory_clamp_item"))
        val stack = ItemStack(item, 10240, 0)

        val result = StackLimitHooks.resolveInventoryClampLimit(
            stack = stack,
            inventoryLimit = StackLimitHooks.getCompatibilityStackSize()
        )

        assertEquals(10240, result)
    }

    @Test
    fun `库存写入钳制不应放大小上限库存`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 10240
        }.setRegistryName(ResourceLocation("stackupup_test", "small_inventory_clamp_item"))
        val stack = ItemStack(item, 10240, 0)

        val result = StackLimitHooks.resolveInventoryClampLimit(
            stack = stack,
            inventoryLimit = 1
        )

        assertEquals(1, result)
    }

    @Test
    fun `创造模式发包校验应允许超过兼容常量的动态物品上限`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 80000
        }.setRegistryName(ResourceLocation("stackupup_test", "creative_packet_item"))
        val stack = ItemStack(item, 80000, 0)

        assertEquals(true, StackLimitHooks.isValidCreativeStackPacket(stack))
    }

    @Test
    fun `创造模式发包校验不应放过超过真实动态上限的物品`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 12L,
                rules = listOf(
                    RuleCompiler.compileLine("item = stackupup_test:creative_packet_item -> 128", 1)
                )
            )
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "creative_packet_item"))
        val stack = ItemStack(item, 256, 0)

        assertEquals(false, StackLimitHooks.isValidCreativeStackPacket(stack))
    }

    @Test
    fun `创造模式上限不应对已动态化的物品再次应用相对动作`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 66
        }.setRegistryName(ResourceLocation("stackupup_test", "creative_limit_item"))
        val stack = ItemStack(item, 1, 0)

        assertEquals(66, StackLimitHooks.resolveCreativeStackLimit(stack))
    }

    @Test
    fun `嵌套的 ItemStack 上限读取不应重复应用规则`() {
        Bootstrap.register()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 13L,
                rules = listOf(
                    RuleCompiler.compileLine("size > 1 -> +2", 1)
                )
            )
        )
        RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { emptySet() })
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "nested_item_limit"))
        val stack = ItemStack(item, 1, 0)

        val firstPass = StackLimitHooks.applyDynamicStackLimit(stack, 64)
        val marked = StackLimitHooks.markResolvedItemLimit(stack, firstPass)

        assertEquals(66, marked)
        assertEquals(true, StackLimitHooks.shouldSkipNestedItemStackLimit(stack, 66))
        assertEquals(false, StackLimitHooks.shouldSkipNestedItemStackLimit(stack, 66))
    }

    @Test
    fun `普通槽位不应对已动态化的物品上限重复执行相对动作`() {
        Bootstrap.register()
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = 66
        }.setRegistryName(ResourceLocation("stackupup_test", "dynamic_slot_item"))
        val stack = ItemStack(item, 1, 0)

        assertEquals(
            66,
            StackLimitHooks.resolveDynamicSlotLimit(
                stack = stack,
                slotLimit = 66
            )
        )
    }
}



