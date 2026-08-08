package io.alexjoest.stackupup.rules.parse

import io.alexjoest.stackupup.rules.ComparisonOperator
import io.alexjoest.stackupup.rules.RuleField
import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.RuleMessages
import io.alexjoest.stackupup.rules.RuleStepKind
import io.alexjoest.stackupup.rules.ast.AndConditionAst
import io.alexjoest.stackupup.rules.ast.ConditionAst
import io.alexjoest.stackupup.rules.ast.FieldComparisonAst
import io.alexjoest.stackupup.rules.ast.ListConditionAst
import io.alexjoest.stackupup.rules.ast.OrConditionAst
import io.alexjoest.stackupup.rules.ast.RangeConditionAst
import io.alexjoest.stackupup.rules.ast.RuleAst
import io.alexjoest.stackupup.rules.model.RuleAction
import io.alexjoest.stackupup.rules.model.RuleStep

object DslParser {
    fun parseLine(line: String): RuleAst {
        val stream = DslTokenCursor(DslTokenizer.tokenize(line))
        val condition = parseOrCondition(stream)
        val action = parseAction(stream)
        stream.consume(DslTokenType.EOF, RuleMessages.message(RuleMessageKey.TRAILING_CONTENT))
        return RuleAst(condition, action)
    }

    private fun parseAction(stream: DslTokenCursor): RuleAction {
        val steps = buildList {
            add(parseActionStep(stream))
            while (stream.peekType() == DslTokenType.ARROW) {
                add(parseActionStep(stream))
            }
        }
        return RuleAction(steps)
    }

    private fun parseActionStep(stream: DslTokenCursor): RuleStep {
        stream.consumeActionOperator()
        return when (val type = stream.peekType()) {
            DslTokenType.NUMBER -> RuleStep(
                RuleStepKind.SET,
                stream.consumeLiteral(RuleMessages.message(RuleMessageKey.ACTION_VALUE_MUST_BE_INTEGER)).toInt(),
            )
            DslTokenType.PLUS -> {
                stream.consume(type, RuleMessages.message(RuleMessageKey.ADD_ACTION_MISSING_SYMBOL))
                RuleStep(RuleStepKind.ADD, stream.consumeLiteral(RuleMessages.message(RuleMessageKey.ADD_ACTION_MISSING_INTEGER)).toInt())
            }
            DslTokenType.MINUS -> {
                stream.consume(type, RuleMessages.message(RuleMessageKey.SUBTRACT_ACTION_MISSING_SYMBOL))
                RuleStep(RuleStepKind.SUBTRACT, stream.consumeLiteral(RuleMessages.message(RuleMessageKey.SUBTRACT_ACTION_MISSING_INTEGER)).toInt())
            }
            DslTokenType.STAR -> {
                stream.consume(type, RuleMessages.message(RuleMessageKey.MULTIPLY_ACTION_MISSING_SYMBOL))
                RuleStep(RuleStepKind.MULTIPLY, stream.consumeLiteral(RuleMessages.message(RuleMessageKey.MULTIPLY_ACTION_MISSING_INTEGER)).toInt())
            }
            DslTokenType.SLASH -> {
                stream.consume(type, RuleMessages.message(RuleMessageKey.DIVIDE_ACTION_MISSING_SYMBOL))
                RuleStep(RuleStepKind.DIVIDE, stream.consumeLiteral(RuleMessages.message(RuleMessageKey.DIVIDE_ACTION_MISSING_INTEGER)).toInt())
            }
            else -> throw RuleMessages.exception(RuleMessageKey.UNSUPPORTED_ACTION_STEP, stream.peekLexeme())
        }
    }

    private fun parseOrCondition(stream: DslTokenCursor): ConditionAst {
        val conditions = buildList {
            add(parseAndCondition(stream))
            while (stream.match(DslTokenType.OR_OR)) {
                add(parseAndCondition(stream))
            }
        }
        return if (conditions.size == 1) conditions.single() else OrConditionAst(conditions)
    }

