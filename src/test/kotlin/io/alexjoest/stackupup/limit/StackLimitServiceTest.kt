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
import java.lang.management.ManagementFactory

class StackLimitServiceTest {
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

    @Test
    fun `shouldExecuteRulesInFileOrder`() {
        val snapshot = RuleSnapshot(
            version = 1L,
            rules = listOf(
                RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                RuleCompiler.compileLine("ore = ingotSteel -> *2", 2),
            ),
        )
        val service = StackLimitService(snapshot)
        val result = service.resolve(
            context("gregtech:gt.metaitem.01", "gregtech", 11305, "item", oreNames = setOf("ingotSteel")),
        )
        assertEquals(1024, result)
    }

    @Test
    fun `sameContext_shouldHitCache`() {
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
        val identity = context("gregtech:meta_ingot", "gregtech", 324, "item", oreNames = setOf("ingotSteel"))

        assertEquals(1024, service.resolve(identity))
        assertEquals(1024, service.resolve(identity))
        assertEquals(1, evaluations)
        assertEquals(1, service.debugResolvedCacheSize())
    }

    @Test
    fun `shouldSupportItemWithMetadataSugar`() {
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
                context("gregtech:gt.metaitem.01", "gregtech", 11305, "item"),
            ),
        )
        assertEquals(
            64,
            service.resolve(
                context("gregtech:gt.metaitem.01", "gregtech", 11306, "item"),
            ),
        )
    }

    @Test
    fun `shouldExecuteActionChainInOrder`() {
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
                context("gregtech:gt.metaitem.01", "gregtech", 11305, "item", oreNames = setOf("ingotSteel")),
            ),
        )
    }

    @Test
    fun `runtimeResult_shouldBeClampedByMaxStackSize`() {
        val previous = StackUpUpConfig.activeMaxStackSize
        StackUpUpConfig.general.maxStackSize = 256
        StackUpUpConfig.activeMaxStackSize = 256
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
                    context("minecraft:egg", "minecraft", 0, "item", baseLimit = 16),
                ),
            )
        } finally {
            StackUpUpConfig.activeMaxStackSize = previous
        }
    }

    @Test
    fun `emptySnapshot_shouldClampBaseLimitWithoutCaching`() {
        StackUpUpConfig.general.maxStackSize = 128
        StackUpUpConfig.activeMaxStackSize = 128
        val service = StackLimitService(RuleSnapshot(version = 10L, rules = emptyList()))
        val identity = context("minecraft:egg", "minecraft", 0, "item", baseLimit = 16)

        assertEquals(128, service.resolve(identity.copy(baseLimit = 999, tab = "ignored", material = "ignored")))
        assertEquals(1, service.resolve(identity.copy(baseLimit = 0, tab = "ignored", material = "ignored")))
        assertEquals(0, service.debugResolvedCacheSize())
    }

    @Test
    fun `materialDependentRules_shouldPartitionResolvedCacheByMaterial`() {
        val snapshot = RuleSnapshot(
            version = 6L,
            rules = listOf(
                RuleCompiler.compileLine("material = steel -> 1024", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("gregtech:meta_item_1", "gregtech", 1000, "item")

        assertEquals(1024, service.resolve(identity.copy(material = "steel")))
        assertEquals(64, service.resolve(identity.copy(material = "copper")))
        assertEquals(2, service.debugResolvedCacheSize())
    }

    @Test
    fun `materialIndependentRules_shouldNotPartitionResolvedCacheByMaterial`() {
        val snapshot = RuleSnapshot(
            version = 7L,
            rules = listOf(
                RuleCompiler.compileLine("item = gregtech:meta_item_1 -> 512", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("gregtech:meta_item_1", "gregtech", 1000, "item")

        assertEquals(512, service.resolve(identity.copy(material = "steel")))
        assertEquals(512, service.resolve(identity.copy(material = "copper")))
        assertEquals(1, service.debugResolvedCacheSize())
    }

    @Test
    fun `tabDependentRules_shouldPartitionResolvedCacheByTab`() {
        val snapshot = RuleSnapshot(
            version = 8L,
            rules = listOf(
                RuleCompiler.compileLine("tab = buildingBlocks -> 256", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("minecraft:stone", "minecraft", 0, "block")

        assertEquals(256, service.resolve(identity.copy(tab = "buildingBlocks")))
        assertEquals(64, service.resolve(identity.copy(tab = "materials")))
        assertEquals(2, service.debugResolvedCacheSize())
    }

    @Test
    fun `mixedDynamicFields_shouldPartitionResolvedCacheByDeclaredFields`() {
        val snapshot = RuleSnapshot(
            version = 9L,
            rules = listOf(
                RuleCompiler.compileLine("material = steel && tab = materials -> 1024", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("gregtech:meta_item_1", "gregtech", 1000, "item")

        assertEquals(1024, service.resolve(identity.copy(tab = "materials", material = "steel")))
        assertEquals(64, service.resolve(identity.copy(tab = "materials", material = "copper")))
        assertEquals(64, service.resolve(identity.copy(tab = "tools", material = "steel")))
        assertEquals(3, service.debugResolvedCacheSize())
    }

    // ---- T6：缓存键机械推导与零分配快路径 ----

    @Test
    fun `contextsDifferingOnlyInReadField_shouldNotShareCacheEntries`() {
        // 规则读取 tab，tab 必须进入字段缓存键：只差 tab 的两个上下文不得共享条目。
        val snapshot = RuleSnapshot(
            version = 20L,
            rules = listOf(
                RuleCompiler.compileLine("tab = buildingBlocks -> 256", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val base = context("minecraft:stone", "minecraft", 0, "block", tab = "buildingBlocks")

        assertEquals(256, service.resolve(base))
        assertEquals(64, service.resolve(base.copy(tab = "materials")))
        assertEquals(2, service.debugResolvedCacheSize())
    }

    @Test
    fun `fastAndSlowPath_shouldResolveIdentically`() {
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
            context("gregtech:meta_item_1", "gregtech", 1000, "item", material = "steel"),
            context("gregtech:meta_item_1", "gregtech", 1000, "item", material = "copper"),
            context("gregtech:meta_item_1", "gregtech", 1001, "item", material = "steel"),
            context("minecraft:egg", "minecraft", 0, "item", material = ""),
        )
        for (case in cases) {
            assertEquals(fast.resolve(case), slow.resolve(case), "context=$case")
        }
    }

    @Test
    fun `oreRules_shouldNotUseFastPath`() {
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
    fun `oreRules_shouldKeyCacheByIdentityNotOreNames`() {
        // ORE 显式声明 STABLE_VIA_IDENTITY：缓存键只含身份，不含矿辞集合。
        // 生产路径矿辞集合由 OreDictIndex 按 itemId+metadata 稳定决定，同一身份不会出现不同矿辞。
        val snapshot = RuleSnapshot(
            version = 23L,
            rules = listOf(
                RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        val identity = context("gregtech:meta_ingot", "gregtech", 324, "item", oreNames = setOf("ingotSteel"))

        assertEquals(512, service.resolve(identity))
        assertEquals(512, service.resolve(identity.copy(oreNames = setOf("ingotGold"))))
        assertEquals(1, service.debugResolvedCacheSize())
    }

    @Test
    fun `fastPathHit_shouldNotAllocateObjects`() {
        // 命中路径零分配：用线程分配字节计数验证快路径命中不构造任何中间对象。
        val snapshot = RuleSnapshot(
            version = 24L,
            rules = listOf(
                RuleCompiler.compileLine("material = steel -> 1024", 1),
            ),
        )
        val service = StackLimitService(snapshot)
        assertTrue(service.usesFastPath())
        val hit = context("gregtech:meta_item_1", "gregtech", 1000, "item", material = "steel")

        // 预热：填充缓存条目并触发 JIT 编译
        repeat(50_000) { service.resolve(hit) }

        val bean = ManagementFactory.getThreadMXBean()
        if (bean !is com.sun.management.ThreadMXBean || !bean.isThreadAllocatedMemorySupported) {
            return // 环境不支持线程分配计数，跳过断言（无法测量）
        }
        val threadId = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(threadId)
        repeat(50_000) { service.resolve(hit) }
        val allocated = bean.getThreadAllocatedBytes(threadId) - before
        assertEquals(0, allocated, "快路径命中不应分配任何对象，实际分配 $allocated 字节")
    }

    private fun context(
        itemId: String,
        modId: String,
        metadata: Int,
        type: String,
        baseLimit: Int = 64,
        oreNames: Set<String> = emptySet(),
        tab: String = "",
        material: String = "",
    ): StackContext = StackContext(
        itemId = itemId,
        modId = modId,
        metadata = metadata,
        type = type,
        baseLimit = baseLimit,
        oreNames = oreNames,
        tab = tab,
        material = material,
    )
}
