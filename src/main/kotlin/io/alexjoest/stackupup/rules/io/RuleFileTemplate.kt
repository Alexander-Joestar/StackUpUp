package io.alexjoest.stackupup.rules.io

import java.io.File

object RuleFileTemplate {
    private val defaultContent: String = """
        # StackUpUp DSL v2 rules
        # This file is loaded from config/stackupup/main.su.
        # Keep rules simple. Later lines continue from earlier results.
        #
        # Examples:
        # item = minecraft:egg -> 64
        # item = gregtech:gt.metaitem.01 && meta = 11305 -> 512
        # ore = ingotSteel -> *2
        # size > 64 -> 128
        # size > 1 -> *2 -> +10
    """.trimIndent() + System.lineSeparator()

    fun ensureExists(file: File) {
        if (file.exists()) {
            return
        }

        file.parentFile?.mkdirs()
        file.writeText(defaultContent, Charsets.UTF_8)
    }
}
