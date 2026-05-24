# Changelog

## 0.2.4

- Add `alwaysCompactNumbers` config option: to force display stack counts into the short capped format (e.g. 1.5k, 0.1m, 2.1b)
- Add late mixin support for IntegratedDynamics, LimeLib, and ImmersiveEngineering

## 0.2.3

- 移除category字段, 1.12 为什么需要这个?
- 尝试修复规则重复执行的问题, 大概修好了?
- 优化代码结构

## 0.2.2

- 修复 ModularUI/RSB 背包 GUI 点击时 `BackpackContainer cannot be cast to ContainerAccessor` 崩溃
- 修复 `ContainerState` 在 mixin-owned 包导致的 `IllegalClassLoadError`
- 修复 `@WrapOperation` 包装 `setItemStack` 和 `dropItem` 时缺少实例方法 receiver 的注入失败
- 简化 `build.gradle.kts` 中重复的 IDEA run configuration 和 auto test task 样板
- 简化 `EarlyMixinConfigTest`，合并同类 IO 读取测试
- 新增 `item = *` 通配符，匹配所有可堆叠物品（排除 baseSize=1 的工具、盔甲等）
- 新增 `tab` 字段，按创造模式标签页匹配（如 `tab = buildingBlocks`）
- 新增 `category` 字段
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
