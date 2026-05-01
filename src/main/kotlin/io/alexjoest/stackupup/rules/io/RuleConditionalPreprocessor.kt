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

    private fun parseIf(trimmed: String): RuleGate? {
        if (!trimmed.startsWith("if ")) {
            return null
        }

        val parts = trimmed.removePrefix("if ").trim().split(Regex("\\s+"), limit = 3)
        if (parts.size != 3 || parts[1] != "=") {
            return null
        }
        return RuleGate(parts[0].lowercase(), parts[2].lowercase())
    }
}
