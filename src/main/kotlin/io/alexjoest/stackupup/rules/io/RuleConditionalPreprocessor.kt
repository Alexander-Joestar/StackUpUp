package io.alexjoest.stackupup.rules.io

internal object RuleConditionalPreprocessor {
    fun filter(inputs: List<RuleLineLoader.RuleLineInput>, context: RuleGateContext): List<RuleLineLoader.RuleLineInput> {
        val output = ArrayList<RuleLineLoader.RuleLineInput>(inputs.size)
        val activeStack = ArrayList<Boolean>()

        for (input in inputs) {
            val trimmed = input.content.trim()
            val gate = parseIf(trimmed)
            when {
                gate != null -> activeStack += isParentActive(activeStack) && context.matches(gate)
                trimmed == "end" -> {
                    if (activeStack.isNotEmpty()) {
                        activeStack.removeAt(activeStack.lastIndex)
                    }
                }
                isParentActive(activeStack) -> output += input
            }
        }

        return output
    }

    private fun isParentActive(activeStack: List<Boolean>): Boolean = activeStack.all { it }

    private fun parseIf(trimmed: String): RuleGateExpression? {
        if (!trimmed.startsWith("if ")) {
            return null
        }

        val expression = trimmed.removePrefix("if ").trim()
        val normalized = legacyIfExpression(expression)
        val parsed = MarkdownGateParser.parse(normalized)
        return when (parsed) {
            is MarkdownGateParseResult.Success -> parsed.expression
            is MarkdownGateParseResult.Failure -> null
        }
    }

    private fun legacyIfExpression(expression: String): String {
        val parts = expression.split(Regex("\\s+"), limit = 3)
        if (parts.size == 3 && parts[0].equals("mod", ignoreCase = true) && parts[1] == "=") {
            return "modLoaded(\"${parts[2]}\")"
        }
        return expression
    }
}
