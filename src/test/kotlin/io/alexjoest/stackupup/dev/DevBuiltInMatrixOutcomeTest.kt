package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DevBuiltInMatrixOutcomeTest {
    @Test
    fun `gtUnloaded_allUnknownIsSkippable`() {
        assertNull(
            unresolvedBuiltInMatrixFailure(
                unresolvedCount = 4,
                totalCount = 4,
                gregTechLoaded = false,
            ),
        )
    }

    @Test
    fun `gtLoaded_allUnknownMustFail`() {
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
    fun `partialUnknown_shouldRetainFailureCount`() {
        assertEquals(
            "built_in_matrix: unresolved=2",
            unresolvedBuiltInMatrixFailure(
                unresolvedCount = 2,
                totalCount = 4,
                gregTechLoaded = false,
            ),
        )
    }
}
