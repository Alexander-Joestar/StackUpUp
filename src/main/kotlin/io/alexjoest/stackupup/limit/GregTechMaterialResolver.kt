package io.alexjoest.stackupup.limit

import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.Loader
import java.lang.reflect.Field
import java.lang.reflect.Method

object GregTechMaterialResolver {
    private const val GREGTECH_MOD_ID = "gregtech"
    private const val ORE_DICT_UNIFIER_CLASS_NAME = "gregtech.api.unification.OreDictUnifier"
    private val MATERIAL_STACK_CLASS_NAMES = listOf(
        "gregtech.api.unification.stack.MaterialStack",
        "gregtech.api.unification.material.MaterialStack",
    )
    private const val META_ITEM_CLASS_NAME = "gregtech.api.items.metaitem.MetaItem"
    private const val MATERIAL_NAME_FIELD = "materialName"

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
        private val materialField: Field,
    ) {
        fun resolve(stack: ItemStack): String =
            runCatching {
                val materialStack = getMaterial.invoke(null, stack) ?: return@runCatching ""
                val material = materialField.get(materialStack) ?: return@runCatching ""
                materialName(material)
            }.getOrDefault("")

        companion object {
            fun create(): PrimaryHandles? =
                runCatching {
                    val oreDictUnifierClass = Class.forName(ORE_DICT_UNIFIER_CLASS_NAME)
                    val materialStackClass = MATERIAL_STACK_CLASS_NAMES.firstNotNullOfOrNull { className ->
                        runCatching { Class.forName(className) }.getOrNull()
                    } ?: return@runCatching null
                    PrimaryHandles(
                        getMaterial = oreDictUnifierClass.getMethod("getMaterial", ItemStack::class.java),
                        materialField = materialStackClass.field("material"),
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
            ?: stringField(valueItem, MATERIAL_NAME_FIELD)
            ?: ""

    private fun materialObject(valueItem: Any): Any? =
        methodResult(valueItem, "getMaterial")
            ?: methodResult(valueItem, "material")
            ?: fieldValue(valueItem, "material")

    private fun materialName(material: Any): String =
        methodResult(material, "getRegistryName")?.toStableMaterialId()
            ?: stringMethod(material, "getName")
            ?: stringField(material, "name")
            ?: material.toString().takeUnless { it.isBlank() }
            ?: ""

    private fun Any.toStableMaterialId(): String? =
        toString().takeUnless { it.isBlank() }

    private fun stringMethod(target: Any, name: String): String? =
        (methodResult(target, name) as? String)?.takeUnless { it.isBlank() }

    private fun methodResult(target: Any, name: String): Any? =
        runCatching { target.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target) }
            .getOrNull()

    private fun stringField(target: Any, name: String): String? =
        (fieldValue(target, name) as? String)?.takeUnless { it.isBlank() }

    private fun fieldValue(target: Any, name: String): Any? =
        runCatching { target.javaClass.field(name).get(target) }.getOrNull()

    private fun Class<*>.field(name: String): Field =
        getDeclaredField(name).apply { isAccessible = true }
}
