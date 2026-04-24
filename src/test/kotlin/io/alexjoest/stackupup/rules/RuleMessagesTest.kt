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
                RuleMessages.format(RuleMessageKey.UNSUPPORTED_FIELD, "mystery")
            )
            assertEquals(
                "[pack.su] Line 7 failed to load: broken",
                RuleMessages.format(RuleMessageKey.LOAD_FAILED_WITH_SOURCE, "pack.su", 7, "broken")
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
