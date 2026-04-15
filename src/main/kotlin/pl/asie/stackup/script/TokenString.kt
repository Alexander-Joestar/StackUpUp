package pl.asie.stackup.script

import java.io.PushbackReader
import java.util.Locale
import java.util.function.Function

open class TokenString<T>(
    protected val function: Function<T, List<String>>,
    private val ignoreCase: Boolean
) : Token<T>() {
    private lateinit var type: ComparisonType
    private lateinit var s: String

    protected open fun isInvalidComparisonType(type: ComparisonType): Boolean {
        return type != ComparisonType.EQUAL && type != ComparisonType.APPROXIMATELY_EQUAL && type != ComparisonType.NOT_EQUAL
    }

    override fun parse(reader: PushbackReader) {
        type = getComparisonType(reader)
        if (isInvalidComparisonType(type)) {
            throw TokenException("Unsupported comparison type $type!")
        }

        val builder = StringBuilder()
        ScriptHandler.cutWhitespace(reader)
        var c = reader.read()
        if (c != '"'.code) {
            throw TokenException("Expected string beginning, $c found!")
        }

        while (reader.read().also { c = it } != '"'.code) {
            builder.appendCodePoint(c)
        }
        ScriptHandler.cutWhitespace(reader)
        s = builder.toString()
    }

    protected fun getString(): String = s
    protected fun getComparisonType(): ComparisonType = type

    protected fun compare(sReceived: String, sSet: String): Boolean {
        return when (type) {
            ComparisonType.EQUAL, ComparisonType.NOT_EQUAL, ComparisonType.REGEX_EQUAL,
            ComparisonType.LESS_THAN, ComparisonType.LESS_EQUAL, ComparisonType.GREATER_THAN,
            ComparisonType.GREATER_EQUAL, ComparisonType.ASSIGN_ADD, ComparisonType.ASSIGN_SUB,
            ComparisonType.ASSIGN_MUL, ComparisonType.ASSIGN_DIV ->
                if (ignoreCase) sReceived.equals(sSet, ignoreCase = true) else sReceived == sSet

            ComparisonType.APPROXIMATELY_EQUAL -> {
                val hasStartStar = sSet.startsWith("*")
                val hasEndStar = sSet.endsWith("*")
                when {
                    hasStartStar && hasEndStar -> {
                        if (sSet.length == 1) {
                            true
                        } else {
                            sReceived.lowercase(Locale.ROOT)
                                .contains(sSet.substring(1, sSet.length - 1).lowercase(Locale.ROOT))
                        }
                    }

                    hasStartStar -> sReceived.lowercase(Locale.ROOT).endsWith(sSet.substring(1).lowercase(Locale.ROOT))
                    hasEndStar -> sReceived.lowercase(Locale.ROOT)
                        .startsWith(sSet.substring(0, sSet.length - 1).lowercase(Locale.ROOT))

                    else -> sReceived.equals(sSet, ignoreCase = true)
                }
            }
        }
    }

    override fun apply(`object`: T): Boolean {
        for (str in function.apply(`object`)) {
            if (compare(str, s)) {
                return getComparisonType() != ComparisonType.NOT_EQUAL
            }
        }

        return getComparisonType() == ComparisonType.NOT_EQUAL
    }
}
