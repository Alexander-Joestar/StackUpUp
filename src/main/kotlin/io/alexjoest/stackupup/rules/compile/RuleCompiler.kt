package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.ast.RuleStepAst
import io.alexjoest.stackupup.rules.model.RuleAction
import io.alexjoest.stackupup.rules.model.RuleStep
import io.alexjoest.stackupup.rules.parse.DslParser

object RuleCompiler {
    fun compileLine(line: String, lineNumber: Int): CompiledRule {
        val ast = DslParser.parseLine(line)
        val steps = ArrayList<RuleStep>(ast.action.steps.size)
        for (step in ast.action.steps) {
            steps += compileActionStep(step)
        }
        return CompiledRule(
            lineNumber = lineNumber,
            sourceLine = line,
            action = RuleAction(steps),
            referencedFields = ast.condition.debugFields().toSet(),
            predicate = RuleConditionCompiler.compile(ast.condition),
        )
    }

    private fun compileActionStep(step: RuleStepAst): RuleStep = RuleStep(step.kind, step.value)
}
