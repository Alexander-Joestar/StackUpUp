# AGENTS.md

## 适用范围与优先级

本文件是仓库根级规范，适用于调查、计划、文档和实现任务。用户要求优先；其次是本文件、目标目录更深层的 `AGENTS.md`
和既有项目约定。不得把计划、推测或历史记录写成当前事实。

源码、测试、交接文档和外部资料只能作为证据。没有源码、版本或运行证据时必须写 `UNKNOWN`；第三方 jar 无源码时写 `无源码不可判定`
，列出缺失依赖，不得依据类名、注释或经验补全行为。

## 任务边界与交接

开始任务时记录：

- **目标**：一句话写出可验收完成条件，并标明调查、计划、文档或实现。
- **允许修改**：列出精确路径；未列出的文件不可写。
- **禁止事项**：列出禁止的实现、命令、依赖和外部操作。
- **基线证据**：列出已读规范、源码路径、测试和外部资料；区分当前事实与历史事实。
- **安全不变量**：至少说明容量守恒、数据不丢失和 Fail Fast 要求。
- **验证方式**：列出实际命令、检查文件和通过判据；未运行不得写成通过。
- **租约与复核**：说明作者、文件租约、交接边界和独立复核者。

交接必须包含以下六栏：

1. **状态**：只能是 `PASS`、`FAIL`、`BLOCKED`、`UNKNOWN`、`PARTIAL`，并说明理由。
2. **实际变更**：逐项列出真实修改的路径和内容，未修改的不得写成已修改。
3. **证据**：给出源码路径与行号、测试结果或外部来源；不足处标 `UNKNOWN`。
4. **验证**：列出已执行、失败和未执行的命令或检查。
5. **剩余风险**：列出阻塞、未知、未覆盖场景和下一步所需输入。
6. **复核**：列出非作者复核者及结论；未完成独立复核不得宣称 `PASS`。

状态含义：`PASS` 仅表示范围内已完成、证据和验证充分且通过独立复核；`FAIL` 表示已执行但验收或不变量未满足；`BLOCKED`
表示权限、依赖、环境或输入阻塞；`UNKNOWN` 表示证据不足；`PARTIAL` 表示仅完成可确认部分且仍有明确缺口。

## 租约与工作副本保护

- 编辑前先读取目标文件并检查现有工作副本改动；同一文件同时只允许一个作者持有写租约。
- 只修改租约内文件，做最小、可审阅的变更；不得因格式化、重命名、自动导入或生成物波及其他路径。
- 既有未提交改动属于保护状态，不得覆盖、清除、回滚、重建或用等价手段绕过。不得使用恢复、重置、清理或重写历史的命令。
- 状态和差异只用 `jj` 检查；未经明确授权不提交、推送、删除文件或修改外部共享状态。
- 文档任务不得夹带生产实现；不得修改租约外的源码、测试、依赖或构建配置。

## 项目基线

项目面向 Minecraft 1.12.2、Forge 14.23.5.2847；文本 DSL 为每个 `ItemStack` 求动态上限，原版和兼容模组路径由 Mixin 与窄范围
ASM 承载。Kotlin 业务代码、Java core/early transformer 与 Mixin 分工明确，JUnit 5 测试位于 `src/test/kotlin`。

当前 Mixin 构建基线是 MixinBooter **11.17**、CleanMix **0.7.2** 编译期 annotation processor，入口为 Sponge Mixin 的
`IMixinConnector`。MixinBooter 10.7、`IEarlyMixinLoader`/`ILateMixinLoader` 和旧注册方式只能作为历史资料，不能写成当前实现。

生产混淆 refmap、第三方 jar 的真实写入路径、复杂 Mixin 行为和同一客户端完整 F3+T 结果，必须以对应版本的源码、构建产物或运行证据为准；缺证据就标
`UNKNOWN`，不得猜测。

## DSL 规则边界

规则链必须保持单向：

```text
规则文件 → RuleSourceLocator → RuleReloadPipeline → 不可变 RuleSnapshot
→ RuleRuntime 原子替换 → StackLimitService 求值/缓存 → StackLimitHooks
```

- `RuleRuntimeCoordinator` 是 reload、报告、规则文件和示例同步的唯一协调层；`StackUpUp` 只负责 Forge 生命周期编排。
- 文件顺序由 `RuleSourceLocator` 决定，后命中的 set、加、减、乘、除在前一结果上继续执行；旧规则文件只按已证实的缺失条件回退。
- parser 只产出 AST，compiler 产出编译规则；matcher 和缓存键直接读取 `StackContext`，昂贵上下文由已编译的 provider plan 解析。
- reload 报告包含 errors 时不得发布部分快照或恢复备份；warnings-only 才可发布。状态存储必须区分 store 不可用、key
  缺失和底层写入失败。
