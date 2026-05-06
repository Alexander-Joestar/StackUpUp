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
            StackIdentity("gregtech:gt.metaitem.01", "gregtech", 11305, "item"),
            baseLimit = 64,
            oreNames = setOf("ingotSteel"),
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
        val identity = StackIdentity("gregtech:meta_ingot", "gregtech", 324, "item")

        assertEquals(1024, service.resolve(identity, 64, setOf("ingotSteel")))
        assertEquals(1024, service.resolve(identity, 64, setOf("ingotSteel")))
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
                StackIdentity("gregtech:gt.metaitem.01", "gregtech", 11305, "item"),
                baseLimit = 64,
                oreNames = emptySet(),
            ),
        )
        assertEquals(
            64,
            service.resolve(
                StackIdentity("gregtech:gt.metaitem.01", "gregtech", 11306, "item"),
                baseLimit = 64,
                oreNames = emptySet(),
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
                StackIdentity("gregtech:gt.metaitem.01", "gregtech", 11305, "item"),
                baseLimit = 64,
                oreNames = setOf("ingotSteel"),
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
                    StackIdentity("minecraft:egg", "minecraft", 0, "item"),
                    baseLimit = 16,
                    oreNames = emptySet(),
                ),
            )
        } finally {
            StackUpUpConfig.activeMaxStackSize = previous
        }
    }
}
