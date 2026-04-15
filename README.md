## StackUpUp

StackUp migrated onto the modern Kotlin mod template. Licensed under MIT.

This project currently uses **Gradle 9.3.0** + **[RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle) 2.0.2** + **Forge 14.23.5.2847**.

It requires a Java 25 runtime for Gradle.

With **coremod and mixin support** that is easy to configure.

### Instructions:

1. Click `use this template` at the top.
2. Clone the repository you have created with this template.
3. In the local repository, run the command `gradlew setupDecompWorkspace`
4. Open the project folder in IDEA.
5. Right-click in IDEA `build.gradle` of your project, and select `Link Gradle Project`, after completion, hit `Refresh All` in the gradle tab on the right.
6. Run `gradlew runClient` and `gradlew runServer`, or use the auto-imported run configurations in IntelliJ like `1. Run Client`.

### Mixins:

- When writing Mixins on IntelliJ, it is advisable to use latest [MinecraftDev Fork for RetroFuturaGradle](https://github.com/eigenraven/MinecraftDev/releases).
