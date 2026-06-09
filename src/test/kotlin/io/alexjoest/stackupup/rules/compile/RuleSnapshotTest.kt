package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleSnapshotTest {
    @Test
    fun `requirements_shouldTrackTypedReferencedFieldsAndNeeds`() {
        val materialSnapshot = RuleSnapshot(
            version = 1L,
            rules = listOf(
                RuleCompiler.compileLine("material = steel -> 2048", 1),
            ),
        )
        val oreSnapshot = RuleSnapshot(
            version = 2L,
            rules = listOf(
                RuleCompiler.compileLine("ore in [ingotSteel] -> 128", 1),
            ),
        )
        val itemRule = RuleCompiler.compileLine("item = minecraft:egg -> 128", 1)
        val itemSnapshot = RuleSnapshot(version = 3L, rules = listOf(itemRule))

        assertEquals(setOf(RuleField.MATERIAL), materialSnapshot.rules.single().referencedFields)
        assertEquals(setOf(RuleField.MATERIAL), materialSnapshot.requirements.referencedFields)
        assertEquals(setOf(RuleField.MATERIAL), materialSnapshot.requirements.cacheKeyFields)
        assertEquals(true, materialSnapshot.needsMaterial)
        assertEquals(false, materialSnapshot.needsOreNames)

        assertEquals(setOf(RuleField.ORE), oreSnapshot.requirements.referencedFields)
        assertEquals(true, oreSnapshot.needsOreNames)
        assertEquals(false, oreSnapshot.needsMaterial)
        assertEquals(emptySet<RuleField>(), oreSnapshot.requirements.cacheKeyFields)

        assertEquals(setOf(RuleField.ITEM), itemRule.referencedFields)
        assertEquals(false, itemSnapshot.needsMaterial)
    }
}
