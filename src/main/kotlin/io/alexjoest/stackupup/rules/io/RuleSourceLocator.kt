package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUpIds
import net.minecraftforge.fml.common.FMLCommonHandler
import java.io.File

object RuleSourceLocator {
    @Volatile
    private var worldDirectoryOverride: File? = null

    fun resolveLoadOrder(): List<File> {
        val globalDirectory = RuleFileLocator.resolveRulesDirectory()
        val userFile = File(globalDirectory, StackUpUpIds.USER_RULES_FILE_NAME)
        val primaryFile = File(globalDirectory, StackUpUpIds.RULES_FILE_NAME)
        val packFiles = resolvePackFiles(globalDirectory, userFile)
        val packMarkdownFiles = resolvePackMarkdownFiles(globalDirectory, userFile)
        val legacyFile = RuleFileLocator.resolveLegacy()
        val worldMdFile = resolveWorldMarkdownFile()
        val worldSuFile = resolveWorldFile()
        return buildList(packFiles.size + packMarkdownFiles.size + 3) {
            if (legacyFile.exists() && !primaryFile.exists()) {
                add(legacyFile)
            }
            if (worldMdFile?.exists() == true) {
                add(worldMdFile)
            }
            if (worldSuFile?.exists() == true) {
                add(worldSuFile)
            }
            packFiles.forEach(::add)
            packMarkdownFiles.forEach(::add)
            if (primaryFile.exists()) {
                add(primaryFile)
            }
            if (userFile.exists()) {
                add(userFile)
            }
        }
    }

    fun resolveWorldMarkdownFile(): File? {
        val worldDirectory = worldDirectoryOverride ?: currentWorldDirectory() ?: return null
        if (!worldDirectory.exists()) {
            return null
        }
        return File(File(File(worldDirectory, "data"), StackUpUpIds.MOD_ID), StackUpUpIds.WORLD_MARKDOWN_RULES_FILE_NAME)
    }

    fun resolveWorldFile(): File? {
        val worldDirectory = worldDirectoryOverride ?: currentWorldDirectory() ?: return null
        if (!worldDirectory.exists()) {
            return null
        }
        return File(File(File(worldDirectory, "data"), StackUpUpIds.MOD_ID), StackUpUpIds.WORLD_RULES_FILE_NAME)
    }

    fun setWorldDirectoryForTests(directory: File?) {
        worldDirectoryOverride = directory
    }

    private fun currentWorldDirectory(): File? {
        val server = FMLCommonHandler.instance().minecraftServerInstance ?: return null
        val world = runCatching { server.getWorld(0) }.getOrNull() ?: return null
        return runCatching { world.saveHandler.worldDirectory }.getOrNull()
    }

    private fun resolvePackFiles(globalDirectory: File, userFile: File): List<File> = globalDirectory
        .listFiles { file ->
            file.isFile &&
                file.extension == StackUpUpIds.RULE_FILE_EXTENSION &&
                !file.name.endsWith(".su.md") &&
                file.name != userFile.name &&
                file.name != StackUpUpIds.EXAMPLE_RULES_FILE_NAME
        }
        ?.sortedBy(File::getName)
        .orEmpty()

    private fun resolvePackMarkdownFiles(globalDirectory: File, userFile: File): List<File> = globalDirectory
        .listFiles { file ->
            file.isFile &&
                file.name.endsWith(".su.md") &&
                file.name != userFile.name &&
                file.name != StackUpUpIds.EXAMPLE_MARKDOWN_RULES_FILE_NAME
        }
        ?.sortedBy(File::getName)
        .orEmpty()
}
