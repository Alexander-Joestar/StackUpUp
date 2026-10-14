package io.alexjoest.stackupup.compat.ic2classic;

public final class SlotBaseNativeLimit {
    private static final int VANILLA_STACK_LIMIT = 64;

    private SlotBaseNativeLimit() {
    }

    public static int clamp(int original) {
        return original > VANILLA_STACK_LIMIT ? VANILLA_STACK_LIMIT : original;
    }
}
