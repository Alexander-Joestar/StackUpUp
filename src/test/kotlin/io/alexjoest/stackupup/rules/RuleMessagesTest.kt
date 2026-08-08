package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.LocalizedMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

class RuleMessagesTest {
    @BeforeEach
    fun initializeLocalizedMessages() {
        // 显式初始化自有译表并锁定英文，避免与其他测试的 setLanguage 互相泄漏。
        LocalizedMessages.initialize()
        LocalizedMessages.setLanguage("en_us")
    }

    @Test
    fun `errorMessage_shouldSupportEnglishLocale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            assertEquals(
                "Unsupported field: mystery",
                RuleMessages.message(RuleMessageKey.UNSUPPORTED_FIELD, "mystery").format(),
            )
            assertEquals(
                "[pack.su] Line 7 failed to load: broken",
                RuleMessages.message(RuleMessageKey.LOAD_FAILED_WITH_SOURCE, "pack.su", 7, "broken").format(),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
