package pl.asie.stackup.script

import org.apache.commons.lang3.tuple.Pair
import java.io.IOException
import java.io.PushbackReader
import java.util.function.Supplier

object TokenProvider {
    private val tokenMap: MutableMap<String, Supplier<Token<*>>> = HashMap()

    @JvmStatic
    fun addToken(key: String, t: Supplier<Token<*>>) {
        tokenMap[key] = t
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getToken(r: PushbackReader): Pair<String, Token<*>?> {
        var invToken = false
        val key = StringBuilder()
        ScriptHandler.cutWhitespace(r)

        var c = r.read()
        if (c == '!'.code) {
            invToken = true
        } else {
            r.unread(c)
        }

        while (Character.isAlphabetic(r.read().also { c = it })) {
            key.appendCodePoint(c)
        }
        r.unread(c)
        ScriptHandler.cutWhitespace(r)

        val s = tokenMap[key.toString()]
        return if (s != null) {
            val t = s.get()
            t.setInvert(invToken)
            Pair.of(key.toString(), t)
        } else {
            Pair.of(key.toString(), null)
        }
    }
}
