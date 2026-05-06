package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackIdentity
import io.alexjoest.stackupup.limit.StackLimitService
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DevRuleInjectorTest {
    private var previousMaxStackSize: Int = 10240

    @BeforeEach
    fun setUpMaxStackSize() {
        previousMaxStackSize = StackUpUpConfig.activeMaxStackSize
        StackUpUpConfig.general.maxStackSize = 10240
        StackUpUpConfig.activeMaxStackSize = 10240
    }

    @AfterEach
    fun restoreMaxStackSize() {
        StackUpUpConfig.general.maxStackSize = previousMaxStackSize
        StackUpUpConfig.activeMaxStackSize = previousMaxStackSize
    }

    @Test
    fun `shouldAppendDevRuleToCurrentSnapshot`() {
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
            StackIdentity("gregtech:meta_ingot", "gregtech", 324, "item"),
            baseLimit = 64,
            oreNames = setOf("ingotSteel"),
        )

        assertEquals(2, snapshot.rules.size)
        assertEquals(1024, resolved)
        assertEquals(DevRuleInjectionResult.Applied("ore = ingotSteel -> *2", 1, 2), result)
    }
}
