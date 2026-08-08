package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.LocalizedMessages
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.parse.DslParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T7.2：item 字面量在 tokenizer/AST 阶段一次定型的行为测试。
 *
 * 覆盖：带引号 literal、`@*`、精确 meta、`@abc`/`@-1` fail-fast（列号与原因）、
 * `minecraft:wool:*` 保持 path 通配、多冒号 path 保持原值（不把第三段当 meta）。
 */
class RuleLiteralMatcherTest {
    // ---- 引号：只改变词法边界，不改变值 ----

    @Test
    fun `quotedLiteral_shouldMatchSameValueAsUnquoted`() {
        val compiled = RuleCompiler.compileLine("item = \"minecraft:wool\" -> 128", 1)

        assertTrue(compiled.matches(ctx("minecraft:wool", meta = 0)))
        assertTrue(compiled.matches(ctx("minecraft:wool", meta = 14)))
        assertFalse(compiled.matches(ctx("minecraft:egg")))
    }

    @Test
    fun `quotedLiteralInList_shouldMatchEachValue`() {
        val compiled = RuleCompiler.compileLine("item in [\"minecraft:egg\", \"minecraft:snowball\"] -> 128", 1)

        assertTrue(compiled.matches(ctx("minecraft:egg")))
        assertTrue(compiled.matches(ctx("minecraft:snowball")))
        assertFalse(compiled.matches(ctx("minecraft:wool")))
    }

    @Test
    fun `quotedLiteral_shouldKeepAtMetaSyntax`() {
        val compiled = RuleCompiler.compileLine("item = \"minecraft:wool@14\" -> 128", 1)

        assertTrue(compiled.matches(ctx("minecraft:wool", meta = 14)))
        assertFalse(compiled.matches(ctx("minecraft:wool", meta = 5)))
    }

    // ---- @meta：@整数 精确、@* 任意 ----

    @Test
    fun `exactMeta_shouldMatchOnlyThatMetadata`() {
        val compiled = RuleCompiler.compileLine("item = minecraft:wool@14 -> 128", 1)

        assertTrue(compiled.matches(ctx("minecraft:wool", meta = 14)))
        assertFalse(compiled.matches(ctx("minecraft:wool", meta = 15)))
        assertFalse(compiled.matches(ctx("minecraft:egg", meta = 14)))
    }

    @Test
    fun `atStar_shouldMatchAnyMetadata`() {
        val compiled = RuleCompiler.compileLine("item = minecraft:wool@* -> 128", 1)

        assertTrue(compiled.matches(ctx("minecraft:wool", meta = 0)))
        assertTrue(compiled.matches(ctx("minecraft:wool", meta = 14)))
        assertFalse(compiled.matches(ctx("minecraft:egg", meta = 14)))
    }

    // ---- 多冒号：按 1.12.2 ResourceLocation 保持原值，不把第三段当 meta ----

