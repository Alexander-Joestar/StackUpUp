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
        val packFiles = globalDirectory
            .listFiles { file -> file.isFile && file.extension == StackUpUpIds.RULE_FILE_EXTENSION && file.name != userFile.name }
            ?.sortedBy(File::getName)
            .orEmpty()
            .toMutableList()

        val legacyFile = RuleFileLocator.resolveLegacy()
        if (legacyFile.exists() && !primaryFile.exists()) {
            packFiles.add(0, legacyFile)
        }

        val worldFile = resolveWorldFile()
        return buildList {
            addAll(packFiles.filter(File::exists))
            worldFile?.takeIf(File::exists)?.let(::add)
            userFile.takeIf(File::exists)?.let(::add)
        }
    }

    fun resolveWorldFile(): File? {
        val worldDirectory = worldDirectoryOverride ?: currentWorldDirectory() ?: return null
        return File(worldDirectory, "data/${StackUpUpIds.MOD_ID}/${StackUpUpIds.WORLD_RULES_FILE_NAME}")
    }

    fun setWorldDirectoryForTests(directory: File?) {
        worldDirectoryOverride = directory
    }

    private fun currentWorldDirectory(): File? {
        val server = FMLCommonHandler.instance().minecraftServerInstance ?: return null
        val world = runCatching { server.getWorld(0) }.getOrNull() ?: return null
        return world.saveHandler.worldDirectory
    }
}
