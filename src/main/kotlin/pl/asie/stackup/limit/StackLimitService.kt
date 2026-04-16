package pl.asie.stackup.limit

import pl.asie.stackup.rules.compile.RuleSnapshot
import pl.asie.stackup.rules.model.RuleMatchContext

class StackLimitService(
    private val snapshot: RuleSnapshot
) {
    fun resolve(identity: StackIdentity, baseLimit: Int, oreNames: Set<String>): Int {
        val context = RuleMatchContext(
            itemId = identity.itemId,
            modId = identity.modId,
            meta = identity.meta,
            baseSize = baseLimit,
            type = identity.type,
            oreNames = oreNames
        )

        var result = baseLimit
        for (rule in snapshot.rules) {
            if (rule.matches(context)) {
                result = applyAction(result, rule.action.operator, rule.action.value)
            }
        }

        return result.coerceAtLeast(1)
    }

    private fun applyAction(current: Int, operator: String, value: Int): Int {
        return when (operator) {
            "->" -> value
            "+=" -> current + value
            "-=" -> current - value
            "*=" -> current * value
            "/=" -> if (value == 0) current else current / value
            else -> current
        }
    }
}
