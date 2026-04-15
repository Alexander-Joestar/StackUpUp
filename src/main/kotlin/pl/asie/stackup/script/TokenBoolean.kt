package pl.asie.stackup.script

import java.io.PushbackReader
import java.util.function.Predicate

class TokenBoolean<T>(private val function: Predicate<T>) : Token<T>() {
    override fun parse(reader: PushbackReader) {
    }

    override fun apply(`object`: T): Boolean = function.test(`object`)
}
