package pl.asie.stackup.script

import java.io.IOException
import java.io.PushbackReader
import java.util.function.Function

open class TokenNumeric<T>(protected val function: Function<T, Number>) : Token<T>() {
    private lateinit var type: ComparisonType
    private var number: Int = 0

    protected open fun isInvalidComparisonType(type: ComparisonType): Boolean {
        return type != ComparisonType.EQUAL && type != ComparisonType.LESS_EQUAL && type != ComparisonType.NOT_EQUAL &&
            type != ComparisonType.LESS_THAN && type != ComparisonType.GREATER_EQUAL && type != ComparisonType.GREATER_THAN
    }

    override fun parse(reader: PushbackReader) {
        type = getComparisonType(reader)
        if (isInvalidComparisonType(type)) {
            throw TokenException("Unsupported comparison type $type!")
        }

        try {
            number = parseInteger(reader)
        } catch (e: NumberFormatException) {
            throw TokenException("Invalid number!", e)
        }
    }

    protected fun getNumber(): Int = number
    protected fun getComparisonType(): ComparisonType = type

    override fun apply(`object`: T): Boolean {
        val iv = function.apply(`object`).toInt()
        return when (type) {
            ComparisonType.EQUAL -> iv == number
            ComparisonType.NOT_EQUAL -> iv != number
            ComparisonType.LESS_THAN -> iv < number
            ComparisonType.GREATER_THAN -> iv > number
            ComparisonType.LESS_EQUAL -> iv <= number
            ComparisonType.GREATER_EQUAL -> iv >= number
            else -> iv == number
        }
    }

    companion object {
        @JvmStatic
        @Throws(NumberFormatException::class, IOException::class)
        fun parseInteger(reader: PushbackReader): Int {
            val builder = StringBuilder()
            ScriptHandler.cutWhitespace(reader)
            var c: Int
            while (Character.isDigit(reader.read().also { c = it })) {
                builder.appendCodePoint(c)
            }
            reader.unread(c)
            ScriptHandler.cutWhitespace(reader)
            return builder.toString().toInt()
        }
    }
}
