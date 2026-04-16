package pl.asie.stackup.limit

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
}
