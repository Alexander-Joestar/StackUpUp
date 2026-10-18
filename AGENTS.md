# AGENTS.md

## 范围与证据

本文件是仓库根级规范，适用于调查、计划、文档和实现任务；用户要求优先，其次是本文件与更深层 `AGENTS.md`。
证据只有源码、测试和运行输出；不足写 `UNKNOWN`，第三方 jar 无源码写 `无源码不可判定` 并列出缺失依赖。不得把计划、推测或历史记录写成当前事实。

## 交接、状态与租约

开工前记录：目标（可验收完成条件）、允许修改路径、禁止事项、基线证据、安全不变量（容量守恒、数据不丢失、Fail Fast）、验证命令。
交接六栏：状态 / 实际变更 / 证据（文件与行号）/ 验证 / 剩余风险 / 复核。状态只用 `PASS`、`FAIL`、`BLOCKED`、`UNKNOWN`、`PARTIAL`：`PASS` 要求范围内完成、证据充分且经非作者复核；`FAIL` 已执行但不满足验收；`BLOCKED` 缺权限/依赖/环境/输入；`UNKNOWN` 证据不足；`PARTIAL` 只完成可确认部分。作者不得自称 `PASS`。
同一文件同时只允许一个作者；编辑前先读目标文件并检查现有工作副本改动，只做租约内最小、可审阅的变更。既有未提交改动受保护：不得覆盖、清除、回滚、重建或重写历史。状态与差异只用 `jj st` / `jj diff`；未经授权不提交、推送、删除文件或改外部共享状态；文档任务不得夹带生产实现，也不得改租约外的源码、测试或构建配置。

## 项目基线

Minecraft 1.12.2、Forge 14.23.5.2847；Kotlin 业务代码与 Java Mixin handler 分工；`StackUpUpCore` 是 Forge coremod 入口，并注册窄范围 `InventoryStackLimitTransformer`，按结构处理具体 `IInventory` / `IItemHandler` / `Slot` 实现者的容量 getter；JUnit 5 测试在 `src/test/kotlin`。
构建需 **JDK 25**：RFG **2.0.2** 插件类为 class file 69，且自身要求 Gradle >= 9.2（wrapper 为 9.4.0）；JDK 21 在配置阶段即以 `UnsupportedClassVersionError` 失败。这只约束构建所用的 Gradle JVM，项目 Java toolchain 仍固定为 8（`build-logic/convention/src/main/kotlin/jvm.gradle.kts:9`），客户端运行期要求未因此改动。
非命令行路径为 IDE MCP：IntelliJ IDEA MCP Server **2026.2.3**，端点 `http://127.0.0.1:64342/stream`，其 Gradle JVM 已是 JDK 25，天然满足上述要求。IDE 的 linked Gradle 项目只有主编检出 `C:\dev\mc\1.12.2\StackUpUp`；要在 worktree 上跑须在同一次 `run_gradle_tasks` 里用 `scriptParameters` 传 `--project-dir <worktree 路径>`（实测 `projectDir`/`rootDir`/`buildFile`/`buildDir` 均解析到该 worktree），合并后仍需在主编检出复验。
当前 Mixin 基线 MixinBooter **11.17** + CleanMix **0.7.2** 编译期 annotation processor，入口 Sponge Mixin `IMixinConnector`（`StackUpUpMixinConnector`）；兼容性注入通过显式 early/late Mixin 与窄范围 `InventoryStackLimitTransformer` 完成。10.7 与 `IEarlyMixinLoader`/`ILateMixinLoader` 只是历史资料，不得写成当前实现。

