package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class DevAe2ProbeRoutingTest {
    @Test
    fun `AE2 focuses use deferred adapters and do not join default probes`() {
        for (focus in listOf(AE2_OWNER_OFF_FOCUS, AE2_OWNER_BLACKLIST_FOCUS)) {
            validateFocusedRequest(focus, false, emptySet())
            val probe = focusedProbe(focus)
            assertEquals(focus, probe.id)
            assertTrue(probe is DevAe2ProbeAdapter)
            assertTrue(probe is DevDeferredFocusedProbe)
            assertFalse(focus in DevCompatProbeRunner.probeIds())
            assertThrows(IllegalArgumentException::class.java) { validateFocusedRequest(focus, true, emptySet()) }
            assertThrows(IllegalArgumentException::class.java) { validateFocusedRequest(focus, false, setOf("inv_wrapper_limit")) }
        }
        assertThrows(IllegalStateException::class.java) { focusedProbe("ae2_owner_typo") }
    }

    @Test
    fun `missing AE2 runtime is a failure rather than skip`() {
        requireAe2ProbeRuntime(true)
        assertThrows(IllegalStateException::class.java) { requireAe2ProbeRuntime(false) }
    }

    @Test
    fun `catalog and adapter do not statically reference AE2 types`() {
        val root = Paths.get("src/main/kotlin/io/alexjoest/stackupup/dev")
        for (name in listOf("DevFocusedProbeCatalog.kt", "DevAe2ProbeAdapter.kt")) {
            val source = String(Files.readAllBytes(root.resolve(name)), Charsets.UTF_8)
            assertFalse(source.contains("appeng."), name)
        }
    }
}
