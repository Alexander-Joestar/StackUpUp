package io.alexjoest.stackupup.mixin.early;

final class ContainerState {
    static final ThreadLocal<Object> pendingMergeShrink = new ThreadLocal<>();
    static final ThreadLocal<Integer> pendingDragRemainder = new ThreadLocal<>();
    static final ThreadLocal<Integer> pendingSwapRemainder = new ThreadLocal<>();

    static void clear() {
        pendingMergeShrink.remove();
        pendingDragRemainder.remove();
        pendingSwapRemainder.remove();
    }

    private ContainerState() {}
}