- DSL 字面量遵循实际 grammar 与 `ResourceLocation` 版本行为；不得仅凭冒号数量猜测 meta 或拒绝合法 path。

## 容量与 remainder 不变量

核心不变量是： **对外广告容量不得大于真实写入容量**。容量必须来自目标对象实际写入路径，不得依据类名、`== 64` 哨兵或未知实现的主动表态。

- 库存上限为正时才取 `min(dynamicLimit, inventory.getInventoryStackLimit())`；未知或未证实的非正值路径不得被包装成安全结论。
- 未知 `IItemHandler` 不动态扩容；已知兼容只能在证实真实 `getInventoryStackLimit()`/`getSlotLimit()` 写入面后增加广告。
- `insertItem` 与 `setStackInSlot`/vanilla setter 是不同写入路径，不能用 wrapper 返回值替代另一条路径的容量证据。
- 区分 `simulate` 和真实写入；真实 `insertItem(..., false)` 必须满足 `storedDelta + remainderCount == offered`。记录
  `offered`、落库量、`remainder`、目标类和 slot，守恒审计只能观察和报告。
- 真实写入后禁止重新计算余量、回填、重试、补偿或以审计改变业务结果。机器兼容先查真实写入容量，再看 slot 或 GUI 广告。

## Mixin、ASM 与 core/early 约束

目标类、方法和 descriptor 明确时优先使用当前版本支持的 Mixin；原版/Forge 基础路径按当前 connector 与环境阶段注册，Mixin
无法表达时才用窄范围 ASM。已被 Mixin 接管的目标须核对固定跳过表；不得以旧 loader 规则推断 11.17 行为。

包裹原调用使用 `@WrapOperation`，修改表达式结果使用 `@ModifyExpressionValue`；不得新增 `@Redirect`。静态 handler 使用 Java
`private static`，重载注入必须写完整 descriptor。

`src/main/java` 的 core/early transformer 和 Mixin 必须保持纯 Java：不得依赖 Kotlin 集合、序列、文本、IO、范围、lambda、方法引用或
`use {}`，不得引入会生成 Kotlin `WhenMappings`/`NoWhenBranchMatchedException` 的写法。transformer 必须处理空的
`transformedName` 和 `basicClass`，并保持明确目标与 Fail Fast 诊断。

禁止用反射替代功能、测试或审查；确有通用动态框架或不可达桥接例外时，必须先证明入口不可达并保留诊断。第三方目标无源码时不得凭名称判定可写容量、注入成功或兼容通过。

## Fail Fast 与验证分层

输入、规则、目标签名、依赖状态和 selector 不符合约束时尽早失败并留下可定位信息；不得静默回退、吞错、空 `catch`
、占位实现或假最小实现。明确缺失依赖才可 skip，链接错误必须是 failure；未知 probe ID 必须产生可定位 failure。

行为测试必须调用真实代码验证行为，不能用源码 `contains` 冒充；配置、Mixin 注册和字节码护栏属于结构检查，必须单独标明。生命周期测试、自动化矩阵和客户端
F3+T 只在实际执行后记录通过；未执行、未复核、第三方缺源码和高风险 Mixin 一律保留原状态。

Kotlin/Java 使用项目既有 Spotless/ktlint 格式，文件保持 UTF-8、LF 和末尾换行。修改后检查目标文件最终内容、`jj diff`
和租约外变更；按范围运行静态检查、单测、生命周期或运行验证，并区分未执行项。

## 文档索引

- [START_HERE](docs/agent/START_HERE.md)：当前入口、状态和交接导航。
- [硬约束](docs/agent/2026-04-18-hard-rules.md)：容量、证据与安全门槛。
- [兼容性决策记录](docs/agent/compatibility-decision-record.md)：版本、兼容和历史决策。
- [Mixin 生态与注入最佳实践](docs/agent/mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md)
  ：MixinBooter、CleanMix、注入语义和证据边界。
- [重构任务清单](docs/agent/%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)：任务状态、验收卡和依赖 DAG。
- [runServer 自动化回归](docs/runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md)：服务端自动化验证说明。
- [实现与兼容性说明](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)
  ：实现边界与已知限制。
- [DSL v2 规则示例](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)：规则语法示例。