库存容量扩展策略：配置文件为 `config/stackupup.cfg`，`compat.inventoryExpansion.enabled` 默认 `true`（新/缺失配置采用 broad-default；已有配置中的显式 `false` 保持关闭）；显式设为 `false` 仅作为重启时的紧急回退，只门控可选容量 lane：启用且无冲突时才注册窄范围 `InventoryStackLimitTransformer`（按结构命中具体 `IInventory`/`IItemHandler`/`Slot` 实现者的容量 getter，含原版具体类，只排除接口/抽象类、本 Mod 包与配置托管的 `InventoryCrafting`/`InventoryCraftResult`；值在 getter 调用时由策略快照判定）并入队 `capacity.early` / capacity-late 配置；关闭时这些可选 getter/容量配置不生效。OFF 不等于关闭所有核心写入或安全路径，也不证明每个原版 inventory 都能超过 64：正常无冲突启动时常驻 `baseline.early` 保留有源码依据的具体路线，例如 `InventoryPlayerAddResourceMixin` 的 item-aware clamp，以及 `InventoryWriteLimitMixin` 在 `TileEntityLockableLoot#setInventorySlotContents` 写入前调用 `resolveInventoryWriteLimit`；`VanillaInventoryLimitMixin` 在 OFF 时保留原 getter 值。未知 `IItemHandler` 不因其接口类型而动态扩容。

`compat.inventoryStackLimitBlacklist.modIds` 是唯一容量黑名单输入：空列表是 no-op；非空时，窄 getter hook 按 Forge owned packages 对实际 receiver/受支持公开 owner 做选择性 bypass，connector 另跳过所列 Mod 对应的 capacity-late 配置；这不会全局关闭其他 Mod，也不会关闭该 Mod 的普通/安全 late 配置（例如 IC2 Classic 的 SlotBase 原生 64 上限保护）；EnderIO 与 NuclearCraft 的 no-drop 修复属于各自独立的 ordinary late profiles，不是 IC2 Classic 路径。不引入 `classNames` 配置。启动读取 Mod ID 列表并冻结，首次 preInit 发布包归属，之后仅更新兼容上限；改变开关/黑名单必须重启。已加载 Mod 无归属包须失败，未加载 ID 记录无效果。receiver 或受支持公开 owner 的真实 class 按精确包边界命中才可归属；仅继承黑名单父类或实现黑名单接口不排除未列入的 addon，其实际写入安全性仍需独立验证。共享实例无公开 owner 时无法推断创建者 Mod（例如 plain ItemStackHandler），receiverless optional constructor/private delegate 路径仍 UNKNOWN，不声称完整第三方排除/守恒已证明。设备 wrapper 同时判断实际 wrapper 及实体；关闭扩容时该专用 getter fallback 只保留精确 Forge hands/armor wrapper 与原版实体组合。AE2 UEL v0.56.7 的 typed API 为标准 compile-only 依赖，不随游戏强制安装；其两个内部库存保留原生 maxStack 元数据，仅 getter 按 receiver 与当前公开 TE owner/typed final te 决定扩容，public setTileEntity 换 owner 不缓存初始归属、不修改已有物品。ItemStack DSL、NBT、网络和 NoDrop 不受该开关压制。

## DSL 规则链

规则文件 → `RuleSourceLocator` → `RuleReloadPipeline` → 不可变 `RuleSnapshot` → `RuleRuntime` 原子替换 → `StackLimitService` → `StackLimitHooks`。
`RuleRuntimeCoordinator` 是 reload、报告、规则文件和示例同步的唯一协调层，`StackUpUp` 只管 Forge 生命周期；后命中的 set/加/减/乘/除在前一结果上继续。parser 只产 AST，compiler 产编译规则；matcher 和缓存键直接读 `StackContext`，昂贵上下文走 provider plan。reload 报告含 errors 时不得发布部分快照。DSL 字面量遵循实际 grammar 与 `ResourceLocation` 行为，不得按冒号数量猜 meta。

## 容量与 remainder 不变量

