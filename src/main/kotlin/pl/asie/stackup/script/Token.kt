package pl.asie.stackup.script

import java.io.IOException
import java.io.PushbackReader

abstract class Token<T> {
    enum class ComparisonType {
        EQUAL,
        APPROXIMATELY_EQUAL,
        REGEX_EQUAL,
        NOT_EQUAL,
        LESS_THAN,
        LESS_EQUAL,
        GREATER_THAN,
        GREATER_EQUAL,
        ASSIGN_ADD,
        ASSIGN_SUB,
        ASSIGN_MUL,
        ASSIGN_DIV
    }

    @Throws(IOException::class, TokenException::class)
    protected fun getComparisonType(r: PushbackReader): ComparisonType {
        ScriptHandler.cutWhitespace(r)
        var codePoint = r.read()
        if (codePoint == '+'.code || codePoint == '-'.code || codePoint == '*'.code || codePoint == '/'.code || codePoint == '%'.code) {
            val oldCode = codePoint
            codePoint = r.read()
            if (codePoint == '='.code) {
                return when (oldCode) {
                    '+'.code -> ComparisonType.ASSIGN_ADD
                    '-'.code -> ComparisonType.ASSIGN_SUB
                    '*'.code -> ComparisonType.ASSIGN_MUL
                    '/'.code -> ComparisonType.ASSIGN_DIV
                    '%'.code -> ComparisonType.REGEX_EQUAL
                    else -> throw TokenException("Should not get here! $oldCode")
                }
            }
            throw TokenException("Invalid comparison type!")
        }
        if (codePoint == '!'.code) {
            codePoint = r.read()
            if (codePoint == '='.code) {
                return ComparisonType.NOT_EQUAL
            }
            throw TokenException("Invalid comparison type!")
        } else if (codePoint == '~'.code) {
            codePoint = r.read()
            if (codePoint == '='.code) {
                return ComparisonType.APPROXIMATELY_EQUAL
            }
            throw TokenException("Invalid comparison type!")
        } else if (codePoint == '='.code) {
            codePoint = r.read()
            if (codePoint != '='.code) {
                r.unread(codePoint)
            }
            return ComparisonType.EQUAL
        } else if (codePoint == '<'.code) {
            codePoint = r.read()
            if (codePoint == '='.code) {
                return ComparisonType.LESS_EQUAL
            }
            r.unread(codePoint)
            return ComparisonType.LESS_THAN
        } else if (codePoint == '>'.code) {
            codePoint = r.read()
            if (codePoint == '='.code) {
                return ComparisonType.GREATER_EQUAL
            }
            r.unread(codePoint)
            return ComparisonType.GREATER_THAN
        }
        throw TokenException("Invalid comparison type!")
    }

    private var invert: Boolean = false

    fun isInvert(): Boolean = invert

    fun setInvert(invert: Boolean) {
        this.invert = invert
    }

    @Throws(IOException::class, TokenException::class)
    abstract fun parse(reader: PushbackReader)

    abstract fun apply(`object`: T): Boolean
}
