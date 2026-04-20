package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevCompatProbeSelectorTest {
    @Test
    fun `空白配置应回退为全部探针`() {
        assertEquals(emptySet<String>(), parseRequestedProbeIds(""))
        assertEquals(emptySet<String>(), parseRequestedProbeIds(" , , "))
    }

    @Test
    fun `显式配置应去重并归一化`() {
        assertEquals(
            linkedSetOf("refinedstorage_storage_monitor_extract", "colossalchests_inventory_limit"),
            parseRequestedProbeIds(
                " refinedstorage_storage_monitor_extract, colossalchests_inventory_limit,refinedstorage_storage_monitor_extract "
            )
        )
    }

    @Test
    fun `应只选择存在且被请求的探针`() {
        assertEquals(
            listOf("colossalchests_inventory_limit"),
            selectRequestedProbeIds(
                requestedIds = setOf("colossalchests_inventory_limit"),
                availableIds = listOf("refinedstorage_storage_monitor_extract", "colossalchests_inventory_limit")
            )
        )
    }
}

