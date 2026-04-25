# Cleanroom 对齐与架构说明

## 目标

StackUpUp 面向 `Minecraft 1.12.2` / CleanroomMC 生态，当前架构目标是：

1. Kotlin 负责规则、配置、运行时协调和自动化。
2. 固定目标优先迁到 `MixinBooter + Mixin`。
3. 动态 ASM 只保留运行时才能确定的兼容边界。
4. 规则语义统一到 `ItemStack + metadata + OreDictionary`。

## 规则内核

运行时主链：

1. `StackContext`
2. `StackContextResolver`
3. `StackLimitService`
4. `RuleRuntime`

统一输入：

1. `itemId`
2. `modId`
3. `metadata`
4. `type`
5. `baseLimit`
6. `oreNames`

这样可以让 GT、普通 metadata 物品、矿辞规则和 `size` 条件走同一套语义。

## 物品和槽位入口

物品上限分两层：

1. `ItemMixin` 处理普通 `Item#getItemStackLimit(ItemStack)`。
2. `ItemStackMixin` 兜底处理覆写上限逻辑的物品。

槽位上限分两条路径：

1. `StackLimitHooks.resolveDynamicSlotLimit`
2. `StackLimitHooks.resolveItemHandlerSlotLimit`

`SlotItemHandler#getItemStackLimit` 必须同时看 `stack.maxStackSize` 和 `getSlotStackLimit()`，否则 `size > 2 -> ...`
这类规则会在模拟插入路径里被二次放大。

## 配置和规则来源

1. `@Config` 负责配置声明与 GUI 元数据。
2. `StackUpUpConfig` 保留运行时 facade。
3. 公开规则目录固定为 `config/stackupup/`。
4. 主规则文件固定为 `main.su`。
5. 用户覆盖为 `user.su`。
6. 世界规则为 `<save>/data/stackupup/world.su`。

## DSL 边界

DSL v2 面向玩家和整合包作者，不扩展成脚本语言。

当前支持：

1. 字段：`item`、`mod`、`type`、`ore`、`meta/metadata`、`size`
2. 比较：`=`、`!=`、`>`、`>=`、`<`、`<=`
3. 列表：`field in [...]`
4. 区间：`2 < size < 64`
5. 逻辑：`&&` 高于 `||`
6. 动作链：`-> 128`、`-> +32`、`-> *2 -> +10`

暂不支持括号。

## Mixin 与 ASM 边界

已迁到 Mixin 的固定目标包括原版通用路径、Mantle、IC2、AE2、Actually Additions、Refined Storage 已知路径和 CyclopsCore
`SimpleInventory`。

仍保留 ASM 的边界：

1. 动态发现的 `IInventory`
2. 动态发现的 `IItemHandler`
3. 动态发现的 `Slot`
4. 少量底层协议 / 序列化补丁

这些目标集合只能运行时确认，静态列举 mixin 漏网风险更高。继续收缩时必须以自动化回归和字节码安全测试为前提。

## 自动化

主入口：

```powershell
.\gradlew.bat runServerAutoTestMatrix
```

覆盖重点：

1. GT metadata 样例
2. RS 提取路径
3. CyclopsCore / ColossalChests 库存上限
4. Forge wrapper / `SlotItemHandler` 上限
