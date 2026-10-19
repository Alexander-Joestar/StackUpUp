# mod-capacity-fix

## 用途

用于在 `mod-capacity-audit` 已给出明确 `UNSAFE` 且目标签名和写入路径已闭合后，修复 Minecraft 1.12.2 / Forge 容量或 Mixin 兼容问题。若结论是 `UNKNOWN`，先补证据，禁止以猜测推动修复。

## 证据与范围

先复核实际运行/测试 JAR 的版本；源码优先，实际 JAR 与预期版本不一致、源码缺失或签名不一致时，以实际字节码确认 owner、完整 JVM descriptor 和调用点。只改为解决该 `UNSAFE` 所必需的最小 Mixin/handler 与其注册项，不顺手重构或扩大兼容范围。

简单读写使用内置文件工具；`fd`、`rg`、`bat`、`difft`、`jj` 仅在确实有助于定位或复核时使用。Gradle 构建、测试和同步经 Idea MCP 执行，不用 shell 裸跑 Gradle。

## 最短流程

1. 复核审计证据、实际 JAR、owner、完整 descriptor、`@At` 和真实写入路径；不闭合就停止并保留 `UNKNOWN`。
2. 选择一个最小、显式的 Mixin 注入点：包裹原调用用 `@WrapOperation`，改变表达式结果用 `@ModifyExpressionValue`；不得新增 `@Redirect`，不得按类名或方法名猜目标。
3. 保留目标原有的特殊槽、实体槽、白名单/黑名单和其他槽位限制；只修复已证明的容量路径，不把特殊限制推广成全局规则。
4. 对外广告容量不得大于真实写入容量。区分 `simulate` 与真实写入，真实 `insertItem(..., false)` 必须保持 `storedDelta + remainderCount == offered`；禁止写入后重算余量、吞物、回填、重试或补偿。
5. 静态验证完成后，再交给用户运行真实场景；两类验证分开记录。

## 明确禁止

- 禁止广泛修改 `getInventoryStackLimit`、`getSlotLimit` 或其他 getter 来全局扩容；只能收敛到审计已证明的具体写入调用点。
- 禁止新增 ASM/coremod 字节码改写、反射、动态扫描或未注册的兼容链路。
- 禁止吞物、静默丢失、写入后重试、回填、补偿或把 `simulate=true` 当作成功写入。
- Java Mixin handler 保持 `private static` 和项目既有纯 Java 约束，不引入 Kotlin 集合/IO、lambda 或方法引用。

## 验证与输出

**静态验证**（代理可执行）：检查 JSON 注册、Mixin owner/descriptor/`@At`、编译和 annotation processor/refmap；必要时检查生成字节码结构，并运行项目指定的 Gradle/Idea MCP 检查。静态通过只证明结构和编译，不证明实际容器行为。

**用户运行测试**（必须单独列出）：由用户在实际 1.12.2 modpack/JAR 组合中运行目标插入、特殊槽和余量场景；未实际运行不得写成通过。若依赖缺失、版本不一致或无法运行，写明 `UNKNOWN`/`BLOCKED` 及缺失项。

修复交付至少说明：修改的精确 Mixin/descriptor/写入路径、保留的特殊槽限制、静态验证结果、用户运行测试结果和剩余风险。