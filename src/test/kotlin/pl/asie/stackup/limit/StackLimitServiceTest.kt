package pl.asie.stackup.limit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.asie.stackup.rules.compile.RuleCompiler
import pl.asie.stackup.rules.compile.RuleSnapshot

class StackLimitServiceTest {
    @Test
    fun `应当按文件顺序执行规则`() {
        val snapshot = RuleSnapshot(
            version = 1L,
            rules = listOf(
                RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                RuleCompiler.compileLine("ore = ingotSteel *= 2", 2)
            )
        )
        val service = StackLimitService(snapshot)
        val result = service.resolve(
            StackIdentity("gregtech:gt.metaitem.01", "gregtech", 11305, "item"),
            baseLimit = 64,
            oreNames = setOf("ingotSteel")
        )
        assertEquals(1024, result)
    }
}
