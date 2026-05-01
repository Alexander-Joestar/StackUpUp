package io.alexjoest.stackupup.core;

/**
 * 字符串转换工具：在 '/' 和 '.' 之间互换类名表示。
 * 用纯 Java 编写，避免 Kotlin 扩展函数（String.replace）在 coremod 早期路径触发 ClassCircularityError。
 */
final class NameConverter {

    private NameConverter() {}

    static String toSlashName(String name) {
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            builder.append(c == '.' ? '/' : c);
        }
        return builder.toString();
    }

    static String toDotName(String name) {
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            builder.append(c == '/' ? '.' : c);
        }
        return builder.toString();
    }
}
