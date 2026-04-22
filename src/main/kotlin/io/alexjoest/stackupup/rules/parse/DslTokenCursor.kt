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
        if (peekType() != type) {
            return false
        }
        index++
        return true
    }

    fun consume(type: DslTokenType, message: String): DslToken {
        val token = currentToken()
        require(token.type == type) { message }
        index++
        return token
    }

    fun tryConsume(type: DslTokenType): DslToken? {
        if (peekType() != type) {
            return null
        }
        index++
        return tokens[index - 1]
    }

    fun consumeLiteral(message: String): String {
        val token = currentToken()
        require(token.type == DslTokenType.IDENTIFIER || token.type == DslTokenType.NUMBER) { message }
        index++
        return token.lexeme
    }

    fun tryConsumeLiteral(): String? {
        val token = currentToken()
        if (token.type != DslTokenType.IDENTIFIER && token.type != DslTokenType.NUMBER) {
            return null
        }
        index++
        return token.lexeme
    }

    fun consumeComparisonOperator(): DslToken {
        val token = currentToken()
        require(token.type.isComparisonOperator) { "条件缺少比较运算符" }
        index++
        return token
    }

    fun tryConsumeComparisonOperator(): DslToken? {
        val token = currentToken()
        if (!token.type.isComparisonOperator) {
            return null
        }
        index++
        return token
    }

    fun consumeActionOperator(): DslToken {
        val token = currentToken()
        require(token.type.isActionOperator) { "规则必须包含动作运算符" }
        index++
        return token
    }

    fun peekType(): DslTokenType = currentToken().type

    fun peekLexeme(): String = currentToken().lexeme

    private fun currentToken(): DslToken = tokens.getOrElse(index) { tokens.last() }
}
