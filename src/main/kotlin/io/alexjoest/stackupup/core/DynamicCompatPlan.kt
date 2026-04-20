package io.alexjoest.stackupup.core

import org.objectweb.asm.tree.ClassNode
import java.util.function.Consumer

internal data class DynamicCompatPlan(
    val patches: List<Consumer<ClassNode>>
) {
    val hasPatches: Boolean = patches.isNotEmpty()
}
