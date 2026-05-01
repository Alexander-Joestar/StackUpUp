package io.alexjoest.stackupup

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemainderGuardTest {
    @Test
    fun `guard is enabled by default`() {
        assertTrue(RemainderGuard.enabled)
    }

    @Test
    fun `withoutRemainder disables guard inside block`() {
        var insideBlock = false
        RemainderGuard.withoutRemainder {
            insideBlock = true
            assertFalse(RemainderGuard.enabled)
        }
        assertTrue(insideBlock)
    }

    @Test
    fun `guard re-enables after withoutRemainder block`() {
        RemainderGuard.withoutRemainder {
            // guard disabled inside
        }
        assertTrue(RemainderGuard.enabled)
    }

    @Test
    fun `nested withoutRemainder restores correctly`() {
        RemainderGuard.withoutRemainder {
            RemainderGuard.withoutRemainder {
                assertFalse(RemainderGuard.enabled)
            }
            // Still disabled after inner block exits
            assertFalse(RemainderGuard.enabled)
        }
        assertTrue(RemainderGuard.enabled)
    }
}
