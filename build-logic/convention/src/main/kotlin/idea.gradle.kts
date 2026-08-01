// IDEA 集成：运行配置、javac encoding 与 processIdeaSettings 接线。
// 行为与重构前根脚本一致；运行任务名经 Properties.kt 的 E2 常量集中。

import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.compiler
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings

plugins {
    id("org.jetbrains.gradle.plugin.idea-ext")
}

idea {
    module {
        inheritOutputDirs = true
    }
    project {
        settings {
            runConfigurations {
                listOf(
                    "1. Run Client" to taskRunClient,
                    "2. Run Server" to taskRunServer,
                    "2a. Run Server AutoTest Matrix" to taskRunServerAutoTestMatrix,
                    "3. Run Obfuscated Client" to taskRunObfClient,
                    "4. Run Obfuscated Server" to taskRunObfServer,
                ).forEach { (name, taskName) ->
                    add(Gradle(name).apply { setProperty("taskNames", listOf(taskName)) })
                }
            }
            compiler.javac {
                afterEvaluate {
                    javacAdditionalOptions = "-encoding utf8"
                    moduleJavacAdditionalOptions =
                        mutableMapOf(
                            (project.name + ".main") to
                                tasks
                                    .named<JavaCompile>("compileJava")
                                    .get()
                                    .options.compilerArgs
                                    .joinToString(" ") { "\"$it\"" },
                        )
                }
            }
        }
    }
}

tasks.named("processIdeaSettings").configure {
    dependsOn("injectTags")
}
