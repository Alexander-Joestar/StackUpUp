package pl.asie.stackup.script

import java.util.function.Function

class TokenResourceLocation<T>(function: Function<T, List<String>>) : TokenString<T>(function, false) {
    override fun apply(`object`: T): Boolean {
        val cResult = getComparisonType() != ComparisonType.NOT_EQUAL
        for (str in function.apply(`object`)) {
            val str1 = str.split(":")
            val str2 = getString().split(":")
            if (str1.size == 2 && str2.size == 2) {
                if (str2[0] != "*" && !compare(str1[0], str2[0])) {
                    return !cResult
                }
                if (str2[1] != "*" && !compare(str1[1], str2[1])) {
                    return !cResult
                }
                return cResult
            }
        }
        return !cResult
    }
}
