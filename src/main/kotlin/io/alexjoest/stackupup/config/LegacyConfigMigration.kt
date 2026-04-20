package io.alexjoest.stackupup.config

import io.alexjoest.stackupup.StackUpUpIds
import java.io.File

internal object LegacyConfigMigration {
    private val removedCompatKeys = arrayOf(
        "B:enableScripting=",
        "B:actuallyAdditions=",
        "B:appliedEnergistics2=",
        "B:chiselsAndBits=",
        "B:cyclopsCore=",
        "B:industrialCraft2=",
        "B:refinedStorage="
    )

    fun migrate(configDirectory: File) {
        val newConfigFile = migrateMainConfigFile(configDirectory)
        migrateLegacyRulesFile(configDirectory)
        normalizeConfigFile(newConfigFile)
    }

    private fun migrateMainConfigFile(configDirectory: File): File {
        val oldFile = File(configDirectory, "${StackUpUpIds.MOD_ID}.cfg")
        val newFile = File(configDirectory, "${StackUpUpIds.PUBLIC_ID}.cfg")
        if (oldFile.exists() && !newFile.exists()) {
            oldFile.copyTo(newFile)
        }
        return newFile
    }

    private fun migrateLegacyRulesFile(configDirectory: File) {
        val legacyRulesFile = File(
            configDirectory,
            "${StackUpUpIds.PUBLIC_ID}-rules.${StackUpUpIds.RULE_FILE_EXTENSION}"
        )
        val primaryRulesFile = File(
            File(configDirectory, StackUpUpIds.RULES_DIRECTORY_NAME),
            StackUpUpIds.RULES_FILE_NAME
        )
        if (legacyRulesFile.exists() && !primaryRulesFile.exists()) {
            primaryRulesFile.parentFile?.mkdirs()
            legacyRulesFile.copyTo(primaryRulesFile)
        }
    }

    private fun normalizeConfigFile(file: File) {
        if (!file.exists()) {
            return
        }

        val filteredLines = ArrayList<String>()
        for (line in file.readLines(Charsets.UTF_8)) {
            val trimmed = line.trim()
            if (removedCompatKeys.any(trimmed::startsWith)) {
                continue
            }
            filteredLines += line
        }

        val normalized = filteredLines.joinToString(System.lineSeparator())
            .replace("tooltipStackDisplayMode=advanced", "tooltipStackDisplayMode=ADVANCED")
            .replace("tooltipStackDisplayMode=always", "tooltipStackDisplayMode=ALWAYS")
            .replace("tooltipStackDisplayMode=off", "tooltipStackDisplayMode=OFF")
        val expectedText = normalized + System.lineSeparator()
        if (expectedText != file.readText(Charsets.UTF_8)) {
            file.writeText(expectedText, Charsets.UTF_8)
        }
    }
}
