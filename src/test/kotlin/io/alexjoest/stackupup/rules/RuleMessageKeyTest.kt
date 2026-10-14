package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.LocalizedMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RuleMessageKeyTest {
    @BeforeEach
    fun initializeLocalizedMessages() {
        LocalizedMessages.initialize()
        LocalizedMessages.setLanguage("en_us")
    }

    @Test
    fun inspectKeys_shouldBeOwnedAndFormatUsefulArguments() {
        assertEquals(RuleMessageKey.COMMAND_INSPECT_OBJECT, RuleMessageKey.fromTranslationKey("commands.stackupup.inspect.object"))
        assertEquals(
            "Object #2 Slot: example.Store@123; Forge owned-package modId candidates: UNKNOWN []",
            LocalizedMessages.format(RuleMessageKey.COMMAND_INSPECT_OBJECT, 2, "Slot", "example.Store@123", "UNKNOWN []"),
        )
    }
}
