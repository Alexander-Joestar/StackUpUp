package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.limit.StackIdentity
import io.alexjoest.stackupup.limit.StackLimitService
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot

class DevRuleInjectorTest {
    @Test
    fun `应当把开发规则追加到当前快照末尾`() {
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 1L,
                rules = listOf(
                    RuleCompiler.compileLine("ore = ingotSteel -> 512", 1)
                )
            )
        )

        val result = DevRuleInjector.ensureInjected("ore = ingotSteel -> *2")
        val snapshot = RuleRuntime.currentSnapshot()
        val resolved = StackLimitService(snapshot).resolve(
            StackIdentity("gregtech:meta_ingot", "gregtech", 324, "item"),
            baseLimit = 64,
            oreNames = setOf("ingotSteel")
        )

        assertEquals(2, snapshot.rules.size)
        assertEquals(1024, resolved)
        assertEquals(DevRuleInjectionResult.Applied("ore = ingotSteel -> *2", 1, 2), result)
    }
}


