package io.alexjoest.stackupup.core

internal fun toDotClassName(name: String): String {
    val builder = StringBuilder(name.length)
    for (char in name) {
        builder.append(if (char == '/') '.' else char)
    }
    return builder.toString()
}

