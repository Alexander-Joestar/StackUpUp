package io.alexjoest.stackupup.rules.io

import java.io.File

object RuleFileTemplate {
    private val defaultContent: String = """
        # StackUpUp DSL v2 rules
        # This file is loaded from config/stackupup/main.su.
        # Keep rules simple. Later lines continue from earlier results.
        #
        # Comments:
        #   # line comment
        #   // line comment
        #   /* block comment */
        #
        # Fields:
        #   item, mod, ore, meta, metadata, size
        #
        # Comparisons:
        #   =  !=  >  >=  <  <=
        #
        # Lists:
        #   item in [minecraft:egg, minecraft:snowball]
        #   mod in [minecraft, gregtech]
        #   meta in [0, 1, 2]
        #
        # Range syntax:
        #   size > 2 && size < 64
        #   2 < size < 64
        #
        # Actions:
        #   -> 128      set
        #   -> +32      add
        #   -> -16      subtract
        #   -> *2       multiply
        #   -> /2       divide
        #   -> *2 -> +10 -> /2
        #
        # Item matching:
        #   item = minecraft:egg
        #   item = gregtech:meta_dust
        #   item = gregtech:meta_dust:324
        #   item = gregtech:meta_dust@324
        #   item = gregtech:meta_dust && meta = 324
        #
        # OreDictionary examples:
        #   ore = ingotSteel -> 1024
        #   ore = dustSteel -> 2048
        #
        # Wildcards:
        #   item = thermal:* -> 256
        #   item = minecraft:*_ball -> 128
        #
        # Priority:
        #   && is evaluated before ||
        #   Use simple expressions. Parentheses are not supported.
        #
        # Common examples:
        # item = minecraft:egg -> 64
        # item in [minecraft:egg, minecraft:snowball] -> 128
        # mod = thermal -> 1024
        # ore = ingotSteel -> *2
        # item = gregtech:meta_dust && meta = 324 -> 512
        # 2 < size < 64 -> 1024
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
