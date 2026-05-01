package io.alexjoest.stackupup.rules.parse

import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.LocalizedRuleException
import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.RuleMessages

internal class DslTokenCursor(private val tokens: List<DslToken>) {
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

    fun consume(type: DslTokenType, message: LocalizedMessage): DslToken {
        val token = currentToken()
        if (token.type != type) {
            throw LocalizedRuleException(message)
        }
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

    fun consumeLiteral(message: LocalizedMessage): String {
        val token = currentToken()
        if (token.type != DslTokenType.IDENTIFIER && token.type != DslTokenType.NUMBER) {
            throw LocalizedRuleException(message)
        }
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
        if (!token.type.isComparisonOperator) {
            throw RuleMessages.exception(RuleMessageKey.MISSING_COMPARISON_OPERATOR)
        }
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
        if (!token.type.isActionOperator) {
            throw RuleMessages.exception(RuleMessageKey.MISSING_ACTION_OPERATOR)
        }
        index++
        return token
    }

    fun peekType(): DslTokenType = currentToken().type

    fun peekLexeme(): String = currentToken().lexeme

    private fun currentToken(): DslToken = tokens.getOrElse(index) { tokens.last() }
}
