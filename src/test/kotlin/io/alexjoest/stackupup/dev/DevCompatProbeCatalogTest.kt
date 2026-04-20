package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevCompatProbeCatalogTest {
    @Test
    fun `固定兼容探针应纳入默认探针目录`() {
        val probeIds = DevCompatProbeRunner.probeIds()
        assertEquals(expectedFixedTargetProbeCoverage(), DevCompatProbeRunner.fixedTargetCoverage())
        assertTrue("cyclopscore_simple_inventory_limit" in probeIds)
        assertTrue("combined_inv_wrapper_limit" in probeIds)
        assertTrue("ranged_wrapper_limit" in probeIds)
        assertTrue("refinedstorage_portable_grid_extract" in probeIds)
        assertTrue("slot_item_handler_limit" in probeIds)
        assertTrue("inv_wrapper_limit" in probeIds)
        assertTrue("sided_inv_wrapper_limit" in probeIds)
    }
}
