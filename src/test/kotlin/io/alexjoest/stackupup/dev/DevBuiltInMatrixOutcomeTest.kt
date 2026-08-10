package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevBuiltInMatrixOutcomeTest {
    @Test
    fun gtUnloaded_allUnknownIsSkippable() {
        assertNull(
            unresolvedBuiltInMatrixFailure(
                unresolvedCount = 4,
                totalCount = 4,
                gregTechLoaded = false,
            ),
        )
    }

    @Test
    fun gtLoaded_allUnknownMustFail() {
        assertEquals(
            "built_in_matrix: all targets unresolved while gregtech is loaded",
            unresolvedBuiltInMatrixFailure(
                unresolvedCount = 4,
                totalCount = 4,
                gregTechLoaded = true,
            ),
        )
    }

    @Test
    fun partialUnknown_shouldRetainFailureCount() {
        assertEquals(
            "built_in_matrix: unresolved=2",
            unresolvedBuiltInMatrixFailure(
                unresolvedCount = 2,
                totalCount = 4,
                gregTechLoaded = false,
            ),
        )
    }

    @Test
    fun builtInMatrix_shouldCarryScenarioRules() {
        val specs = DevAutomationConfig.builtInMatrix

        assertEquals(listOf("IngotSteel", "PlateSteel", "DustSteel", "VacuumTube"), specs.map { it.name })
        assertTrue(specs.all { !it.rule.isNullOrBlank() })
        assertEquals("ore = ingotSteel -> 1024", specs[0].rule)
        assertEquals("ore = plateSteel -> 1024", specs[1].rule)
        assertEquals("ore = dustSteel -> 1024", specs[2].rule)
        assertEquals("item = gregtech:meta_item_1 && meta = 516 -> 512", specs[3].rule)
    }

    @Test
    fun matrixTargetFailure_shouldContinueWithLaterTargets() {
        val specs = listOf(
            DevProbeTargetSpec(name = "broken"),
            DevProbeTargetSpec(name = "later"),
        )
        val evaluated = ArrayList<String>()

        val results = DevAutomationServerDriver.evaluateMatrixTargets(specs) { spec ->
            evaluated += spec.name
            if (spec.name == "broken") {
                error("target exploded")
            }
            DevProbeRunResult(passed = true, summary = "ok")
        }

        assertEquals(listOf("broken", "later"), evaluated)
        assertFalse(results[0].passed)
        assertEquals("执行异常：IllegalStateException: target exploded", results[0].summary)
        assertTrue(results[1].passed)
    }
}
