package io.alexjoest.stackupup.rules.parse

import io.alexjoest.stackupup.rules.ComparisonOperator
import io.alexjoest.stackupup.rules.RuleField
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
        stream.consume(DslTokenType.EOF, "规则末尾存在无法识别的多余内容")
        return RuleAst(condition, action)
    }

    private fun parseAction(stream: DslTokenCursor): RuleActionAst {
        val steps = arrayListOf(parseActionStep(stream))
        while (stream.peekType() == DslTokenType.ARROW) {
            steps += parseActionStep(stream)
        }
        return RuleActionAst(steps)
    }

    private fun parseActionStep(stream: DslTokenCursor): RuleStepAst {
        stream.consumeActionOperator()
        return when (stream.peekType()) {
            DslTokenType.NUMBER -> RuleStepAst(RuleStepKind.SET, stream.consumeLiteral("动作值必须是整数").toInt())
            DslTokenType.PLUS -> {
                stream.consume(DslTokenType.PLUS, "加法动作缺少 +")
                RuleStepAst(RuleStepKind.ADD, stream.consumeLiteral("加法动作缺少整数").toInt())
            }
            DslTokenType.MINUS -> {
                stream.consume(DslTokenType.MINUS, "减法动作缺少 -")
                RuleStepAst(RuleStepKind.SUBTRACT, stream.consumeLiteral("减法动作缺少整数").toInt())
            }
            DslTokenType.STAR -> {
                stream.consume(DslTokenType.STAR, "乘法动作缺少 *")
                RuleStepAst(RuleStepKind.MULTIPLY, stream.consumeLiteral("乘法动作缺少整数").toInt())
            }
            DslTokenType.SLASH -> {
                stream.consume(DslTokenType.SLASH, "除法动作缺少 /")
                RuleStepAst(RuleStepKind.DIVIDE, stream.consumeLiteral("除法动作缺少整数").toInt())
            }
            else -> error("不支持的动作步骤: ${stream.peekLexeme()}")
        }
    }

    private fun parseCondition(stream: DslTokenCursor): ConditionAst = parseOrCondition(stream)

    private fun parseOrCondition(stream: DslTokenCursor): ConditionAst {
        val conditions = arrayListOf(parseAndCondition(stream))
        while (stream.match(DslTokenType.OR_OR)) {
            conditions += parseAndCondition(stream)
        }
        return if (conditions.size == 1) conditions.single() else OrConditionAst(conditions)
    }

    private fun parseAndCondition(stream: DslTokenCursor): ConditionAst {
        val conditions = arrayListOf(parseAtomicCondition(stream))
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

        val literals = arrayListOf(stream.consumeLiteral("列表条件不能为空"))
        while (stream.match(DslTokenType.COMMA)) {
            literals += stream.consumeLiteral("列表条件中存在空条目")
        }
        stream.consume(DslTokenType.RIGHT_BRACKET, "列表条件缺少右中括号 ]")
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
        val fieldToken = stream.consume(DslTokenType.IDENTIFIER, "条件必须以字段名开头").lexeme
        val field = requireNotNull(RuleField.fromIdentifier(fieldToken)) { "不支持的字段: $fieldToken" }
        val operator = ComparisonOperator.fromSymbol(stream.consumeComparisonOperator().lexeme)
        val literal = stream.consumeLiteral("条件缺少比较值")
        return FieldComparisonAst(field, operator, literal)
    }
}

