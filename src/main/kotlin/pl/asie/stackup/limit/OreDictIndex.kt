package pl.asie.stackup.limit

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.oredict.OreDictionary
import java.util.concurrent.ConcurrentHashMap

class OreDictIndex(
    private val loader: (String, Int) -> Set<String>
) {
    private val cache = ConcurrentHashMap<Pair<String, Int>, Set<String>>()

    fun getOreNames(itemId: String, meta: Int): Set<String> {
        return cache.computeIfAbsent(itemId to meta) { (cachedItemId, cachedMeta) ->
            loader(cachedItemId, cachedMeta)
        }
    }

    fun debugCacheSize(): Int = cache.size

    companion object {
        @JvmStatic
        fun createDefault(): OreDictIndex {
            return OreDictIndex { itemId, meta ->
                val item = try {
                    ForgeRegistries.ITEMS.getValue(ResourceLocation(itemId))
                } catch (_: IllegalArgumentException) {
                    null
                } ?: return@OreDictIndex emptySet()

                // 这里只构造最小栈做矿物辞典查询，避免把运行时对象状态混进缓存键。
                val stack = ItemStack(item, 1, meta)
                OreDictionary.getOreIDs(stack)
                    .map(OreDictionary::getOreName)
                    .toSet()
            }
        }
    }
}
