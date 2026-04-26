package io.alexjoest.stackupup.config

import io.alexjoest.stackupup.StackUpUp
import net.minecraftforge.common.config.Configuration
import java.io.File

internal object ConfigFileSanitizer {
    private val allowedRootCategories = linkedSetOf(
        Configuration.CATEGORY_GENERAL,
        Configuration.CATEGORY_CLIENT
    )

    fun sanitize(configDirectory: File) {
        val configFile = File(configDirectory, "${StackUpUp.PUBLIC_ID}.cfg")
        if (!configFile.exists()) {
            return
        }

        val configuration = Configuration(configFile)
        val removedRootCategories = ArrayList<String>()
        for (categoryName in configuration.categoryNames.toList()) {
            if (categoryName in allowedRootCategories) {
                continue
            }
            configuration.removeCategory(configuration.getCategory(categoryName))
            removedRootCategories += categoryName
        }

        if (removedRootCategories.isNotEmpty()) {
            StackUpUp.logger?.info(
                "Sanitized {} by removing unsupported root categories: {}",
                configFile.name,
                removedRootCategories.joinToString(", ")
            )
        }

        if (removedRootCategories.isNotEmpty() || configuration.hasChanged()) {
            configuration.save()
        }
    }
}
