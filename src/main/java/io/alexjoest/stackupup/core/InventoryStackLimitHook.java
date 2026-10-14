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

    private InventoryStackLimitHook() {
    }

    /**
     * Publishes the runtime snapshot used by transformed inventory methods.
     *
     * @param compatibilityLimit the configured StackUpUp compatibility upper bound
     * @param blacklistedPackages package prefixes whose inventory methods must remain unchanged
     */
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
        if (!current.prepared || className == null || current.matches(className)) {
            return original;
        }
        return current.compatibilityLimit;
    }

    static void resetForTesting() {
        state = State.unprepared();
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
            Set<String> normalized = new HashSet<>();
            if (packageNames != null) {
                for (String packageName : packageNames) {
                    if (packageName == null) {
                        continue;
                    }
                    String value = packageName.trim().toLowerCase(Locale.ROOT);
                    if (!value.isEmpty()) {
                        normalized.add(value.replace('/', '.'));
                    }
                }
            }
            return new State(true, compatibilityLimit, Collections.unmodifiableSet(normalized));
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
