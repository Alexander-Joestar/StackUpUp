package pl.asie.stackup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.asie.stackup.limit.StackUpServices
import pl.asie.stackup.rules.compile.RuleCompiler
import pl.asie.stackup.rules.compile.RuleSnapshot

class StackUpHelpersTest {
    @Test
    fun `动态兼容入口应委托给当前快照`() {
        StackUpServices.replaceSnapshot(
            RuleSnapshot(
                version = 2L,
                rules = listOf(
                    RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                    RuleCompiler.compileLine("ore = ingotSteel *= 2", 2)
                )
            )
        )

        val result = StackUpHelpers.applyDynamicStackLimit(
            itemId = "gregtech:gt.metaitem.01",
            modId = "gregtech",
            meta = 11305,
            type = "item",
            baseLimit = 64,
            oreNames = setOf("ingotSteel")
        )

        assertEquals(1024, result)
    }
}
