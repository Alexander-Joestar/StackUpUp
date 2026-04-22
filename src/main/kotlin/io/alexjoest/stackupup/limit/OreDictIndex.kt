package io.alexjoest.stackupup.limit

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.oredict.OreDictionary
import java.util.concurrent.ConcurrentHashMap

class OreDictIndex(
    private val loader: (String, Int) -> Set<String>,
    private val stackLoader: ((ItemStack) -> Set<String>)? = null
) {
    private val cache = ConcurrentHashMap<OreDictKey, Set<String>>()

    fun getOreNames(itemId: String, meta: Int): Set<String> {
        val key = OreDictKey(itemId, meta)
        return cache.computeIfAbsent(key) { cachedKey ->
            loader(cachedKey.itemId, cachedKey.meta)
        }
    }

    fun getOreNames(stack: ItemStack): Set<String> {
        if (stack.isEmpty) {
            return emptySet()
        }

        val itemId = stack.item.registryName?.toString() ?: return emptySet()
        val key = OreDictKey(itemId, stack.metadata)
        return cache.computeIfAbsent(key) {
            stackLoader?.invoke(stack) ?: loader(key.itemId, key.meta)
        }
    }

    fun debugCacheSize(): Int = cache.size

    private data class OreDictKey(
        val itemId: String,
        val meta: Int
    )

    companion object {
        @JvmStatic
        fun createDefault(): OreDictIndex {
            return OreDictIndex(
                loader = { itemId, meta ->
                    val item = try {
                        ForgeRegistries.ITEMS.getValue(ResourceLocation(itemId))
                    } catch (_: IllegalArgumentException) {
                        null
                    } ?: return@OreDictIndex emptySet()

                    // 只构造最小栈做矿物辞典查询，避免把运行时对象状态混进缓存键。
                    val stack = ItemStack(item, 1, meta)
                    readOreNames(stack)
                },
                stackLoader = ::readOreNames
            )
        }

        @JvmStatic
        fun fromStackLoader(loader: (ItemStack) -> Set<String>): OreDictIndex {
            return OreDictIndex({ _, _ -> emptySet() }, loader)
        }

        private fun readOreNames(stack: ItemStack): Set<String> {
            val oreIds = OreDictionary.getOreIDs(stack)
            if (oreIds.isEmpty()) {
                return emptySet()
            }

            val oreNames = LinkedHashSet<String>(oreIds.size)
            for (oreId in oreIds) {
                oreNames += OreDictionary.getOreName(oreId)
            }
            return oreNames
        }
    }
}

