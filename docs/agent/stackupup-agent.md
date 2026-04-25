# StackUpUp Agent Card

## 项目定位

StackUpUp 是 `Minecraft 1.12.2` 的大堆叠控制模组。目标是用 DSL v2 统一控制物品堆叠上限，并在常见容器、ItemHandler、Slot
和外部模组路径里保持一致行为。

当前版本线：`0.2.1`。

## 当前状态

1. DSL v2 已覆盖 `item`、`mod`、`type`、`ore`、`meta/metadata`、`size`。
2. `metadata` 物品、GT 前缀物品、矿辞规则、相对动作链已可用。
3. AE2、Refined Storage、CyclopsCore、ColossalChests、Forge wrapper 相关路径已有自动化覆盖。
4. 近期主线是压样板、统一资源键、清理文档和降低兼容链复杂度。

## 核心入口

1. Mod 生命周期：`src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt`
2. 规则协调：`src/main/kotlin/io/alexjoest/stackupup/RuleRuntimeCoordinator.kt`
3. 求值入口：`src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt`
4. 运行态：`limit/RuleRuntime.kt`、`limit/StackLimitService.kt`
5. DSL：`rules/parse/DslParser.kt`
6. 条件编译：`rules/compile/RuleConditionCompiler.kt`
7. 自动化：`dev/DevAutomationServerDriver.kt`、`dev/DevCompatProbeRunner.kt`

## 规则边界

1. 主规则文件：`config/stackupup/main.su`
2. 用户覆盖：`config/stackupup/user.su`
3. 世界规则：`<save>/data/stackupup/world.su`
4. 只支持 DSL v2，不兼容 DSL v1。
5. 规则只在加载/重载时解析，运行时只匹配和缓存。

## 关键语义

1. `size` 表示 `baseLimit`，不是当前堆叠数量。
2. 普通物品主走 `ItemMixin`。
3. 覆写上限逻辑的物品由 `ItemStackMixin` 兜底。
4. `SlotItemHandler#getItemStackLimit` 必须同时看 `stack.maxStackSize` 和 `getSlotStackLimit()`。

## 兼容策略

1. 固定目标优先 late mixin。
2. 动态 ASM 只保留运行时才能确定的 `IInventory / IItemHandler / Slot` 边界。
3. `FixedCompatTargets` 是 dynamic ASM 固定跳过目标的唯一事实源。
4. `CompatibilityLimitPatch.planFor(...)` 是动态补丁唯一决策入口。

## 自动化

主回归入口：

```powershell
.\gradlew.bat runServerAutoTestMatrix
```

重点覆盖：

1. GT `metadata` 样例
2. RS 提取路径
3. 库存上限
4. Forge wrapper / `SlotItemHandler` 上限

## 高风险区

1. `core/` 早期路径禁止重新引入 Kotlin 高阶集合和重型抽象。
2. 构造器上的 `@ModifyConstant` handler 必须是 `static`。
3. 包裹原逻辑时优先 `MixinExtras`，不要轻易回退到 `@Redirect` 或 ASM。
4. 避免同一路径双重应用规则。
5. `dev` 探针允许反射和代理，但不要重新堆成单个大文件。
