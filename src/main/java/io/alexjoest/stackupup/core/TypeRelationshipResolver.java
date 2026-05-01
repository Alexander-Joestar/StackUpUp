package io.alexjoest.stackupup.core;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

final class TypeRelationshipResolver {

    private TypeRelationshipResolver() {}

    private static final ConcurrentHashMap<String, Boolean> implementsCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> extendsCache = new ConcurrentHashMap<>();

    static boolean implementsInterface(String className, String targetType) {
        String cacheKey = cacheKey(className, targetType);
        Boolean cached = implementsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean resolved = implementsRecursive(className, targetType, new HashSet<String>(8));
        Boolean previous = implementsCache.putIfAbsent(cacheKey, resolved);
        return previous != null ? previous : resolved;
    }

    private static boolean implementsRecursive(String className, String targetType, HashSet<String> visited) {
        String current = className;
        while (current != null && !current.isEmpty()) {
            if (!visited.add(current)) {
                return false;
            }

            if (current.equals(targetType)) {
                return true;
            }

            for (String implementedInterface : ClassHierarchyRepository.interfacesOf(current)) {
                if (implementsRecursive(implementedInterface, targetType, visited)) {
                    return true;
                }
            }
            current = ClassHierarchyRepository.superClassOf(current);
        }

        return false;
    }

    static boolean extendsClass(String className, String targetType) {
        String cacheKey = cacheKey(className, targetType);
        Boolean cached = extendsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        HashSet<String> visited = new HashSet<>(8);
        String current = className;
        while (current != null && !current.isEmpty()) {
            if (!visited.add(current)) {
                extendsCache.putIfAbsent(cacheKey, false);
                return false;
            }

            if (current.equals(targetType)) {
                extendsCache.putIfAbsent(cacheKey, true);
                return true;
            }

            current = ClassHierarchyRepository.superClassOf(current);
        }

        extendsCache.putIfAbsent(cacheKey, false);
        return false;
    }

    private static String cacheKey(String className, String targetType) {
        return className + '>' + targetType;
    }
}
