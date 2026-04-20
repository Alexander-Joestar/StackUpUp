package io.alexjoest.stackupup.core

import org.objectweb.asm.tree.ClassNode
import java.util.function.Consumer

internal object DynamicCompatPlanBuilder {
    fun build(transformedName: String, basicClass: ByteArray? = null): List<Consumer<ClassNode>> {
        val patches = ArrayList<Consumer<ClassNode>>(4)
        collectDynamicPatches(transformedName, basicClass, patches)
        return patches
    }

    private fun collectDynamicPatches(
        transformedName: String,
        basicClass: ByteArray?,
        patches: MutableList<Consumer<ClassNode>>
    ) {
        val declaredProfiles = basicClass?.let(DynamicCompatMethodProbe::detectProfiles)
        if (declaredProfiles == DynamicCompatTargetProfile.NONE) {
            return
        }

        val profile = declaredProfiles?.let { DynamicCompatTargetClassifier.classify(transformedName, it) }
            ?: DynamicCompatTargetClassifier.classify(transformedName)
        val methods = DynamicCompatTargetProfile.methodsFor(profile) ?: return
        patches += CompatibilityLimitPatch.rewrite(*methods)
    }
}