**对外广告容量不得大于真实写入容量**，容量必须来自目标对象实际写入路径。只有库存上限为正时才取 `min(dynamicLimit, inventory.getInventoryStackLimit())`；未知 `IItemHandler` 不动态扩容，兼容目标先证实真实 `getInventoryStackLimit()`/`getSlotLimit()`。
`insertItem` 与 `setStackInSlot`/vanilla setter 是不同写入路径，证据不可互替。区分 `simulate` 与真实写入，真实 `insertItem(..., false)` 必须满足 `storedDelta + remainderCount == offered`；写入后禁止重算余量、回填、重试或补偿。

## Mixin 与 coremod 入口约束

目标、方法和 descriptor 明确时使用显式 Mixin；不得恢复已删除的 dynamic ASM 兼容链路或以旧 loader 规则推断 11.17 行为。包裹原调用用 `@WrapOperation`，改表达式结果用 `@ModifyExpressionValue`，不得新增 `@Redirect`；静态 handler 用 Java `private static`，重载注入写完整 descriptor。
`src/main/java` 的 Mixin handler 保持纯 Java：不得依赖 Kotlin 集合、序列、文本、IO、范围、lambda、方法引用或 `use {}`，不得引入生成 `WhenMappings`/`NoWhenBranchMatchedException` 的写法。`StackUpUpCore` 只承担 Forge coremod 入口和冲突状态，不承担兼容性字节码改写。禁止用反射替代功能、测试或审查。

## Fail Fast 与验证

输入、规则、目标签名、依赖状态或 selector 不合约束时尽早失败并留可定位信息；不得静默回退、吞错、空 `catch`、占位实现。明确缺失依赖才可 skip，链接错误与未知 probe ID 必须是 failure。
行为测试必须调用真实代码；配置、Mixin 注册与字节码护栏属结构检查，须单独标明。生命周期测试、自动化矩阵与客户端 F3+T 只在实际执行后记录通过。Kotlin/Java 用项目 Spotless/ktlint（UTF-8、LF、末尾换行），修改后检查最终内容、`jj diff` 与租约外变更。
Gradle 生命周期（`spotlessApply`、`test`、`check`/`build`）通过 IDE MCP 的 `run_gradle_tasks` 执行：`projectPath` 指向主编检出、`taskNames` 为任务名；worktree 用 `scriptParameters="--project-dir <worktree>"` 路由，结果以实际 rootDir 为证据。外层 MCP 约 60s 会返回 `-32001 Request timed out` 或空响应，即使传 `timeoutMillis=600000` 也一样，且该参数超时会触发取消请求；这类超时不能判定 Gradle 失败（daemon 可能仍在跑），须查到实际 rootDir、任务终态与 daemon 日志后再下结论。不用命令行 `./gradlew`；IDE 未运行（headless/CI）时才回退命令行并仍用 JDK 25。合并后仍需在主编检出复验。
代码任务合并前必须有一条该 mission worktree 上的 `run_gradle_tasks` 成功记录（`exitCode 0`，`test` 或 `check`），仅 review 判 clean 不构成构建证据；worktree 证据须对应被审 tip，rebase 后须重跑。

## 容量/Mixin Skill 索引与触发顺序

- 审计：[mod-capacity-audit](docs/agent/skills/mod-capacity-audit/SKILL.md)（只读）；修复：[mod-capacity-fix](docs/agent/skills/mod-capacity-fix/SKILL.md)（仅在审计明确 `UNSAFE` 后使用）。
- 顺序：先按审计 Skill 取证；`SAFE` 停止，`UNKNOWN` 先补证据，只有 `UNSAFE` 才进入最小修复；修复后先做静态验证，再单独记录用户实际运行测试。

## 文档索引
入口 [START_HERE](docs/agent/START_HERE.md)，硬约束 [hard-rules](docs/agent/2026-04-18-hard-rules.md)，决策 [CDR](docs/agent/compatibility-decision-record.md)，任务 [清单](docs/agent/%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)，子代理 [库](docs/agent/%E5%AD%90%E4%BB%A3%E7%90%86%E5%BA%93.md)，实现 [说明](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)，回归 [runServer](docs/runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md)，DSL [示例](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)。
