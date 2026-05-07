package io.alexjoest.stackupup;

public final class ContainerState {
    public static final ThreadLocal<ContainerMergeShrink> pendingMergeShrink = new ThreadLocal<>();
    public static final ThreadLocal<Integer> pendingDragRemainder = new ThreadLocal<>();
    public static final ThreadLocal<Integer> pendingSwapRemainder = new ThreadLocal<>();
    /**
     * slotClick 进入 slotId == -999 路径时设为 true，
     * WrapOperation 处理器遇到此值时直接放行原调用。
     */
    public static final ThreadLocal<Boolean> isDropOperation = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() { return false; }
    };

    public static void clear() {
        pendingMergeShrink.remove();
        pendingDragRemainder.remove();
        pendingSwapRemainder.remove();
        isDropOperation.remove();
    }

    private ContainerState() {}
}
