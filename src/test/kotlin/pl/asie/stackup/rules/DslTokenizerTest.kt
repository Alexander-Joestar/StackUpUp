package pl.asie.stackup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.asie.stackup.rules.parse.DslTokenType
import pl.asie.stackup.rules.parse.DslTokenizer

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
}
