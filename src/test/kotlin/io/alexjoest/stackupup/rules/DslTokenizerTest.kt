package io.alexjoest.stackupup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.rules.parse.DslTokenType
import io.alexjoest.stackupup.rules.parse.DslTokenizer

class DslTokenizerTest {
    @Test
    fun `应当识别 item 与 metadata 规则`() {
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
                DslTokenType.EOF
            ),
            tokens.map { it.type }
        )
    }

    @Test
    fun `应当统一识别全部核心运算符`() {
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
                DslTokenType.EOF
            ),
            tokens.map { it.type }
        )
    }
}

