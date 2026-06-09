package io.alexjoest.stackupup.rules.compile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleSnapshotTest {
    @Test
    fun `needsMaterial_shouldTrackMaterialReferencedRules`() {
        val materialSnapshot = RuleSnapshot(
            version = 1L,
            rules = listOf(
                RuleCompiler.compileLine("material = steel -> 2048", 1),
            ),
        )
        val itemSnapshot = RuleSnapshot(
            version = 2L,
            rules = listOf(
                RuleCompiler.compileLine("item = minecraft:egg -> 128", 1),
            ),
        )

        assertEquals(true, materialSnapshot.needsMaterial)
        assertEquals(false, itemSnapshot.needsMaterial)
    }
}
