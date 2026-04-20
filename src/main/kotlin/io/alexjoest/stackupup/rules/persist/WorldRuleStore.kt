package io.alexjoest.stackupup.rules.persist

import java.io.File

class WorldRuleStore(
    file: File
) {
    private val blockStore = RuleBlockFileStore(file)

    fun replaceSourceBlock(sourceId: String, lines: List<String>) {
        blockStore.replaceBlock(
            RuleTextBlock(
                id = sourceId,
                lines = lines
            )
        )
    }

    fun readBlocks(): List<RuleTextBlock> = blockStore.readBlocks()
}
