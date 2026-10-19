package io.alexjoest.stackupup.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Runtime-only state used by the narrow inventory transformer.
 *
 * <p>The transformer can be loaded while Forge is still constructing its class loader. This
 * class therefore has no Forge, Minecraft, Kotlin, or configuration dependency. The mod can
 * publish a prepared immutable snapshot once its configuration is ready.</p>
 */
public final class InventoryStackLimitHook {
    private static final int VANILLA_STACK_LIMIT = 64;
    private static volatile State state = State.unprepared();
    private static volatile boolean expansionEnabled;
    private static volatile boolean expansionDecisionFrozen;

    private InventoryStackLimitHook() {
    }

    /** Publishes the startup-only compatibility expansion decision. */
    public static synchronized void configureExpansionEnabled(boolean enabled) {
        if (expansionDecisionFrozen && enabled) {
            return;
        }
        expansionEnabled = enabled;
        expansionDecisionFrozen = true;
    }

    public static boolean isExpansionEnabled() {
        return expansionEnabled;
    }

    public static void prepare(int compatibilityLimit, String[] blacklistedPackages) {
        if (compatibilityLimit <= 0) {
            throw new IllegalArgumentException("compatibilityLimit must be positive");
        }
        state = State.prepared(compatibilityLimit, blacklistedPackages);
    }

    public static void setCompatibilityStackSize(int compatibilityLimit) {
        if (compatibilityLimit <= 0) {
            throw new IllegalArgumentException("compatibilityLimit must be positive");
        }
        State previous = state;
        state = new State(true, compatibilityLimit, previous.blacklistedPackages);
    }

    public static boolean isPrepared() {
        return state.prepared;
    }

    public static int resolveInventoryStackLimit(String className, int original) {
        State current = state;
        if (!expansionEnabled || !current.prepared || original != VANILLA_STACK_LIMIT
            || className == null || current.matches(className)) {
            return original;
        }
        return current.compatibilityLimit;
    }

    public static int resolveInventoryStackLimit(Class<?> receiverClass, int original) {
        State current = state;
        if (!expansionEnabled || !current.prepared || original != VANILLA_STACK_LIMIT
            || receiverClass == null || current.bypasses(receiverClass)) {
            return original;
        }
        return current.compatibilityLimit;
    }

    public static boolean isBlacklisted(Class<?> receiverClass) {
        return state.bypasses(receiverClass);
    }

    static synchronized void resetForTesting() {
        state = State.unprepared();
        expansionEnabled = false;
        expansionDecisionFrozen = false;
    }

    private static final class State {
        private final boolean prepared;
        private final int compatibilityLimit;
        private final Set<String> blacklistedPackages;
        private final ClassValue<Boolean> classResults = new ClassValue<Boolean>() {
            @Override
            protected Boolean computeValue(Class<?> receiverClass) {
                return matches(receiverClass.getName());
            }
        };

        private boolean bypasses(Class<?> receiverClass) {
            return receiverClass != null && classResults.get(receiverClass);
        }

        private State(boolean prepared, int compatibilityLimit, Set<String> blacklistedPackages) {
            this.prepared = prepared;
            this.compatibilityLimit = compatibilityLimit;
            this.blacklistedPackages = blacklistedPackages;
        }

        private static State unprepared() {
            return new State(false, VANILLA_STACK_LIMIT, Collections.<String>emptySet());
        }

        private static State prepared(int compatibilityLimit, String[] packageNames) {
            Set<String> normalizedPackages = normalize(packageNames);
            return new State(
                true,
                compatibilityLimit,
                Collections.unmodifiableSet(normalizedPackages)
            );
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
