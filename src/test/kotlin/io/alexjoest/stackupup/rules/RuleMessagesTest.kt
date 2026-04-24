package io.alexjoest.stackupup.rules

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleMessagesTest {
    @Test
    fun `规则错误消息应支持英文本地化`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            assertEquals(
                "Unsupported field: mystery",
                RuleMessages.unsupportedField("mystery")
            )
            assertEquals(
                "[pack.su] Line 7 failed to load: broken",
                RuleMessages.loadFailed(7, "pack.su", "broken")
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
