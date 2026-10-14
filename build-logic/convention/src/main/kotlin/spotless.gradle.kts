// 格式化与行尾强制：四组规则与重构前一致；kotlinGradle 的 target 覆盖根脚本与
// build-logic 的全部 *.gradle.kts（含 build-logic 子目录）。

plugins {
    id("com.diffplug.spotless")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "indent_size" to "4",
                    "max_line_length" to "160",
                    "ktlint_code_style" to "intellij_idea",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }

    java {
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target(
            fileTree(projectDir) {
                include("*.gradle.kts", "build-logic/**/*.gradle.kts")
                exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
            },
        )
        ktlint()
    }

    format("misc") {
        target(".gitattributes", ".gitignore", ".editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
