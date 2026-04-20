package io.alexjoest.stackupup.core

import java.util.HashSet
import java.util.concurrent.ConcurrentHashMap

internal object TypeRelationshipResolver {
    private val implementsCache = ConcurrentHashMap<String, Boolean>()
    private val extendsCache = ConcurrentHashMap<String, Boolean>()

    fun implements(className: String, targetType: String): Boolean {
        val cacheKey = cacheKey(className, targetType)
        val cached = implementsCache[cacheKey]
        if (cached != null) {
            return cached
        }

        val resolved = implements(className, targetType, HashSet<String>(8))
        val previous = implementsCache.putIfAbsent(cacheKey, resolved)
        return previous ?: resolved
    }

    private fun implements(className: String?, targetType: String, visited: MutableSet<String>): Boolean {
        var current: String? = className
        while (!current.isNullOrEmpty()) {
            if (!visited.add(current)) {
                return false
            }

            if (current == targetType) {
                return true
            }

            for (implementedInterface in ClassHierarchyRepository.interfacesOf(current)) {
                if (implements(implementedInterface, targetType, visited)) {
                    return true
                }
            }
            current = ClassHierarchyRepository.superClassOf(current)
        }

        return false
    }

    fun extends(className: String, targetType: String): Boolean {
        val cacheKey = cacheKey(className, targetType)
        val cached = extendsCache[cacheKey]
        if (cached != null) {
            return cached
        }

        val visited = HashSet<String>(8)
        var current: String? = className
        while (!current.isNullOrEmpty()) {
            if (!visited.add(current)) {
                extendsCache.putIfAbsent(cacheKey, false)
                return false
            }

            if (current == targetType) {
                extendsCache.putIfAbsent(cacheKey, true)
                return true
            }

            current = ClassHierarchyRepository.superClassOf(current)
        }

        extendsCache.putIfAbsent(cacheKey, false)
        return false
    }

    private fun cacheKey(className: String, targetType: String): String {
        return StringBuilder(className.length + targetType.length + 1)
            .append(className)
            .append('>')
            .append(targetType)
            .toString()
    }
}
