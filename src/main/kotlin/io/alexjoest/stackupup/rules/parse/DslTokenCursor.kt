package io.alexjoest.stackupup.rules.parse

internal class DslTokenCursor(
    private val tokens: List<DslToken>
) {
    private var index: Int = 0

    fun mark(): Int = index

    fun restore(mark: Int) {
        index = mark
    }

    fun match(type: DslTokenType): Boolean {
        if (peek().type != type) {
            return false
        }
        index++
        return true
    }

    fun consume(type: DslTokenType, message: String): DslToken {
        val token = peek()
        require(token.type == type) { message }
        index++
        return token
    }

    fun tryConsume(type: DslTokenType): DslToken? {
        if (peek().type != type) {
            return null
        }
        index++
        return tokens[index - 1]
    }

    fun consumeLiteral(message: String): String {
        val token = peek()
        require(token.type == DslTokenType.IDENTIFIER || token.type == DslTokenType.NUMBER) { message }
        index++
        return token.lexeme
    }

    fun tryConsumeLiteral(): String? {
        val token = peek()
        if (token.type != DslTokenType.IDENTIFIER && token.type != DslTokenType.NUMBER) {
            return null
        }
        index++
        return token.lexeme
    }

    fun consumeComparisonOperator(): DslToken {
        val token = peek()
        require(token.type in DslTokenType.comparisonOperators) { "条件缺少比较运算符" }
        index++
        return token
    }

    fun tryConsumeComparisonOperator(): DslToken? {
        val token = peek()
        if (token.type !in DslTokenType.comparisonOperators) {
            return null
        }
        index++
        return token
    }

    fun consumeActionOperator(): DslToken {
        val token = peek()
        require(token.type in DslTokenType.actionOperators) { "规则必须包含动作运算符" }
        index++
        return token
    }

    fun peekType(): DslTokenType = peek().type

    fun peekLexeme(): String = peek().lexeme

    private fun peek(): DslToken = tokens.getOrElse(index) { tokens.last() }
}
