package io.alexjoest.stackupup.core;

import net.minecraft.launchwrapper.Launch;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.ArrayList;

public final class InventoryExpansionStartupConfig {
    private static final boolean DEFAULT_ENABLED = true;
    private static volatile StartupPolicy startupPolicy;

    private InventoryExpansionStartupConfig() {
    }

    public static StartupPolicy load() {
        StartupPolicy current = startupPolicy;
        if (current != null) {
            return current;
        }
        synchronized (InventoryExpansionStartupConfig.class) {
            if (startupPolicy == null) {
                File configFile = new File(new File(Launch.minecraftHome, "config"), "stackupup.cfg");
                startupPolicy = load(configFile);
            }
            return startupPolicy;
        }
    }

    public static boolean loadEnabled() {
        return load().enabled;
    }

    static boolean loadEnabled(File configFile) {
        return load(configFile).enabled;
    }

    static StartupPolicy load(File configFile) {
        if (!configFile.isFile()) {
            return new StartupPolicy(DEFAULT_ENABLED, Collections.<String>emptyList());
        }
        Deque<String> categories = new ArrayDeque<>();
        boolean enabled = DEFAULT_ENABLED;
        boolean enabledSeen = false;
        List<String> modIds = new ArrayList<>();
        boolean modIdsSeen = false;
        boolean readingModIds = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                String lower = value.toLowerCase(java.util.Locale.ROOT);
                if (readingModIds) {
                    if (">".equals(value)) {
                        readingModIds = false;
                    } else {
                        String modId = value.trim();
                        if (!modId.matches("[a-z0-9_.-]+")) {
                            throw new IllegalStateException("Invalid inventory capacity blacklist mod ID in " + configFile + ": " + modId);
                        }
                        modIds.add(modId);
                    }
                } else if (lower.endsWith("{")) {
                    categories.push(lower.substring(0, lower.length() - 1).trim());
                } else if ("}".equals(value)) {
                    if (categories.isEmpty()) {
                        throw new IllegalStateException("Unbalanced category in " + configFile);
                    }
                    categories.pop();
                } else if (categories.size() == 2
                    && "inventoryexpansion".equals(categories.peek())
                    && "compat".equals(categories.toArray(new String[0])[1])
                    && lower.startsWith("b:enabled=")) {
                    String setting = lower.substring("b:enabled=".length());
                    if (!"true".equals(setting) && !"false".equals(setting)) {
                        throw new IllegalStateException("Invalid inventory expansion setting in " + configFile + ": " + setting);
                    }
                    enabled = Boolean.parseBoolean(setting);
                    enabledSeen = true;
                } else if (categories.size() == 2
                    && "inventorystacklimitblacklist".equals(categories.peek())
                    && "compat".equals(categories.toArray(new String[0])[1])
                    && lower.startsWith("s:modids")) {
                    if (modIdsSeen || !lower.matches("s:modids\\s*<")) {
                        throw new IllegalStateException("Invalid inventory capacity blacklist in " + configFile);
                    }
                    modIdsSeen = true;
                    readingModIds = true;
                }
            }
            if (readingModIds || !categories.isEmpty()) {
                throw new IllegalStateException("Unbalanced startup config structure in " + configFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read inventory expansion startup config " + configFile, e);
        }
        return new StartupPolicy(enabledSeen ? enabled : DEFAULT_ENABLED, modIds);
    }

    static void resetForTesting() {
        startupPolicy = null;
    }

    public static final class StartupPolicy {
        public final boolean enabled;
        public final List<String> modIds;
        public final boolean capacityAllowed;

        private StartupPolicy(boolean enabled, List<String> modIds) {
            this.enabled = enabled;
            this.modIds = Collections.unmodifiableList(new ArrayList<>(modIds));
            this.capacityAllowed = enabled;
        }
    }
}