    private fun parseAndCondition(stream: DslTokenCursor): ConditionAst {
        val conditions = buildList {
            add(parseAtomicCondition(stream))
            while (stream.match(DslTokenType.AND_AND)) {
                add(parseAtomicCondition(stream))
            }
        }
        return if (conditions.size == 1) conditions.single() else AndConditionAst(conditions)
    }

    private fun parseAtomicCondition(stream: DslTokenCursor): ConditionAst {
        parseListCondition(stream)?.let { return it }
        parseChainedComparison(stream)?.let { return it }
        return parseSingleCondition(stream)
    }

    private fun parseListCondition(stream: DslTokenCursor): ListConditionAst? {
        // 纯前瞻判定，失败不消费任何 token
        if (stream.peekType() != DslTokenType.IDENTIFIER ||
            stream.peekType(1) != DslTokenType.IN ||
            stream.peekType(2) != DslTokenType.LEFT_BRACKET
        ) {
            return null
        }
        val fieldToken = stream.consume(DslTokenType.IDENTIFIER, RuleMessages.message(RuleMessageKey.CONDITION_MUST_START_WITH_FIELD)).lexeme
        val field = RuleField.fromIdentifier(fieldToken)
            ?: throw RuleMessages.exception(RuleMessageKey.UNSUPPORTED_FIELD, fieldToken)
        stream.match(DslTokenType.IN)
        stream.match(DslTokenType.LEFT_BRACKET)

        val literalTokens = buildList {
            add(stream.consumeLiteralToken(RuleMessages.message(RuleMessageKey.LIST_CONDITION_CANNOT_BE_EMPTY)))
            while (stream.match(DslTokenType.COMMA)) {
                add(stream.consumeLiteralToken(RuleMessages.message(RuleMessageKey.LIST_CONDITION_CONTAINS_EMPTY_ENTRY)))
            }
        }
        stream.consume(DslTokenType.RIGHT_BRACKET, RuleMessages.message(RuleMessageKey.LIST_CONDITION_MISSING_RIGHT_BRACKET))
        if (field == RuleField.ITEM) {
            for (token in literalTokens) {
                validateItemLiteral(token.lexeme, token.column)
            }
        }
        return ListConditionAst(field, literalTokens.map { it.lexeme })
    }

    private fun parseChainedComparison(stream: DslTokenCursor): ConditionAst? {
        // 单遍 operand (cmp operand)+：纯前瞻判定链形状，失败不消费任何 token
        if (!stream.peekType().isLiteralToken()) {
            return null
        }
        if (stream.peekType(1)?.isComparisonOperator != true) {
            return null
        }
        if (stream.peekType(2) != DslTokenType.IDENTIFIER) {
            return null
        }
        val hasSecondComparison = stream.peekType(3)?.isComparisonOperator == true
        if (hasSecondComparison && !stream.peekType(4).isLiteralToken()) {
            return null
        }
        // 单比较链 `literal cmp field` 与标准 `field cmp literal` 歧义：左侧为 IDENTIFIER 时
        // 一律按标准条件解析（`mod = thermal` 是字段比较，不是链），链的左操作数只接受 NUMBER；
        // 双比较链保持旧解析行为，左右字面量仍可为 IDENTIFIER。
        if (!hasSecondComparison && stream.peekType() != DslTokenType.NUMBER) {
            return null
        }

        val leftLiteralToken = stream.consumeLiteralToken(RuleMessages.message(RuleMessageKey.CONDITION_MISSING_VALUE))
        val firstOperator = ComparisonOperator.fromSymbol(stream.consumeComparisonOperator().lexeme)
        val fieldToken = stream.consume(DslTokenType.IDENTIFIER, RuleMessages.message(RuleMessageKey.CONDITION_MUST_START_WITH_FIELD)).lexeme
        val field = RuleField.fromIdentifier(fieldToken)
            ?: throw RuleMessages.exception(RuleMessageKey.UNSUPPORTED_FIELD, fieldToken)
        if (field == RuleField.ITEM) {
            validateItemLiteral(leftLiteralToken.lexeme, leftLiteralToken.column)
        }
        if (!hasSecondComparison) {
            // 单比较链：literal cmp field ≡ field cmp.reverse() literal
            return FieldComparisonAst(field, firstOperator.reverse(), leftLiteralToken.lexeme)
        }

        val secondOperator = ComparisonOperator.fromSymbol(stream.consumeComparisonOperator().lexeme)
        val rightLiteralToken = stream.consumeLiteralToken(RuleMessages.message(RuleMessageKey.CONDITION_MISSING_VALUE))
        if (field == RuleField.ITEM) {
            validateItemLiteral(rightLiteralToken.lexeme, rightLiteralToken.column)
        }
        // 三项以上比较链不静默截断：完整区间之后仍是比较符，直接报错
        if (stream.peekType()?.isComparisonOperator == true) {
            throw RuleMessages.exception(RuleMessageKey.TRAILING_CONTENT)
        }

        val firstIsLowerBound = firstOperator.isLowerBound()
        return RangeConditionAst(
            field = field,
            lower = if (firstIsLowerBound) leftLiteralToken.lexeme else rightLiteralToken.lexeme,
            lowerInclusive = if (firstIsLowerBound) {
                firstOperator == ComparisonOperator.LESS_EQUALS
            } else {
                secondOperator == ComparisonOperator.GREATER_EQUALS
            },
            upper = if (firstIsLowerBound) rightLiteralToken.lexeme else leftLiteralToken.lexeme,
            upperInclusive = if (firstIsLowerBound) {
                secondOperator == ComparisonOperator.LESS_EQUALS
            } else {
                firstOperator == ComparisonOperator.GREATER_EQUALS
            },
        )
    }

