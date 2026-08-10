package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevCompatProbeSelectorTest {
    @Test
    fun blankConfig_shouldDefaultToAllProbes() {
        assertEquals(emptySet<String>(), parseRequestedProbeIds(""))
        assertEquals(emptySet<String>(), parseRequestedProbeIds(" , , "))
        val availableIds = listOf("first_probe", "second_probe")
        assertEquals(availableIds, selectRequestedProbeIds(emptySet(), availableIds))
        assertEquals(emptyList<String>(), unknownProbeFailures(emptySet(), availableIds))
    }

    @Test
    fun explicitConfig_shouldDedupAndNormalize() {
        assertEquals(
            linkedSetOf("refinedstorage_storage_monitor_extract", "colossalchests_inventory_limit"),
            parseRequestedProbeIds(
                " refinedstorage_storage_monitor_extract, colossalchests_inventory_limit,refinedstorage_storage_monitor_extract ",
            ),
        )
    }

    @Test
    fun shouldSelectOnlyRequestedAndAvailable() {
        assertEquals(
            listOf("colossalchests_inventory_limit"),
            selectRequestedProbeIds(
                requestedIds = setOf("colossalchests_inventory_limit"),
                availableIds = listOf("refinedstorage_storage_monitor_extract", "colossalchests_inventory_limit"),
            ),
        )
    }

    @Test
    fun unknownRequestedIds_shouldBeReportedSeparately() {
        val requestedIds = linkedSetOf("colossalchests_inventory_limit", "missing_probe")
        val availableIds = listOf("refinedstorage_storage_extract", "colossalchests_inventory_limit")

        assertEquals(linkedSetOf("missing_probe"), unknownRequestedProbeIds(requestedIds, availableIds))
        assertEquals(listOf("unknown_probe_id: missing_probe"), unknownProbeFailures(requestedIds, availableIds))
    }
}
