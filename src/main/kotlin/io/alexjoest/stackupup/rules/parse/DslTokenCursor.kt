package io.alexjoest.stackupup.rules.parse

import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.LocalizedRuleException
import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.RuleMessages

internal class DslTokenCursor(private val tokens: List<DslToken>) {
    private var index: Int = 0

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

    fun consumeLiteral(message: LocalizedMessage): String = consumeLiteralToken(message).lexeme

    fun consumeLiteralToken(message: LocalizedMessage): DslToken {
        val token = currentToken()
        if (token.type != DslTokenType.IDENTIFIER && token.type != DslTokenType.NUMBER) {
            throw LocalizedRuleException(message)
        }
        index++
        return token
    }

    fun consumeComparisonOperator(): DslToken {
        val token = currentToken()
        if (!token.type.isComparisonOperator) {
            throw RuleMessages.exception(RuleMessageKey.MISSING_COMPARISON_OPERATOR)
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

    /**
     * 前瞻查看距当前位置 [offset] 个 token 的类型；越过 token 末尾（EOF 之后）返回 null。
     * 解析器用纯前瞻做分支判定，任何失败分支都不会消费 token。
     */
    fun peekType(offset: Int = 0): DslTokenType? = tokens.getOrNull(index + offset)?.type

    fun peekLexeme(): String = currentToken().lexeme

    private fun currentToken(): DslToken = tokens.getOrElse(index) { tokens.last() }
}
