package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.rules.ComparisonOperator
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.field.RuleFieldMatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StackLimitServiceTest {
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
    fun shouldExecuteRulesInFileOrder() {
        val snapshot = RuleSnapshot(
            version = 1L,
            rules = listOf(
                RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                RuleCompiler.compileLine("ore = ingotSteel -> *2", 2),
            ),
        )
        val service = StackLimitService(snapshot)
        val result = service.resolve(
            context("gregtech:gt.metaitem.01", 11305, "item", oreNames = setOf("ingotSteel")),
        )
        assertEquals(1024, result)
    }

    @Test
    fun sameContext_shouldHitCache() {
        var evaluations = 0
        val snapshot = RuleSnapshot(
            version = 2L,
            rules = listOf(
                RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                RuleCompiler.compileLine("ore = ingotSteel -> *2", 2).let { compiled ->
                    // sealed matcher 不允许测试模块实现委托包装，改为在 selector 处计数：
                    // 与 ORE 字段编译产物的求值路径一致，且每次 matches 只调用一次 selector。
                    compiled.copy(
                        matcher = RuleFieldMatchers.stringSet { context ->
                            evaluations++
                            context.oreNames
                        }.compile(ComparisonOperator.EQUALS, "ingotSteel"),
                    )
                },
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("gregtech:meta_ingot", 324, "item", oreNames = setOf("ingotSteel"))

        assertEquals(1024, service.resolve(identity))
        assertEquals(1024, service.resolve(identity))
        assertEquals(1, evaluations)
        assertEquals(1, service.debugResolvedCacheSize())
    }

    @Test
    fun shouldSupportItemWithMetadataSugar() {
        val snapshot = RuleSnapshot(
            version = 3L,
            rules = listOf(
                RuleCompiler.compileLine("item = gregtech:gt.metaitem.01@11305 -> 1024", 1),
            ),
        )
        val service = StackLimitService(snapshot)

        assertEquals(
            1024,
            service.resolve(
                context("gregtech:gt.metaitem.01", 11305, "item"),
            ),
        )
        assertEquals(
            64,
            service.resolve(
                context("gregtech:gt.metaitem.01", 11306, "item"),
            ),
        )
    }

    @Test
    fun shouldExecuteActionChainInOrder() {
        val snapshot = RuleSnapshot(
            version = 4L,
            rules = listOf(
                RuleCompiler.compileLine("ore = ingotSteel -> *2 -> +10", 1),
            ),
        )
        val service = StackLimitService(snapshot)

        assertEquals(
            138,
            service.resolve(
                context("gregtech:gt.metaitem.01", 11305, "item", oreNames = setOf("ingotSteel")),
            ),
        )
    }

    @Test
    fun runtimeResult_shouldBeClampedByMaxStackSize() {
        val previous = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 256
        try {
            val snapshot = RuleSnapshot(
                version = 5L,
                rules = listOf(
                    RuleCompiler.compileLine("item = minecraft:egg -> 999999", 1),
                ),
            )
            val service = StackLimitService(snapshot)

            assertEquals(
                256,
                service.resolve(
                    context("minecraft:egg", 0, "item", baseLimit = 16),
                ),
            )
        } finally {
            StackUpUpConfig.maxStackSize = previous
        }
    }

    @Test
    fun emptySnapshot_shouldClampBaseLimitWithoutCaching() {
        StackUpUpConfig.maxStackSize = 128
        val service = StackLimitService(RuleSnapshot(version = 10L, rules = emptyList()))
        val identity = context("minecraft:egg", 0, "item", baseLimit = 16)

        assertEquals(
            128,
            service.resolve(context(identity, baseLimit = 999, tab = "ignored", material = "ignored")),
        )
        assertEquals(
            1,
            service.resolve(context(identity, baseLimit = 0, tab = "ignored", material = "ignored")),
        )
        assertEquals(0, service.debugResolvedCacheSize())
    }

    @Test
    fun materialDependentRules_shouldPartitionResolvedCacheByMaterial() {
        val snapshot = RuleSnapshot(
            version = 6L,
            rules = listOf(
                RuleCompiler.compileLine("material = steel -> 1024", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("gregtech:meta_item_1", 1000, "item")

        assertEquals(1024, service.resolve(context(identity, material = "steel")))
        assertEquals(64, service.resolve(context(identity, material = "copper")))
        assertEquals(2, service.debugResolvedCacheSize())
    }

    @Test
    fun materialIndependentRules_shouldNotPartitionResolvedCacheByMaterial() {
        val snapshot = RuleSnapshot(
            version = 7L,
            rules = listOf(
                RuleCompiler.compileLine("item = gregtech:meta_item_1 -> 512", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("gregtech:meta_item_1", 1000, "item")

        assertEquals(512, service.resolve(context(identity, material = "steel")))
        assertEquals(512, service.resolve(context(identity, material = "copper")))
        assertEquals(1, service.debugResolvedCacheSize())
    }

    @Test
    fun tabDependentRules_shouldPartitionResolvedCacheByTab() {
        val snapshot = RuleSnapshot(
            version = 8L,
            rules = listOf(
                RuleCompiler.compileLine("tab = buildingBlocks -> 256", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("minecraft:stone", 0, "block")

        assertEquals(256, service.resolve(context(identity, tab = "buildingBlocks")))
        assertEquals(64, service.resolve(context(identity, tab = "materials")))
        assertEquals(2, service.debugResolvedCacheSize())
    }

    @Test
    fun mixedDynamicFields_shouldPartitionResolvedCacheByDeclaredFields() {
        val snapshot = RuleSnapshot(
            version = 9L,
            rules = listOf(
                RuleCompiler.compileLine("material = steel && tab = materials -> 1024", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("gregtech:meta_item_1", 1000, "item")

        assertEquals(1024, service.resolve(context(identity, tab = "materials", material = "steel")))
        assertEquals(64, service.resolve(context(identity, tab = "materials", material = "copper")))
        assertEquals(64, service.resolve(context(identity, tab = "tools", material = "steel")))
        assertEquals(3, service.debugResolvedCacheSize())
    }

    @Test
    fun exactItemMatcher_shouldMatchOnlyExactIdAndMetadata() {
        val snapshot = RuleSnapshot(
            version = 11L,
            rules = listOf(
                RuleCompiler.compileLine("item = minecraft:foo.bar@7 -> 128", 1),
            ),
        )
        val service = StackLimitService(snapshot)

        assertEquals(128, service.resolve(context("minecraft:foo.bar", 7, "item")))
        assertEquals(64, service.resolve(context("minecraft:foo.bar", 8, "item")))
        assertEquals(64, service.resolve(context("minecraft:fooXbar", 7, "item")))
    }

    @Test
    fun singleItemWildcardSet_shouldShortCircuitWithStackabilityParity() {
        val previous = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 256
        try {
            val snapshot = RuleSnapshot(
                version = 12L,
                rules = listOf(
                    RuleCompiler.compileLine("item = * -> 999", 1),
                ),
            )
            val shortcut = StackLimitService(snapshot)
            val generic = StackLimitService(snapshot, forceSlowPath = true)
            val cases = listOf(
                context("minecraft:stick", 0, "item", baseLimit = 64),
                context("minecraft:diamond_sword", 0, "item", baseLimit = 1),
                context("minecraft:invalid", 0, "item", baseLimit = 0),
            )

            for (case in cases) {
                assertEquals(generic.resolve(case), shortcut.resolve(case), "context=$case")
            }
            assertEquals(256, shortcut.resolve(cases[0]))
            assertEquals(1, shortcut.resolve(cases[1]))
            assertEquals(0, shortcut.debugResolvedCacheSize())
        } finally {
            StackUpUpConfig.maxStackSize = previous
        }
    }

    @Test
    fun wildcardShortcut_shouldRequirePositiveSingleSetAction() {
        val chained = StackLimitService(
            RuleSnapshot(
                version = 13L,
                rules = listOf(RuleCompiler.compileLine("item = * -> 512 -> +10", 1)),
            ),
        )
        assertEquals(522, chained.resolve(context("minecraft:stick", 0, "item", baseLimit = 64)))
        assertEquals(1, chained.resolve(context("minecraft:sword", 0, "item", baseLimit = 1)))

        val negative = StackLimitService(
            RuleSnapshot(
                version = 14L,
                rules = listOf(RuleCompiler.compileLine("item != * -> 512", 1)),
            ),
        )
        assertEquals(64, negative.resolve(context("minecraft:stick", 0, "item", baseLimit = 64)))
        assertEquals(512, negative.resolve(context("minecraft:sword", 0, "item", baseLimit = 1)))
    }

    // ---- T6：缓存键机械推导与快路径命中 ----

    @Test
    fun contextsDifferingOnlyInReadField_shouldNotShareCacheEntries() {
        // 规则读取 tab，tab 必须进入字段缓存键：只差 tab 的两个上下文不得共享条目。
        val snapshot = RuleSnapshot(
            version = 20L,
            rules = listOf(
                RuleCompiler.compileLine("tab = buildingBlocks -> 256", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val base = context("minecraft:stone", 0, "block", tab = "buildingBlocks")

        assertEquals(256, service.resolve(base))
        assertEquals(64, service.resolve(context(base, tab = "materials")))
        assertEquals(2, service.debugResolvedCacheSize())
    }

    @Test
    fun fastAndSlowPath_shouldResolveIdentically() {
        // 同一快照分别走快路径（自动定型）与强制慢路径，结果必须逐上下文一致。
        val snapshot = RuleSnapshot(
            version = 21L,
            rules = listOf(
                RuleCompiler.compileLine("material = steel -> 1024", 1),
            ),
        )
        val fast = StackLimitService(snapshot)
        val slow = StackLimitService(snapshot, forceSlowPath = true)
        assertTrue(fast.usesFastPath())
        assertFalse(slow.usesFastPath())

        val cases = listOf(
            context("gregtech:meta_item_1", 1000, "item", material = "steel"),
            context("gregtech:meta_item_1", 1000, "item", material = "copper"),
            context("gregtech:meta_item_1", 1001, "item", material = "steel"),
            context("minecraft:egg", 0, "item", material = ""),
        )
        for (case in cases) {
            assertEquals(fast.resolve(case), slow.resolve(case), "context=$case")
        }
    }

    @Test
    fun oreRules_shouldNotUseFastPath() {
        // ORE 的身份稳定性契约（索引替换失效）由慢路径承载，含 ORE 的快照不得走快路径。
        val snapshot = RuleSnapshot(
            version = 22L,
            rules = listOf(
                RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        assertFalse(service.usesFastPath())
    }

    @Test
    fun oreRules_shouldKeyCacheByIdentityNotOreNames() {
        // ORE 显式声明 STABLE_VIA_IDENTITY：缓存键只含身份，不含矿辞集合。
        // 生产路径矿辞集合由 OreDictIndex 按 itemId+metadata 稳定决定，同一身份不会出现不同矿辞。
        val snapshot = RuleSnapshot(
            version = 23L,
            rules = listOf(
                RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("gregtech:meta_ingot", 324, "item", oreNames = setOf("ingotSteel"))

        assertEquals(512, service.resolve(identity))
        assertEquals(512, service.resolve(context(identity, oreNames = setOf("ingotGold"))))
        assertEquals(1, service.debugResolvedCacheSize())
    }

    @Test
    fun fastPathHit_shouldReuseCachedResult() {
        // 命中路径：重复求值返回同一结果且不新增缓存条目。
        // modId（和 type）改为每次访问就地推导，单次命中会切分一次 itemId 并分配一个字符串，
        // 原先用线程分配字节探针断言“零分配”不再成立，故删除该探针。
        val snapshot = RuleSnapshot(
            version = 24L,
            rules = listOf(
                RuleCompiler.compileLine("material = steel -> 1024", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        assertTrue(service.usesFastPath())
        val hit = context("gregtech:meta_item_1", 1000, "item", material = "steel")

        assertEquals(1024, service.resolve(hit))
        repeat(10_000) { assertEquals(1024, service.resolve(hit)) }
        assertEquals(1, service.debugResolvedCacheSize())
    }

    private fun context(
        source: StackContext,
        baseLimit: Int = source.baseLimit,
        oreNames: Set<String> = source.oreNames,
        tab: String = source.tab,
        material: String = source.material,
    ): StackContext = context(
        itemId = source.itemId,
        metadata = source.metadata,
        type = source.type,
        baseLimit = baseLimit,
        oreNames = oreNames,
        tab = tab,
        material = material,
    )

    private fun context(
        itemId: String,
        metadata: Int,
        type: String = "item",
        baseLimit: Int = 64,
        oreNames: Set<String> = emptySet(),
        tab: String = "",
        material: String = "",
    ): StackContext = StackContext(
        itemId = itemId,
        metadata = metadata,
        baseLimit = baseLimit,
        directType = type,
        directOreNames = oreNames,
        directTab = tab,
        directMaterial = material,
    )
}
