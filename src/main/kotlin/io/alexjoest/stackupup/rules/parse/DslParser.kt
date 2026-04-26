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
import io.alexjoest.stackupup.rules.ast.RuleActionAst
import io.alexjoest.stackupup.rules.ast.RuleAst
import io.alexjoest.stackupup.rules.ast.RuleStepAst

object DslParser {
    fun parseLine(line: String): RuleAst {
        val stream = DslTokenCursor(DslTokenizer.tokenize(line))
        val condition = parseCondition(stream)
        val action = parseAction(stream)
        stream.consume(DslTokenType.EOF, RuleMessages.message(RuleMessageKey.TRAILING_CONTENT))
        return RuleAst(condition, action)
    }

    private fun parseAction(stream: DslTokenCursor): RuleActionAst {
        val steps = ArrayList<RuleStepAst>(4)
        steps += parseActionStep(stream)
        while (stream.peekType() == DslTokenType.ARROW) {
            steps += parseActionStep(stream)
        }
        return RuleActionAst(steps)
    }

    private fun parseActionStep(stream: DslTokenCursor): RuleStepAst {
        stream.consumeActionOperator()
        return when (stream.peekType()) {
            DslTokenType.NUMBER -> RuleStepAst(
                RuleStepKind.SET,
                stream.consumeLiteral(RuleMessages.message(RuleMessageKey.ACTION_VALUE_MUST_BE_INTEGER)).toInt()
            )
            DslTokenType.PLUS -> {
                stream.consume(DslTokenType.PLUS, RuleMessages.message(RuleMessageKey.ADD_ACTION_MISSING_SYMBOL))
                RuleStepAst(RuleStepKind.ADD, stream.consumeLiteral(RuleMessages.message(RuleMessageKey.ADD_ACTION_MISSING_INTEGER)).toInt())
            }
            DslTokenType.MINUS -> {
                stream.consume(DslTokenType.MINUS, RuleMessages.message(RuleMessageKey.SUBTRACT_ACTION_MISSING_SYMBOL))
                RuleStepAst(
                    RuleStepKind.SUBTRACT,
                    stream.consumeLiteral(RuleMessages.message(RuleMessageKey.SUBTRACT_ACTION_MISSING_INTEGER)).toInt()
                )
            }
            DslTokenType.STAR -> {
                stream.consume(DslTokenType.STAR, RuleMessages.message(RuleMessageKey.MULTIPLY_ACTION_MISSING_SYMBOL))
                RuleStepAst(
                    RuleStepKind.MULTIPLY,
                    stream.consumeLiteral(RuleMessages.message(RuleMessageKey.MULTIPLY_ACTION_MISSING_INTEGER)).toInt()
                )
            }
            DslTokenType.SLASH -> {
                stream.consume(DslTokenType.SLASH, RuleMessages.message(RuleMessageKey.DIVIDE_ACTION_MISSING_SYMBOL))
                RuleStepAst(
                    RuleStepKind.DIVIDE,
                    stream.consumeLiteral(RuleMessages.message(RuleMessageKey.DIVIDE_ACTION_MISSING_INTEGER)).toInt()
                )
            }
            else -> throw RuleMessages.exception(RuleMessageKey.UNSUPPORTED_ACTION_STEP, stream.peekLexeme())
        }
    }

    private fun parseCondition(stream: DslTokenCursor): ConditionAst = parseOrCondition(stream)

    private fun parseOrCondition(stream: DslTokenCursor): ConditionAst {
        val conditions = ArrayList<ConditionAst>(2)
        conditions += parseAndCondition(stream)
        while (stream.match(DslTokenType.OR_OR)) {
            conditions += parseAndCondition(stream)
        }
        return if (conditions.size == 1) conditions.single() else OrConditionAst(conditions)
    }

    private fun parseAndCondition(stream: DslTokenCursor): ConditionAst {
        val conditions = ArrayList<ConditionAst>(2)
        conditions += parseAtomicCondition(stream)
        while (stream.match(DslTokenType.AND_AND)) {
            conditions += parseAtomicCondition(stream)
        }
        return if (conditions.size == 1) conditions.single() else AndConditionAst(conditions)
    }

    private fun parseAtomicCondition(stream: DslTokenCursor): ConditionAst {
        parseListCondition(stream)?.let { return it }
        parseChainedComparison(stream)?.let { return it }
        return parseSingleCondition(stream)
    }

    private fun parseListCondition(stream: DslTokenCursor): ListConditionAst? {
        val checkpoint = stream.mark()
        val fieldToken = stream.tryConsume(DslTokenType.IDENTIFIER) ?: return null
        val field = RuleField.fromIdentifier(fieldToken.lexeme) ?: return null
        if (!stream.match(DslTokenType.IN)) {
            stream.restore(checkpoint)
            return null
        }
        if (!stream.match(DslTokenType.LEFT_BRACKET)) {
            stream.restore(checkpoint)
            return null
        }

        val literals = ArrayList<String>(4)
        literals += stream.consumeLiteral(RuleMessages.message(RuleMessageKey.LIST_CONDITION_CANNOT_BE_EMPTY))
        while (stream.match(DslTokenType.COMMA)) {
            literals += stream.consumeLiteral(RuleMessages.message(RuleMessageKey.LIST_CONDITION_CONTAINS_EMPTY_ENTRY))
        }
        stream.consume(DslTokenType.RIGHT_BRACKET, RuleMessages.message(RuleMessageKey.LIST_CONDITION_MISSING_RIGHT_BRACKET))
        return ListConditionAst(field, literals)
    }

    private fun parseChainedComparison(stream: DslTokenCursor): ConditionAst? {
        val checkpoint = stream.mark()
        val leftLiteral = stream.tryConsumeLiteral() ?: return null
        val firstOperator = stream.tryConsumeComparisonOperator() ?: run {
            stream.restore(checkpoint)
            return null
        }
        val fieldToken = stream.tryConsume(DslTokenType.IDENTIFIER) ?: run {
            stream.restore(checkpoint)
            return null
        }
        val secondOperator = stream.tryConsumeComparisonOperator() ?: run {
            stream.restore(checkpoint)
            return null
        }
        val rightLiteral = stream.tryConsumeLiteral() ?: run {
            stream.restore(checkpoint)
            return null
        }

        val field = RuleField.fromIdentifier(fieldToken.lexeme) ?: run {
            stream.restore(checkpoint)
            return null
        }

        return AndConditionAst(
            listOf(
                FieldComparisonAst(field, ComparisonOperator.fromSymbol(firstOperator.lexeme).reverse(), leftLiteral),
                FieldComparisonAst(field, ComparisonOperator.fromSymbol(secondOperator.lexeme), rightLiteral)
            )
        )
    }

    private fun parseSingleCondition(stream: DslTokenCursor): ConditionAst {
        val fieldToken = stream.consume(DslTokenType.IDENTIFIER, RuleMessages.message(RuleMessageKey.CONDITION_MUST_START_WITH_FIELD)).lexeme
        val field = RuleField.fromIdentifier(fieldToken)
            ?: throw RuleMessages.exception(RuleMessageKey.UNSUPPORTED_FIELD, fieldToken)
        val operator = ComparisonOperator.fromSymbol(stream.consumeComparisonOperator().lexeme)
        val literal = stream.consumeLiteral(RuleMessages.message(RuleMessageKey.CONDITION_MISSING_VALUE))
        return FieldComparisonAst(field, operator, literal)
    }
}

