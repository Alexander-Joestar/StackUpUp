package io.alexjoest.stackupup.limit

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.Loader
import java.lang.reflect.Method

object GregTechMaterialResolver {
    private const val GREGTECH_MOD_ID = "gregtech"
    private const val ORE_DICT_UNIFIER_CLASS_NAME = "gregtech.api.unification.OreDictUnifier"
    private const val META_ITEM_CLASS_NAME = "gregtech.api.items.metaitem.MetaItem"

    @Volatile
    private var handles: ReflectionHandles? = null
    @Volatile
    private var reflectionUnavailable = false
    @Volatile
    private var resolverOverride: ((ItemStack) -> String)? = null

    @JvmStatic
    fun resolveMaterial(stack: ItemStack): String {
        resolverOverride?.let { return it(stack) }
        return resolveMaterial(stack, isGregTechLoaded())
    }

    private fun resolveMaterial(stack: ItemStack, gregTechLoaded: Boolean): String {
        if (stack.isEmpty || !gregTechLoaded || reflectionUnavailable) {
            return ""
        }

        val currentHandles = handles ?: initializeHandles() ?: return ""
        return currentHandles.resolve(stack)
    }

    private fun isGregTechLoaded(): Boolean =
        runCatching { Loader.isModLoaded(GREGTECH_MOD_ID) }.getOrDefault(false)

    @Synchronized
    private fun initializeHandles(): ReflectionHandles? {
        handles?.let { return it }
        if (reflectionUnavailable) {
            return null
        }

        val primary = PrimaryHandles.create()
        val fallback = FallbackHandles.create()
        if (primary == null && fallback == null) {
            reflectionUnavailable = true
            return null
        }

        return ReflectionHandles(primary = primary, fallback = fallback).also {
            handles = it
        }
    }

    internal fun installResolverForTesting(resolver: (ItemStack) -> String): () -> Unit {
        val previous = resolverOverride
        resolverOverride = resolver
        return { resolverOverride = previous }
    }

    internal fun resetResolverForTesting() {
        resolverOverride = null
    }

    internal fun resolveMaterialForTesting(stack: ItemStack, gregTechLoaded: Boolean): String =
        resolveMaterial(stack, gregTechLoaded)

    @Synchronized
    internal fun resetReflectionForTesting() {
        handles = null
        reflectionUnavailable = false
    }

    internal fun materialNameForTesting(material: Any): String = materialName(material)

    private data class ReflectionHandles(
        private val primary: PrimaryHandles?,
        private val fallback: FallbackHandles?,
    ) {
        fun resolve(stack: ItemStack): String =
            primary?.resolve(stack).orEmpty().ifEmpty { fallback?.resolve(stack).orEmpty() }
    }

    private data class PrimaryHandles(
        private val getMaterial: Method,
    ) {
        fun resolve(stack: ItemStack): String =
            runCatching {
                resolveMaterialStack(getMaterial.invoke(null, stack))
            }.getOrDefault("")

        companion object {
            fun create(): PrimaryHandles? =
                runCatching {
                    PrimaryHandles(
                        getMaterial = Class.forName(ORE_DICT_UNIFIER_CLASS_NAME)
                            .getMethod("getMaterial", ItemStack::class.java),
                    )
                }.getOrNull()
        }
    }

    private data class FallbackHandles(
        private val metaItemClass: Class<*>,
        private val getItem: Method,
    ) {
        fun resolve(stack: ItemStack): String =
            runCatching {
                val item = stack.item
                if (!metaItemClass.isInstance(item)) {
                    return@runCatching ""
                }

                val valueItem = getItem.invoke(item, stack) ?: return@runCatching ""
                materialNameFromValueItem(valueItem)
            }.getOrDefault("")

        companion object {
            fun create(): FallbackHandles? =
                runCatching {
                    val metaItemClass = Class.forName(META_ITEM_CLASS_NAME)
                    FallbackHandles(
                        metaItemClass = metaItemClass,
                        getItem = metaItemClass.getMethod("getItem", ItemStack::class.java),
                    )
                }.getOrNull()
        }
    }

    private fun materialNameFromValueItem(valueItem: Any): String =
        materialObject(valueItem)?.let { materialName(it) }
            ?: ""

    private fun materialObject(valueItem: Any): Any? =
        methodResult(valueItem, "getMaterial")

    private fun resolveMaterialStack(materialStack: Any?): String {
        if (materialStack == null) {
            return ""
        }
        return materialObject(materialStack)?.let { materialName(it) }.orEmpty()
    }

    private fun materialName(material: Any): String =
        methodResult(material, "getRegistryName")?.toRegistryMaterialId()
            ?: stringMethod(material, "getName")
            ?: ""

    private fun Any.toRegistryMaterialId(): String? =
        when (this) {
            is ResourceLocation -> toString()
            else -> takeIf { it.javaClass.name.contains("ResourceLocation") }
                ?.toString()
                ?.takeIf { it.isRegistryLikeId() }
        }

    private fun String.isRegistryLikeId(): Boolean =
        matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))

    private fun stringMethod(target: Any, name: String): String? =
        (methodResult(target, name) as? String)?.takeUnless { it.isBlank() }

    private fun methodResult(target: Any, name: String): Any? =
        runCatching { target.javaClass.getMethod(name).invoke(target) }.getOrNull()
}
