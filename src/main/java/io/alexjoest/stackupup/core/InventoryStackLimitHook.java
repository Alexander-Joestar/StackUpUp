package io.alexjoest.stackupup.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Runtime hook and immutable policy used by the narrow inventory transformer.
 *
 * <p>The transformer can be loaded while Forge is still constructing its class loader. This
 * class therefore has no Forge, Minecraft, Kotlin, or configuration dependency. The mod can
 * publish a prepared immutable snapshot once its configuration is ready.</p>
 */
public final class InventoryStackLimitHook {
    private static final int VANILLA_STACK_LIMIT = 64;
    private static volatile ClassLoadPolicy classLoadPolicy = ClassLoadPolicy.unprepared();
    private static volatile boolean expansionDecisionFrozen;

    private InventoryStackLimitHook() {
    }

    /** Publishes the startup-only compatibility expansion decision. */
    public static synchronized void configureExpansionEnabled(boolean enabled) {
        if (expansionDecisionFrozen && enabled) {
            return;
        }
        ClassLoadPolicy previous = classLoadPolicy;
        classLoadPolicy = previous.withExpansionEnabled(enabled);
        expansionDecisionFrozen = true;
    }

    /**
     * Returns the immutable snapshot consumed by classes transformed after startup policy setup.
     * The snapshot uses class names rather than {@code Class<?>} values so callers can inspect
     * hierarchy metadata without resolving or initializing the target class.
     */
    public static ClassLoadPolicy classLoadPolicy() {
        return classLoadPolicy;
    }

    public static boolean isExpansionEnabled() {
        return classLoadPolicy.isExpansionEnabled();
    }

    public static synchronized void prepare(int compatibilityLimit, String[] blacklistedPackages) {
        requirePositiveLimit(compatibilityLimit);
        ClassLoadPolicy previous = classLoadPolicy;
        classLoadPolicy = ClassLoadPolicy.prepared(
            previous.isExpansionEnabled(),
            compatibilityLimit,
            blacklistedPackages
        );
    }

    public static synchronized void setCompatibilityStackSize(int compatibilityLimit) {
        requirePositiveLimit(compatibilityLimit);
        classLoadPolicy = classLoadPolicy.withCompatibilityLimit(compatibilityLimit);
    }

    public static boolean isPrepared() {
        return classLoadPolicy.isPrepared();
    }

    public static int resolveInventoryStackLimit(String className, int original) {
        return classLoadPolicy.resolveInventoryStackLimit(className, original);
    }

    public static int resolveInventoryStackLimit(Class<?> receiverClass, int original) {
        return receiverClass == null
            ? original
            : classLoadPolicy.resolveInventoryStackLimit(receiverClass.getName(), original);
    }

    public static boolean isBlacklisted(Class<?> receiverClass) {
        return receiverClass != null && classLoadPolicy.isBlacklisted(receiverClass.getName());
    }

    static synchronized void resetForTesting() {
        classLoadPolicy = ClassLoadPolicy.unprepared();
        expansionDecisionFrozen = false;
    }

    private static void requirePositiveLimit(int compatibilityLimit) {
        if (compatibilityLimit <= 0) {
            throw new IllegalArgumentException("compatibilityLimit must be positive");
        }
    }

    /** Immutable class-load-time policy for the narrow inventory capacity transformer. */
    public static final class ClassLoadPolicy {
        private final boolean expansionEnabled;
        private final boolean prepared;
        private final int compatibilityLimit;
        private final Set<String> blacklistedPackages;

        private ClassLoadPolicy(
            boolean expansionEnabled,
            boolean prepared,
            int compatibilityLimit,
            Set<String> blacklistedPackages
        ) {
            this.expansionEnabled = expansionEnabled;
            this.prepared = prepared;
            this.compatibilityLimit = compatibilityLimit;
            this.blacklistedPackages = blacklistedPackages;
        }

        public boolean isExpansionEnabled() {
            return expansionEnabled;
        }

        public boolean isPrepared() {
            return prepared;
        }

        public int getCompatibilityLimit() {
            return compatibilityLimit;
        }

        /** Returns whether this exact class name belongs to a blacklisted owned package. */
        public boolean isBlacklisted(String className) {
            return className != null && matches(className);
        }

        /** Returns whether a class-load-time capacity patch may be applied to this class. */
        public boolean shouldPatch(String className) {
            return expansionEnabled && prepared && className != null && !matches(className);
        }

        /** Resolves a legacy runtime getter value using this immutable class-name policy. */
        public int resolveInventoryStackLimit(String className, int original) {
            if (!shouldPatch(className) || original != VANILLA_STACK_LIMIT) {
                return original;
            }
            return compatibilityLimit;
        }

        private ClassLoadPolicy withExpansionEnabled(boolean enabled) {
            return new ClassLoadPolicy(enabled, prepared, compatibilityLimit, blacklistedPackages);
        }

        private ClassLoadPolicy withCompatibilityLimit(int limit) {
            return new ClassLoadPolicy(expansionEnabled, true, limit, blacklistedPackages);
        }

        private static ClassLoadPolicy unprepared() {
            return new ClassLoadPolicy(false, false, VANILLA_STACK_LIMIT, Collections.<String>emptySet());
        }

        private static ClassLoadPolicy prepared(
            boolean expansionEnabled,
            int compatibilityLimit,
            String[] packageNames
        ) {
            return new ClassLoadPolicy(
                expansionEnabled,
                true,
                compatibilityLimit,
                immutablePackages(normalize(packageNames))
            );
        }

        private static Set<String> immutablePackages(Set<String> packages) {
            if (packages.isEmpty()) {
                return Collections.emptySet();
            }
            return Collections.unmodifiableSet(packages);
        }

        private static Set<String> normalize(String[] names) {
            Set<String> normalized = new HashSet<>();
            if (names != null) {
                for (String name : names) {
                    if (name == null) {
                        continue;
                    }
                    String value = name.trim().toLowerCase(Locale.ROOT).replace('/', '.');
                    if (!value.isEmpty()) {
                        normalized.add(value);
                    }
                }
            }
            return normalized;
        }

        private boolean matches(String className) {
            if (blacklistedPackages.isEmpty()) {
                return false;
            }
            String normalizedClassName = className.replace('/', '.').toLowerCase(Locale.ROOT);
            for (String packageName : blacklistedPackages) {
                if (normalizedClassName.equals(packageName)
                    || (normalizedClassName.startsWith(packageName)
                        && normalizedClassName.length() > packageName.length()
                        && (normalizedClassName.charAt(packageName.length()) == '.'
                            || normalizedClassName.charAt(packageName.length()) == '$'))) {
                    return true;
                }
            }
            return false;
        }
    }
}
