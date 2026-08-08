package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.rules.parse.DslTokenType
import io.alexjoest.stackupup.rules.parse.DslTokenizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DslTokenizerTest {
    @Test
    fun `shouldRecognizeItemAndMetadata`() {
        val tokens = DslTokenizer.tokenize("item = gregtech:gt.metaitem.01 && meta = 11305 -> 512")
        assertEquals(
            listOf(
                DslTokenType.IDENTIFIER,
                DslTokenType.EQUALS,
                DslTokenType.IDENTIFIER,
                DslTokenType.AND_AND,
                DslTokenType.IDENTIFIER,
                DslTokenType.EQUALS,
                DslTokenType.NUMBER,
                DslTokenType.ARROW,
                DslTokenType.NUMBER,
                DslTokenType.EOF,
            ),
            tokens.map { it.type },
        )
    }

    @Test
    fun `shouldRecognizeAllCoreOperators`() {
        val tokens = DslTokenizer.tokenize("size >= 2 && size <= 64 || meta != 1 -> +4 -> -3 -> *2 -> /1 item in [a, b]")
        assertEquals(
            listOf(
                DslTokenType.IDENTIFIER,
                DslTokenType.GREATER_EQUALS,
                DslTokenType.NUMBER,
                DslTokenType.AND_AND,
                DslTokenType.IDENTIFIER,
                DslTokenType.LESS_EQUALS,
                DslTokenType.NUMBER,
                DslTokenType.OR_OR,
                DslTokenType.IDENTIFIER,
                DslTokenType.NOT_EQUALS,
                DslTokenType.NUMBER,
                DslTokenType.ARROW,
                DslTokenType.PLUS,
                DslTokenType.NUMBER,
                DslTokenType.ARROW,
                DslTokenType.MINUS,
                DslTokenType.NUMBER,
                DslTokenType.ARROW,
                DslTokenType.STAR,
                DslTokenType.NUMBER,
                DslTokenType.ARROW,
                DslTokenType.SLASH,
                DslTokenType.NUMBER,
                DslTokenType.IDENTIFIER,
                DslTokenType.IN,
                DslTokenType.LEFT_BRACKET,
                DslTokenType.IDENTIFIER,
                DslTokenType.COMMA,
                DslTokenType.IDENTIFIER,
                DslTokenType.RIGHT_BRACKET,
                DslTokenType.EOF,
            ),
            tokens.map { it.type },
        )
    }

    @Test
    fun `quotedLiteral_shouldStripQuotesAndKeepValueAndColumn`() {
        val tokens = DslTokenizer.tokenize("item = \"minecraft:wool@14\" -> 128")

        assertEquals(
            listOf(
                DslTokenType.IDENTIFIER,
                DslTokenType.EQUALS,
                DslTokenType.IDENTIFIER,
                DslTokenType.ARROW,
                DslTokenType.NUMBER,
                DslTokenType.EOF,
            ),
            tokens.map { it.type },
        )
        assertEquals("minecraft:wool@14", tokens[2].lexeme)
        assertEquals(8, tokens[2].column)
        assertEquals(31, tokens[4].column)
    }

    @Test
    fun `quotedSectionMidValue_shouldAppendContentWithoutQuotes`() {
        val tokens = DslTokenizer.tokenize("item = minecraft:\"wool@14\" -> 128")

        assertEquals("minecraft:wool@14", tokens[2].lexeme)
        assertEquals(8, tokens[2].column)
    }

    @Test
    fun `unterminatedQuote_shouldFailFastWithOpeningQuoteColumn`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            DslTokenizer.tokenize("item = \"minecraft:wool")
        }

        assertEquals(RuleMessageKey.UNTERMINATED_QUOTED_LITERAL.translationKey, error.messageData.translationKey)
        assertEquals(listOf(8), error.messageData.args)
    }

    @Test
    fun `emptyQuote_shouldFailFastWithOpeningQuoteColumn`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            DslTokenizer.tokenize("item = \"\" -> 128")
        }

        assertEquals(RuleMessageKey.EMPTY_QUOTED_LITERAL.translationKey, error.messageData.translationKey)
        assertEquals(listOf(8), error.messageData.args)
    }
}
