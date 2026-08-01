// JVM：Java 8 toolchain + UTF-8 + sources jar。行为与重构前根脚本 java{} 块一致。

plugins {
    id("java")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    // Generate sources and javadocs jars when building and publishing
    withSourcesJar()
    // withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
