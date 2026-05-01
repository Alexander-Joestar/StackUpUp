package io.alexjoest.stackupup.core;

/**
 * 仅过滤"确定不需要进入 StackUpUp coremod 处理链"的基础运行时类。
 *
 * 现在用纯 Java 编写，彻底杜绝 Kotlin stdlib 在 coremod 早期路径的类加载循环风险。
 */
public final class CoremodClassFilter {

    private CoremodClassFilter() {}

    private static final String[] SKIPPED_PREFIXES = {
        "java/",
        "javax/",
        "jdk/",
        "kotlin/",
        "org/spongepowered/",
        "sun/",
        "zone/rong/mixinbooter/"
    };

    public static boolean shouldSkip(String internalName) {
        if (internalName.isEmpty()) {
            return true;
        }

        for (String prefix : SKIPPED_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}
