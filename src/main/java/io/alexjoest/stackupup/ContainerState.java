package io.alexjoest.stackupup;

public final class ContainerState {
    public static final ThreadLocal<ContainerMergeShrink> pendingMergeShrink = new ThreadLocal<>();

    public static void clear() {
        pendingMergeShrink.remove();
    }

    private ContainerState() {}
}
