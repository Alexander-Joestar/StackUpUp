package io.alexjoest.stackupup.mixin.late;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Pseudo
@Mixin(
    targets = {
        "appeng.container.implementations.ContainerPatternTerm",
        "appeng.container.implementations.ContainerExpandedProcessingPatternTerm",
        "appeng.container.implementations.ContainerWirelessPatternTerminal"
    },
    remap = false
)
abstract class AppEngPatternTermMixin {
    @Inject(method = "<init>*", at = @At("RETURN"), require = 0)
    private void stackupup$expandBlankPatternSlotLimit(CallbackInfo ci) {
        Object patternSlotIN = stackupup$getBlankPatternSlot();
        if (patternSlotIN != null) {
            stackupup$setStackLimit(patternSlotIN, StackLimitHooks.getCompatibilityStackSize());
        }
    }

    @Unique
    private Object stackupup$getBlankPatternSlot() {
        Class<?> current = this.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField("patternSlotIN");
                field.setAccessible(true);
                return field.get(this);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
        return null;
    }

    @Unique
    private static void stackupup$setStackLimit(Object slot, int limit) {
        try {
            Method method = slot.getClass().getMethod("setStackLimit", int.class);
            method.invoke(slot, limit);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