    private fun parseSingleCondition(stream: DslTokenCursor): ConditionAst {
        val fieldToken = stream.consume(DslTokenType.IDENTIFIER, RuleMessages.message(RuleMessageKey.CONDITION_MUST_START_WITH_FIELD)).lexeme
        val field = RuleField.fromIdentifier(fieldToken)
            ?: throw RuleMessages.exception(RuleMessageKey.UNSUPPORTED_FIELD, fieldToken)
        val operator = ComparisonOperator.fromSymbol(stream.consumeComparisonOperator().lexeme)
        val literalToken = stream.consumeLiteralToken(RuleMessages.message(RuleMessageKey.CONDITION_MISSING_VALUE))
        if (field == RuleField.ITEM) {
            validateItemLiteral(literalToken.lexeme, literalToken.column)
        }
        return FieldComparisonAst(field, operator, literalToken.lexeme)
    }

    /**
     * item 字面量在解析阶段一次定型（T7.2）：非法写法直接抛带列号与原因的错误，
     * 不再静默回落为 pattern。列号指向字面量中的 `@` 字符。
     */
    private fun validateItemLiteral(literal: String, tokenColumn: Int) {
        val parsed = ItemLiteralSyntax.parse(literal)
        if (parsed is ItemLiteralSyntax.Invalid) {
            val atColumn = tokenColumn + literal.indexOf('@')
            throw RuleMessages.exception(
                RuleMessageKey.INVALID_ITEM_LITERAL,
                literal,
                atColumn,
                RuleMessages.message(parsed.reasonKey, *parsed.reasonArgs.toTypedArray()),
            )
        }
    }

    private fun DslTokenType?.isLiteralToken(): Boolean = this == DslTokenType.IDENTIFIER || this == DslTokenType.NUMBER

    /**
     * 链式比较中的操作符是否表达字段下界：`field > lower` / `field >= lower`。
     * 等值操作符在链式比较中没有方向语义，沿用既有的 UNSUPPORTED_REVERSE_OPERATOR 拒绝。
     */
    private fun ComparisonOperator.isLowerBound(): Boolean = when (this) {
        ComparisonOperator.LESS, ComparisonOperator.LESS_EQUALS -> true
        ComparisonOperator.GREATER, ComparisonOperator.GREATER_EQUALS -> false
        ComparisonOperator.EQUALS, ComparisonOperator.NOT_EQUALS ->
            throw RuleMessages.exception(RuleMessageKey.UNSUPPORTED_REVERSE_OPERATOR, symbol)
    }
}
