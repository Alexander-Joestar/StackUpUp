# Changelog

## 0.2.4

### 启动与运行时稳定性

- 修复生产/Notch 混淆环境下的启动崩溃，移除早期 Connector 中违规的 Forge Loader 初始化。
- 修复 AE2 Supergiant 在 Sponge Mixin 接口校验期间触发 `InvalidMixinException` 的启动崩溃。

### 模组兼容与数据安全

- 彻底修复 NuclearCraft 机器输出槽在产物超过 64 个时被硬编码截断并吞物的恶性问题。
- 修复 TechReborn 研磨机、离心机等加工机器，以及铁合金炉在产物达到 64 个后误判满仓、卡死并停止工作的问题。
- 完善两代 AE2 兼容：同时支持传统 Applied Energistics 2 与新版 AE2 Supergiant，修复外部处理器大堆叠插入和样板终端空白样板槽上限问题。
- 为 NuclearCraft 速度与能量升级槽开放堆叠上限配置，默认值为 64 以避免过度强化，并支持按需扩展。

### 性能与配置

- 新增 `compat` 配置模块，支持通过 `compat.vanilla.craftingSlotLimit` 限制原版工作台槽位上限，避免按住 Shift 批量合成超大堆叠造成严重卡顿。
- 新增 `alwaysCompactNumbers` 配置选项，在文本适配和字体缩放前使用有上限的紧凑堆叠数量显示（例如 `1.5K`、`0.1M`、`2.1B`）。
- 优化配置注释，明确标注性能与平衡影响，精简冗余说明。

### 规则运行时与架构清理

- 收紧规则旧壳与探针薄包装，移除 `RuleMatchContext`、`RuleField.requirements` 等过时路径。
- 保留命令入口的 Forge 非空 override 签名，参数校验继续通过轻量的 `executeArguments` / `completeArguments` 完成。
- 将规则字段主路径统一为 `RuleField.contextProviders -> RuntimeContextRequirements` provider plan，`RuleContextRequirement` 仅保留旧兼容和诊断投影。
- 统一以 `RuntimeContextRequirements.requires(...)` 作为主查询，`fromProviders` 保序去重，并确保 `ORE_NAMES` 不进入缓存键。
- 保持 `RuleStateService` 与 `RuleRuntimeCoordinator` 的三态及成功/失败发布契约，不恢复 `remainder-system`。
- 将全部 Late Mixin 按模组子目录重构为 17 个模块子包，纠正历史遗留的错配引用并清理冗余文件。
- 新增 IntegratedDynamics、LimeLib 和 ImmersiveEngineering 的 Late Mixin 支持。

### 构建与验证

- 构建链升级至 RetroFuturaGradle 2.0.2，并切换至 JDK 25。
- 全量 422 项测试通过。

## 0.2.3

- 移除 1.12.2 下不再需要的 `category` 字段
- 修复规则可能重复执行的问题
- 优化代码结构

## 0.2.2

- 修复 ModularUI/RSB 背包 GUI 点击时 `BackpackContainer cannot be cast to ContainerAccessor` 崩溃
- 修复 `ContainerState` 在 mixin-owned 包导致的 `IllegalClassLoadError`
- 修复 `@WrapOperation` 包装 `setItemStack` 和 `dropItem` 时缺少实例方法 receiver 的注入失败
- 简化 `build.gradle.kts` 中重复的 IDEA run configuration 和 auto test task 样板
- 简化 `EarlyMixinConfigTest`，合并同类 IO 读取测试
- 新增 `item = *` 通配符，匹配所有可堆叠物品（排除 baseSize=1 的工具、盔甲等）
- 新增 `tab` 字段，按创造模式标签页匹配（如 `tab = buildingBlocks`）
- 新增 `category` 字段（后续版本已移除）
- 新增 `meta` 范围写法（`100 < meta < 300`）
- `RuleField` 简化：去掉冗余 `id` 构造参数，`matchers`/`byName` 懒加载，`fromIdentifier` 只做一次 uppercase 查表
- 引入 `FieldType` 枚举，编译器按类型分发，加新字段只需 enum 一行
- 移除 `RuleStepAst`/`RuleActionAst` 空壳，parser 直接输出 `RuleStep`/`RuleAction`
- 移除 `RuleReloadWarning`/`RuleComplexityWarning` 空 typealias
- 简化 `RuleComplexityAnalyzer.analyze()` 直接返回 `List<LocalizedMessage>`

## 0.2.1

- 修复拾取时退回原始堆叠上限

## 0.2.0

- 修正部分本地化错误
