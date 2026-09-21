package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackLimitService
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DevRuleInjectorTest {
    private var previousMaxStackSize: Int = 10240
    private var previousSnapshot: RuleSnapshot = RuleSnapshot(version = 0L, rules = emptyList())

    @BeforeEach
    fun setUp() {
        previousMaxStackSize = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 10240
        previousSnapshot = RuleRuntime.currentSnapshot()
        DevRuleInjector.resetForTests()
    }

    @AfterEach
    fun restoreState() {
        StackUpUpConfig.maxStackSize = previousMaxStackSize
        RuleRuntime.replaceSnapshot(previousSnapshot)
        DevRuleInjector.resetForTests()
    }

    @Test
    fun shouldAppendDevRuleToCurrentSnapshot() {
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 1L,
                rules = listOf(
                    RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                ),
            ),
        )

        val result = DevRuleInjector.ensureInjected("ore = ingotSteel -> *2")
        val snapshot = RuleRuntime.currentSnapshot()
        val resolved = StackLimitService(snapshot).resolve(
            StackContext(
                itemId = "gregtech:meta_ingot",
                modId = "gregtech",
                metadata = 324,
                type = "item",
                baseLimit = 64,
                oreNames = setOf("ingotSteel"),
            ),
        )

        assertEquals(2, snapshot.rules.size)
        assertEquals(1024, resolved)
        assertEquals(DevRuleInjectionResult.Applied(listOf("ore = ingotSteel -> *2"), 1, 2), result)
        assertEquals("ore = ingotSteel -> *2", (result as DevRuleInjectionResult.Applied).ruleLine)
    }

    @Test
    fun sameRuleInjectedTwice_shouldNotStack() {
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 1L,
                rules = listOf(
                    RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                ),
            ),
        )

        val first = DevRuleInjector.ensureInjected("ore = ingotSteel -> 1024")
        val second = DevRuleInjector.ensureInjected("ore = ingotSteel -> 1024")

        assertEquals(DevRuleInjectionResult.Applied(listOf("ore = ingotSteel -> 1024"), 1, 2), first)
        assertEquals(DevRuleInjectionResult.Skipped, second)
        assertEquals(2, RuleRuntime.currentSnapshot().rules.size)
    }

    @Test
    fun batchInjection_shouldApplyAllDistinctRulesOnce() {
        RuleRuntime.replaceSnapshot(RuleSnapshot(version = 1L, rules = emptyList()))

        val first = DevRuleInjector.ensureInjected(listOf("ore = ingotSteel -> 1024", "item = minecraft:stick -> 512"))
        val second = DevRuleInjector.ensureInjected(listOf("ore = ingotSteel -> 1024", "item = minecraft:stick -> 512"))

        assertEquals(
            DevRuleInjectionResult.Applied(listOf("ore = ingotSteel -> 1024", "item = minecraft:stick -> 512"), 0, 2),
            first,
        )
        assertEquals(DevRuleInjectionResult.Skipped, second)
        assertEquals(2, RuleRuntime.currentSnapshot().rules.size)
    }

    @Test
    fun batchInjection_withParseError_shouldFailAndInjectNothing() {
        RuleRuntime.replaceSnapshot(RuleSnapshot(version = 1L, rules = emptyList()))

        val result = DevRuleInjector.ensureInjected(listOf("ore = ingotSteel -> 1024", "这不是合法规则"))

        assertEquals(true, result is DevRuleInjectionResult.Failed)
        assertEquals(0, RuleRuntime.currentSnapshot().rules.size)
    }

    @Test
    fun blankRule_shouldBeSkipped() {
        RuleRuntime.replaceSnapshot(RuleSnapshot(version = 1L, rules = emptyList()))

        assertEquals(DevRuleInjectionResult.Skipped, DevRuleInjector.ensureInjected(""))
        assertEquals(DevRuleInjectionResult.Skipped, DevRuleInjector.ensureInjected(listOf("", "  ")))
    }
}
