package io.alexjoest.stackupup.dev

import com.google.common.base.Defaults.defaultValue
import io.alexjoest.stackupup.core.FixedCompatTargets
import net.minecraft.item.ItemStack
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal fun hasClass(name: String): Boolean = try {
    loadClass(name)
    true
} catch (_: Throwable) {
    false
}

internal fun loadClass(name: String): Class<*> = Class.forName(name, false, DevCompatProbeRunner::class.java.classLoader)

internal fun findField(type: Class<*>, name: String): Field {
    var current: Class<*>? = type
    while (current != null) {
        try {
            return current.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            current = current.superclass
        }
    }
    throw NoSuchFieldException(name)
}

internal fun findMethod(type: Class<*>, names: Array<String>, vararg parameterTypes: Class<*>): Method {
    var current: Class<*>? = type
    while (current != null) {
        for (name in names) {
            try {
                return current.getMethod(name, *parameterTypes)
            } catch (_: NoSuchMethodException) {
            }

            try {
                val method = current.getDeclaredMethod(name, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (_: NoSuchMethodException) {
            }
        }
        current = current.superclass
    }

    throw NoSuchMethodException(names.joinToString(prefix = "[", postfix = "]"))
}

internal fun formatProbeThrowable(throwable: Throwable): String {
    val unwrapped = (throwable as? InvocationTargetException)?.targetException ?: throwable
    val message = unwrapped.message?.takeIf(String::isNotBlank)
    return if (message == null) unwrapped.javaClass.simpleName else "${unwrapped.javaClass.simpleName}: $message"
}

internal fun evaluateProbeAvailability(check: () -> Boolean): ProbeAvailability = try {
    if (check()) ProbeAvailability.available() else ProbeAvailability.missing()
} catch (throwable: Throwable) {
    ProbeAvailability.failed(formatProbeThrowable(throwable))
}

internal fun expectedFixedTargetProbeCoverage(): Set<String> = FixedCompatTargets.probeTargets().toSet()

internal fun appendProbeFailureCause(summary: String, throwable: Throwable?): String {
    if (throwable == null) {
        return summary
    }
    return "$summary 原因=${formatProbeThrowable(throwable)}"
}

internal val Method.safeNullValue: Any?
    get() = when (returnType) {
        ItemStack::class.java -> ItemStack.EMPTY
        else -> defaultValue(returnType)
    }
