package io.alexjoest.stackupup.config

import io.alexjoest.stackupup.StackUpUp
import java.io.File

internal object ConfigFileSanitizer {
    private val allowedRootCategories = linkedSetOf(
        "general",
        "client",
        "compatibility",
    )

    fun sanitize(configDirectory: File) {
        val configFile = File(configDirectory, "${StackUpUp.PUBLIC_ID}.cfg")
        if (!configFile.exists()) {
            return
        }

        val originalText = configFile.readText(Charsets.UTF_8)
        val sanitized = RawConfigFileScanner.sanitizeRootCategories(originalText, allowedRootCategories)
        if (sanitized.removedRootCategories.isEmpty()) {
            return
        }

        configFile.writeText(sanitized.text, Charsets.UTF_8)
        if (sanitized.removedRootCategories.isNotEmpty()) {
            StackUpUp.logger?.info(
                "Sanitized {} by removing unsupported root categories: {}",
                configFile.name,
                sanitized.removedRootCategories.joinToString(", "),
            )
        }
    }
}
