# mod-capacity-audit

## 用途

用于 Minecraft 1.12.2 / Forge 容量与 Mixin 兼容调查。**只读审计**：不得修改源码、Mixin JSON、构建配置或第三方 JAR。

## 证据优先级

1. 先读项目源码和当前构建生成的映射源码，分别确认广告容量、真实写入路径和调用方。
2. 再核对实际运行/测试用 JAR 的文件名、版本和来源；实际 JAR 与预期版本不一致时，以实际 JAR 为准。
3. JAR 无可用源码、源码与实际字节码不一致或签名无法确认时，使用字节码作为兜底；不能用经验补全缺失事实。

简单读写优先使用内置文件工具。仅在确实能缩小范围或复核证据时使用 `fd`、`rg`、`bat`、`difft`、`jj`；Gradle 构建和测试经 Idea MCP 执行，不用 shell 裸跑 Gradle。

## 最短流程

1. 记录目标类、调用方、实际 JAR 版本和证据路径；版本不一致必须单独标出。
2. 对每个目标锁定**精确 owner、方法名、完整 JVM descriptor、Mixin 注册项和 `@At`**；重载不得只写方法名。
3. 从调用方追到实际写入路径：区分 `insertItem(..., false)`、`insertItem(..., true)`、`setStackInSlot` 与 vanilla setter；广告 getter、槽位限制和真实存储不得互相替代。记录特殊槽/实体槽的独立限制。
4. 只把 `simulate=false` 的真实调用纳入容量守恒，核对 `storedDelta + remainderCount == offered`；`simulate=true` 只能作为预测证据，绝不能当真实写入。
5. 审计结论逐目标标为 `SAFE`、`UNSAFE` 或 `UNKNOWN`：证据缺失、版本未闭合、descriptor/写入路径不明时必须是 `UNKNOWN`，不能为了推进修复而猜测。

## Mixin 核对

按目标适用时核对 JSON 到 Java、Java 到 JSON、目标 owner、完整方法 descriptor、`@At` 的 `target`/`ordinal`/`slice`/`shift`、`remap`/`@Pseudo`、`require`/`expect` 与实际字节码；适用项缺失才记问题。只凭类名、短方法名、默认 `64`、注释、槽位名称或广告值推断容量/兼容性均无效。源码无法证明时，字节码只用于确认实际 owner、descriptor、调用和字段访问，不得扩大结论。

## 输出契约

每个站点至少包含：

- 实际 JAR/版本（以及与预期版本是否一致）；
- owner、方法和完整 descriptor；
- handler/API 类型、广告面、真实写入面、`simulate` 实参及特殊槽限制；
- 真实 `insertItem(..., false)` 的 output/remainder 来源（具体返回值、余量对象、字段或调用链），不能只写守恒公式；
- 源码或字节码路径、行号/指令证据；
- `SAFE` / `UNSAFE` / `UNKNOWN`、理由和未闭合风险。

审计报告不得包含修复代码，不得把静态结构检查写成运行时行为通过；未执行的客户端/用户测试必须明确写为未执行。
