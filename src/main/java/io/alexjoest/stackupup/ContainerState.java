package io.alexjoest.stackupup;

public final class ContainerState {
    public static final ThreadLocal<ContainerMergeShrink> pendingMergeShrink = new ThreadLocal<>();
    public static final ThreadLocal<Integer> pendingDragRemainder = new ThreadLocal<>();
    public static final ThreadLocal<Integer> pendingSwapRemainder = new ThreadLocal<>();
    public static final ThreadLocal<Integer> pendingOutsideDropRemainder = new ThreadLocal<>();

    public static void clear() {
        pendingMergeShrink.remove();
        pendingDragRemainder.remove();
        pendingSwapRemainder.remove();
        pendingOutsideDropRemainder.remove();
    }

    private ContainerState() {}
}
