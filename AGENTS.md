# AGENTS.md

## 范围与证据

本文件是仓库根级规范，适用于调查、计划、文档和实现任务；用户要求优先，其次是本文件与更深层 `AGENTS.md`。
证据只有源码、测试和运行输出；不足写 `UNKNOWN`，第三方 jar 无源码写 `无源码不可判定` 并列出缺失依赖。不得把计划、推测或历史记录写成当前事实。

## 交接、状态与租约

开工前记录：目标（可验收完成条件）、允许修改路径、禁止事项、基线证据、安全不变量（容量守恒、数据不丢失、Fail Fast）、验证命令。
交接六栏：状态 / 实际变更 / 证据（文件与行号）/ 验证 / 剩余风险 / 复核。状态只用 `PASS`、`FAIL`、`BLOCKED`、`UNKNOWN`、`PARTIAL`：`PASS` 要求范围内完成、证据充分且经非作者复核；`FAIL` 已执行但不满足验收；`BLOCKED` 缺权限/依赖/环境/输入；`UNKNOWN` 证据不足；`PARTIAL` 只完成可确认部分。作者不得自称 `PASS`。
同一文件同时只允许一个作者；编辑前先读目标文件并检查现有工作副本改动，只做租约内最小、可审阅的变更。既有未提交改动受保护：不得覆盖、清除、回滚、重建或重写历史。状态与差异只用 `jj st` / `jj diff`；未经授权不提交、推送、删除文件或改外部共享状态；文档任务不得夹带生产实现，也不得改租约外的源码、测试或构建配置。

## 项目基线

Minecraft 1.12.2、Forge 14.23.5.2847；Kotlin 业务代码与 Java core/early transformer、Mixin 分工，JUnit 5 测试在 `src/test/kotlin`。
当前 Mixin 基线 MixinBooter **11.17** + CleanMix **0.7.2** 编译期 annotation processor，入口 Sponge Mixin `IMixinConnector`（`StackUpUpMixinConnector`）；10.7 与 `IEarlyMixinLoader`/`ILateMixinLoader` 只是历史资料，不得写成当前实现。

## DSL 规则链

规则文件 → `RuleSourceLocator` → `RuleReloadPipeline` → 不可变 `RuleSnapshot` → `RuleRuntime` 原子替换 → `StackLimitService` → `StackLimitHooks`。
`RuleRuntimeCoordinator` 是 reload、报告、规则文件和示例同步的唯一协调层，`StackUpUp` 只管 Forge 生命周期；后命中的 set/加/减/乘/除在前一结果上继续。parser 只产 AST，compiler 产编译规则；matcher 和缓存键直接读 `StackContext`，昂贵上下文走 provider plan。reload 报告含 errors 时不得发布部分快照。DSL 字面量遵循实际 grammar 与 `ResourceLocation` 行为，不得按冒号数量猜 meta。

## 容量与 remainder 不变量

**对外广告容量不得大于真实写入容量**，容量必须来自目标对象实际写入路径。只有库存上限为正时才取 `min(dynamicLimit, inventory.getInventoryStackLimit())`；未知 `IItemHandler` 不动态扩容，兼容目标先证实真实 `getInventoryStackLimit()`/`getSlotLimit()`。
`insertItem` 与 `setStackInSlot`/vanilla setter 是不同写入路径，证据不可互替。区分 `simulate` 与真实写入，真实 `insertItem(..., false)` 必须满足 `storedDelta + remainderCount == offered`；写入后禁止重算余量、回填、重试或补偿。

## Mixin、ASM 与 core/early 约束

目标、方法和 descriptor 明确时优先 Mixin，Mixin 表达不了才用窄范围 ASM；不得以旧 loader 规则推断 11.17 行为。包裹原调用用 `@WrapOperation`，改表达式结果用 `@ModifyExpressionValue`，不得新增 `@Redirect`；静态 handler 用 Java `private static`，重载注入写完整 descriptor。
`src/main/java` 的 core/early 与 Mixin 保持纯 Java：不得依赖 Kotlin 集合、序列、文本、IO、范围、lambda、方法引用或 `use {}`，不得引入生成 `WhenMappings`/`NoWhenBranchMatchedException` 的写法；transformer 必须处理空 `transformedName` 和 `basicClass`。禁止用反射替代功能、测试或审查。

## Fail Fast 与验证

输入、规则、目标签名、依赖状态或 selector 不合约束时尽早失败并留可定位信息；不得静默回退、吞错、空 `catch`、占位实现。明确缺失依赖才可 skip，链接错误与未知 probe ID 必须是 failure。
行为测试必须调用真实代码；配置、Mixin 注册与字节码护栏属结构检查，须单独标明。生命周期测试、自动化矩阵与客户端 F3+T 只在实际执行后记录通过。Kotlin/Java 用项目 Spotless/ktlint（UTF-8、LF、末尾换行），修改后检查最终内容、`jj diff` 与租约外变更。

## 文档索引
入口 [START_HERE](docs/agent/START_HERE.md)，硬约束 [hard-rules](docs/agent/2026-04-18-hard-rules.md)，决策 [CDR](docs/agent/compatibility-decision-record.md)，任务 [清单](docs/agent/%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)，子代理 [库](docs/agent/%E5%AD%90%E4%BB%A3%E7%90%86%E5%BA%93.md)，实现 [说明](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)，回归 [runServer](docs/runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md)，DSL [示例](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)。
