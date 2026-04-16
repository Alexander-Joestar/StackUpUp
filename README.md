## StackUpUp

StackUpUp 是 [StackUp](https://github.com/asiekierka/StackUp) 在 **Minecraft 1.12.2** 上的现代化 Kotlin 分支。

当前重构目标：

1. 逐步替换旧版按 `Item` 生效的堆叠规则。
2. 新增支持 `metadata`、GregTech `gt.metaitem.*` 与矿物辞典的 DSL v2。
3. 逐步接入 `MixinBooter`，缩减旧 ASM 的职责范围。

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

### 规则系统

当前提供两套规则入口：

1. 旧版 StackUp 脚本目录：`config/stackup/`
2. 新版 DSL v2 规则文件：`config/stackup/rules.su`

注意：

1. 目标版本是 **1.12.2**，仅支持 **矿物辞典**，不支持高版本标签。
2. 旧脚本格式处于过渡兼容阶段，建议逐步迁移到 DSL v2。
3. `DSL v2` 的迁移说明与示例见 [docs/DSL-v2-迁移说明.md](docs/DSL-v2-迁移说明.md) 与 [docs/DSL-v2-规则示例.md](docs/DSL-v2-规则示例.md)。
