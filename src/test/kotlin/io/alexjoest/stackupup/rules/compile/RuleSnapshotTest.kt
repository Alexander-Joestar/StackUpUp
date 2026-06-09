package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleContextRequirement
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
        assertEquals(listOf(RuleField.MATERIAL), materialSnapshot.requirements.cacheKeyFields)
        assertEquals(true, materialSnapshot.needsMaterial)
        assertEquals(false, materialSnapshot.needsOreNames)
        assertEquals(true, materialSnapshot.requirements.runtimeRequirements().requires(RuleContextRequirement.MATERIAL))
        assertEquals(false, materialSnapshot.requirements.runtimeRequirements().requires(RuleContextRequirement.ORE_NAMES))
        assertEquals(true, materialSnapshot.requires(RuleContextRequirement.MATERIAL))
        assertEquals(false, materialSnapshot.requires(RuleContextRequirement.ORE_NAMES))

        assertEquals(setOf(RuleField.ORE), oreSnapshot.requirements.referencedFields)
        assertEquals(true, oreSnapshot.needsOreNames)
        assertEquals(false, oreSnapshot.needsMaterial)
        assertEquals(true, oreSnapshot.requires(RuleContextRequirement.ORE_NAMES))
        assertEquals(false, oreSnapshot.requires(RuleContextRequirement.MATERIAL))
        assertEquals(emptyList<RuleField>(), oreSnapshot.requirements.cacheKeyFields)

        assertEquals(setOf(RuleField.ITEM), itemRule.referencedFields)
        assertEquals(false, itemSnapshot.needsMaterial)

        val tabSnapshot = RuleSnapshot(
            version = 4L,
            rules = listOf(
                RuleCompiler.compileLine("tab = buildingBlocks -> 256", 1),
            ),
        )
        assertEquals(listOf(RuleField.TAB), tabSnapshot.requirements.cacheKeyFields)
    }

    @Test
    fun `requirements_shouldMergeMixedFieldNeeds`() {
        val snapshot = RuleSnapshot(
            version = 1L,
            rules = listOf(
                RuleCompiler.compileLine("mod = gregtech && material = steel && ore = ingotSteel && tab = materials -> 2048", 1),
            ),
        )

        assertEquals(setOf(RuleField.MOD, RuleField.MATERIAL, RuleField.ORE, RuleField.TAB), snapshot.requirements.referencedFields)
        assertEquals(true, snapshot.needsMaterial)
        assertEquals(true, snapshot.needsOreNames)
        assertEquals(listOf(RuleField.MATERIAL, RuleField.TAB), snapshot.requirements.cacheKeyFields)
    }
}
