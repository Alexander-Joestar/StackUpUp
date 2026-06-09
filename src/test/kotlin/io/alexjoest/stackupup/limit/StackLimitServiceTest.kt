package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
                    compiled.copy(
                        predicate = { context ->
                            evaluations++
                            compiled.matches(context)
                        },
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
                RuleCompiler.compileLine("item = gregtech:gt.metaitem.01:11305 -> 1024", 1),
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
