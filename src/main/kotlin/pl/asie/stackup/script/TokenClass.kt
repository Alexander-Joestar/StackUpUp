package pl.asie.stackup.script

import java.util.function.Function
import java.util.stream.Collectors

class TokenClass<T>(
    private val classFunction: Function<T, List<Class<*>>>,
    ignoreCase: Boolean
) : TokenString<T>(
    Function { t -> classFunction.apply(t).stream().map { obj: Class<*> -> obj.name }.collect(Collectors.toList()) },
    ignoreCase
) {
    override fun isInvalidComparisonType(type: ComparisonType): Boolean {
        return type != ComparisonType.EQUAL && type != ComparisonType.GREATER_EQUAL &&
            type != ComparisonType.GREATER_THAN && type != ComparisonType.NOT_EQUAL
    }

    override fun apply(`object`: T): Boolean {
        val cReceivedL = classFunction.apply(`object`)
        return try {
            val cSet = Class.forName(getString())
            for (cReceived in cReceivedL) {
                when (getComparisonType()) {
                    ComparisonType.EQUAL -> if (cSet == cReceived) return true
                    ComparisonType.NOT_EQUAL -> if (cSet == cReceived) return false
                    ComparisonType.GREATER_THAN -> {
                        if (cSet == cReceived) return false
                        if (cSet.isAssignableFrom(cReceived)) return true
                    }

                    ComparisonType.GREATER_EQUAL -> if (cSet == cReceived || cSet.isAssignableFrom(cReceived)) return true
                    else -> if (cSet == cReceived) return true
                }
            }
            false
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            false
        }
    }
}
