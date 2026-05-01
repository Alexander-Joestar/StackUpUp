package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevCompatProbeSelectorTest {
    @Test
    fun `blankConfig_shouldDefaultToAllProbes`() {
        assertEquals(emptySet<String>(), parseRequestedProbeIds(""))
        assertEquals(emptySet<String>(), parseRequestedProbeIds(" , , "))
    }

    @Test
    fun `explicitConfig_shouldDedupAndNormalize`() {
        assertEquals(
            linkedSetOf("refinedstorage_storage_monitor_extract", "colossalchests_inventory_limit"),
            parseRequestedProbeIds(
                " refinedstorage_storage_monitor_extract, colossalchests_inventory_limit,refinedstorage_storage_monitor_extract ",
            ),
        )
    }

    @Test
    fun `shouldSelectOnlyRequestedAndAvailable`() {
        assertEquals(
            listOf("colossalchests_inventory_limit"),
            selectRequestedProbeIds(
                requestedIds = setOf("colossalchests_inventory_limit"),
                availableIds = listOf("refinedstorage_storage_monitor_extract", "colossalchests_inventory_limit"),
            ),
        )
    }
}
