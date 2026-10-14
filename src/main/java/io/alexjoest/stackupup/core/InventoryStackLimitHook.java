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

    /**
     * Publishes the startup expansion decision. The first decision is frozen so a default-off
     * startup or a runtime blacklist disable cannot be reopened by a later caller.
     */
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

    public static synchronized void disableExpansionForRuntimeBlacklist() {
        expansionEnabled = false;
        expansionDecisionFrozen = true;
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

    public static void setBlacklistedPackages(String[] blacklistedPackages) {
        State previous = state;
        state = State.prepared(previous.compatibilityLimit, blacklistedPackages);
    }

    public static boolean isPrepared() {
        return state.prepared;
    }

    /**
     * Called from transformed bytecode. The class name is passed as a string deliberately so
     * resolving a transformed inventory never loads the target class or consults Forge.
     */
    public static int resolveInventoryStackLimit(String className, int original) {
        State current = state;
        if (!expansionEnabled || !current.prepared || original != VANILLA_STACK_LIMIT
            || className == null || current.matches(className)) {
            return original;
        }
        return current.compatibilityLimit;
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