    @Test
    fun `multiColonPath_shouldStayFullLiteralId`() {
        // 1.12.2 ResourceLocation.splitObjectName 只按第一个冒号分割（indexOf(58)），
        // path 可为 'gt.metaitem.01:11305'，末段不得被当作 meta。
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01:11305 -> 128", 1)

        assertTrue(compiled.matches(ctx("gregtech:gt.metaitem.01:11305", meta = 7)))
        assertFalse(compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 11305)))
        assertFalse(compiled.matches(ctx("gregtech:gt.metaitem.01:11306", meta = 7)))
    }

    @Test
    fun `colonStar_shouldStayPathWildcardNotMetaWildcard`() {
        // minecraft:wool:* 是 path 含 '*' 的 pattern（匹配 minecraft:wool:... 的 id），
        // 不是 minecraft:wool 加任意 meta，也不是永不命中的整串精确匹配。
        val compiled = RuleCompiler.compileLine("item = minecraft:wool:* -> 128", 1)

        assertTrue(compiled.matches(ctx("minecraft:wool:custom")))
        assertFalse(compiled.matches(ctx("minecraft:wool")))
        assertFalse(compiled.matches(ctx("minecraft:wool", meta = 14)))
    }

    // ---- fail-fast：错误指向正确列与原因 ----

    @Test
    fun `invalidMeta_shouldFailFastWithColumnAndReason`() {
        LocalizedMessages.setLanguage(LocalizedMessages.DEFAULT_LANGUAGE_CODE)
        try {
            val error = assertThrows(LocalizedRuleException::class.java) {
                DslParser.parseLine("item = minecraft:wool@abc -> 128")
            }

            assertEquals(RuleMessageKey.INVALID_ITEM_LITERAL.translationKey, error.messageData.translationKey)
            // 字面量从第 8 列开始，'@' 在字面量内偏移 14，绝对列 22
            assertEquals(listOf("minecraft:wool@abc", 22), error.messageData.args.take(2))
            val reason = error.messageData.args[2] as LocalizedMessage
            assertEquals(RuleMessageKey.ITEM_META_NOT_INTEGER.translationKey, reason.translationKey)
            assertTrue(error.message.orEmpty().contains("column 22"))
        } finally {
            LocalizedMessages.setLanguage(LocalizedMessages.DEFAULT_LANGUAGE_CODE)
        }
    }

    @Test
    fun `negativeMeta_shouldFailFastWithNegativeReason`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            DslParser.parseLine("item = minecraft:wool@-1 -> 128")
        }

        assertEquals(RuleMessageKey.INVALID_ITEM_LITERAL.translationKey, error.messageData.translationKey)
        val reason = error.messageData.args[2] as LocalizedMessage
        assertEquals(RuleMessageKey.ITEM_META_NEGATIVE.translationKey, reason.translationKey)
        assertEquals(22, error.messageData.args[1])
    }

    @Test
    fun `emptyItemIdBeforeAt_shouldFailFast`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            DslParser.parseLine("item = @14 -> 128")
        }

        assertEquals(RuleMessageKey.INVALID_ITEM_LITERAL.translationKey, error.messageData.translationKey)
        assertEquals(8, error.messageData.args[1])
        val reason = error.messageData.args[2] as LocalizedMessage
        assertEquals(RuleMessageKey.ITEM_META_MISSING_ITEM_ID.translationKey, reason.translationKey)
    }

    @Test
    fun `invalidMetaInList_shouldFailFastWithColumn`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            DslParser.parseLine("item in [minecraft:egg, minecraft:wool@abc] -> 128")
        }

        // 'minecraft:wool@abc' 从第 25 列开始，'@' 在字面量内偏移 14，绝对列 39
        assertEquals(39, error.messageData.args[1])
        val reason = error.messageData.args[2] as LocalizedMessage
        assertEquals(RuleMessageKey.ITEM_META_NOT_INTEGER.translationKey, reason.translationKey)
    }

    @Test
    fun `unterminatedQuote_shouldFailFastWithColumn`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            DslParser.parseLine("item = \"minecraft:wool -> 128")
        }

        assertEquals(RuleMessageKey.UNTERMINATED_QUOTED_LITERAL.translationKey, error.messageData.translationKey)
        assertEquals(listOf(8), error.messageData.args)
    }

    @Test
    fun `emptyQuote_shouldFailFastWithColumn`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            DslParser.parseLine("item = \"\" -> 128")
        }

        assertEquals(RuleMessageKey.EMPTY_QUOTED_LITERAL.translationKey, error.messageData.translationKey)
        assertEquals(listOf(8), error.messageData.args)
    }

    private fun ctx(itemId: String, meta: Int = 0, modId: String = itemId.substringBefore(':'), baseSize: Int = 64) = StackContext(
        itemId = itemId,
        modId = modId,
        metadata = meta,
        type = "item",
        baseLimit = baseSize,
        oreNames = emptySet(),
        tab = "",
        material = "",
    )
}
