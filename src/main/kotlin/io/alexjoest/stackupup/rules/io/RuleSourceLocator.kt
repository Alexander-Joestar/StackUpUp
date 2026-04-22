package io.alexjoest.stackupup.rules.io

import java.io.File
import net.minecraftforge.fml.common.FMLCommonHandler
import io.alexjoest.stackupup.StackUpUpIds

object RuleSourceLocator {
    @Volatile
    private var worldDirectoryOverride: File? = null

    fun resolveLoadOrder(): List<File> {
        val globalDirectory = RuleFileLocator.resolveRulesDirectory()
        val userFile = File(globalDirectory, StackUpUpIds.USER_RULES_FILE_NAME)
        val primaryFile = File(globalDirectory, StackUpUpIds.RULES_FILE_NAME)
        val packFiles = resolvePackFiles(globalDirectory, userFile)
        val legacyFile = RuleFileLocator.resolveLegacy()
        val worldFile = resolveWorldFile()
        return buildList(packFiles.size + 3) {
            if (legacyFile.exists() && !primaryFile.exists()) {
                add(legacyFile)
            }
            packFiles.forEach(::add)
            if (worldFile?.exists() == true) {
                add(worldFile)
            }
            if (userFile.exists()) {
                add(userFile)
            }
        }
    }

    fun resolveWorldFile(): File? {
        val worldDirectory = worldDirectoryOverride ?: currentWorldDirectory() ?: return null
        return File(File(File(worldDirectory, "data"), StackUpUpIds.MOD_ID), StackUpUpIds.WORLD_RULES_FILE_NAME)
    }

    fun setWorldDirectoryForTests(directory: File?) {
        worldDirectoryOverride = directory
    }

    private fun currentWorldDirectory(): File? {
        val server = FMLCommonHandler.instance().minecraftServerInstance ?: return null
        val world = runCatching { server.getWorld(0) }.getOrNull() ?: return null
        return world.saveHandler.worldDirectory
    }

    private fun resolvePackFiles(globalDirectory: File, userFile: File): List<File> =
        globalDirectory
            .listFiles { file ->
                file.isFile && file.extension == StackUpUpIds.RULE_FILE_EXTENSION && file.name != userFile.name
            }
            ?.sortedBy(File::getName)
            .orEmpty()
}
