package io.alexjoest.stackupup.rules.io

import java.io.File

object RuleFileTemplate {
    fun ensureExists(file: File) {
        if (file.exists()) {
            return
        }

        file.parentFile?.mkdirs()
        file.writeText("", Charsets.UTF_8)
    }
}
