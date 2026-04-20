package io.alexjoest.stackupup.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoremodHierarchyBytecodeSafetyTest {
    @Test
    fun `类层级解析路径不应重新依赖 Kotlin 集合与高阶函数运行时`() {
        val coreClassDirectory = Paths.get("build", "classes", "kotlin", "main", "io", "alexjoest", "stackupup", "core")
        assertTrue(Files.isDirectory(coreClassDirectory), "缺少主源码编译产物: $coreClassDirectory")

        val targetClasses =
            Files.walk(coreClassDirectory).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter { it.fileName.toString().endsWith(".class") }
                    .filter(::isHierarchyRuntimeClass)
                    .sorted()
                    .collect(Collectors.toList())
            }

        assertFalse(targetClasses.isEmpty(), "未找到类层级解析相关的 class 文件")

        val forbiddenReferences =
            listOf(
                "kotlin/collections/",
                "kotlin/sequences/",
                "kotlin/text/",
                "kotlin/io/",
                "kotlin/ranges/",
                "kotlin/jvm/functions/",
                "kotlin/jvm/internal/Lambda",
                "kotlin/jvm/internal/Ref${'$'}BooleanRef",
                "kotlin/jvm/internal/FunctionReferenceImpl",
                "kotlin/jvm/internal/DefaultConstructorMarker"
            )

        val violations = ArrayList<String>()
        for (targetClass in targetClasses) {
            val classContent = String(Files.readAllBytes(targetClass), StandardCharsets.ISO_8859_1)
            for (forbiddenReference in forbiddenReferences) {
                if (classContent.contains(forbiddenReference)) {
                    violations += "${targetClass.fileName}: $forbiddenReference"
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "类层级解析路径重新引入了 Kotlin 运行时依赖:\n${violations.joinToString(separator = "\n")}"
        )

        val repositoryClass = targetClasses.firstOrNull { it.fileName.toString().startsWith("ClassHierarchyRepository") }
        assertTrue(repositoryClass != null, "缺少 ClassHierarchyRepository 编译产物")
        val repositoryContent = String(Files.readAllBytes(repositoryClass), StandardCharsets.ISO_8859_1)
        assertFalse(
            repositoryContent.contains("org/objectweb/asm/tree/"),
            "ClassHierarchyRepository 不应再依赖 asm.tree 读取完整类树"
        )
    }

    private fun isHierarchyRuntimeClass(path: Path): Boolean {
        val fileName = path.fileName.toString()
        return fileName.startsWith("ClassHierarchyRepository")
            || fileName.startsWith("TypeRelationshipResolver")
            || fileName.startsWith("FixedCompatTargets")
            || fileName.startsWith("DynamicCompatMethodProbe")
    }
}
