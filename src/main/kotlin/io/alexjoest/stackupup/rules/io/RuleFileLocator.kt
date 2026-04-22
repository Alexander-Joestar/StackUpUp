package io.alexjoest.stackupup.rules.io

import java.io.File
import io.alexjoest.stackupup.StackUpUpIds

object RuleFileLocator {
    @Volatile
    private var configDirectory: File? = null

    fun setConfigDirectory(directory: File) {
        configDirectory = directory
    }

    fun resolve(): File = File(resolveRulesDirectory(), StackUpUpIds.RULES_FILE_NAME)

    fun resolveRulesDirectory(): File = File(currentConfigDirectory(), StackUpUpIds.RULES_DIRECTORY_NAME)

    fun resolveLegacy(): File =
        File(currentConfigDirectory(), "${StackUpUpIds.PUBLIC_ID}-rules.${StackUpUpIds.RULE_FILE_EXTENSION}")

    fun resetForTests() {
        configDirectory = null
    }

    private fun currentConfigDirectory(): File = configDirectory ?: File("run/config")
}
