# 兼容决策记录

面向未来 agent 和维护者。短句记录，不替代主架构文档。

## 当前决策

- 新增兼容优先用 MixinBooter + Mixin。
- ASM 只保留旧兼容和早期加载兜底。
- unknown `IItemHandler` 不动态扩大。
- 不恢复 remainder-system。
- 机器类兼容先查真实写入容量。
- `RuleField` 保持静态 enum 自描述。
- 昂贵/可选上下文走 provider plan，不走字段名硬编码。
- `reload` 不隐式刷新示例文件。

## 规则字段上下文

- 不引入动态 `RuleField` 注册表。
- 新字段先写清 enum 自描述。
- 昂贵/可选上下文由 `RuleField.contextProviders` 声明。
- 运行时聚合成 `RuntimeContextRequirements` provider plan。
- `StackContextResolver` 只执行已编译 plan。
- 不在 resolver 里按字段名写分支。
- `RuleContextRequirement` 只保留旧兼容和诊断查询。
- 新字段不要把它当主扩展点。

## GT material resolver

- 只支持 public API 链。
- 拒绝 private getter/field。
- 拒绝任意 debug `toString`。
- 优先用 registry id / `getName`。
- 这是有意兼容边界收紧。

## GroovyScript 参考结论

可吸收：

- 自描述 DSL 元素。
- 初始化路径和热路径分离。
- 冻结后的只读 registry 思想。

不吸收：

- Groovy runtime。
- MetaClass。
- 反射扫描。
- 大型 ModSupport 总表。

当前分支不做大 registry。
等第二个 mod 字段出现后，再评估是否需要 registry。

## 示例同步

- `reload` 只重载规则。
- 不顺手刷新示例文件。
- 示例同步是显式初始化动作。

## 为什么禁用 unknown IItemHandler 动态 ASM

- `IItemHandler` 的 `getSlotLimit()` 是真实容量承诺。
- handler 若仍报 64，就不能对 slot 层广告 256。
- 只改 UI/slot 层会让 vanilla 投入过量物品。
- 过量写入会触发截断、丢弃或模组自己的 remainder 语义。
- 动态 ASM 无法知道未知 handler 是否安全。
- 所以未知 handler 保持原真实容量。

## 为什么不用 remainder-system

- remainder-system 是写入后的补救。
- 它需要猜测库存实际接受了多少。
- 模组可能已经自己处理了溢出。
- 模组也可能改写传入 stack 表示剩余量。
- 事后回填容易从吞物品变成复制物品。
- 当前策略是写入前保持容量一致。

## 为什么 Mixin/MixinBooter 是首选

- 已知 mod 目标类和方法通常稳定。
- late mixin 可以只在目标 mod 存在时加载。
- 方法签名可以精确写 descriptor。
- 补丁位置可读，测试也更容易锁住。
- Mixin 接管的类应让 dynamic ASM 跳过。
- ASM 不再承担主要业务逻辑。

## NC 研究记录

比较对象：

- NuclearCraft `1.12.2o`。
- NuclearCraft `1.12.2` 分支。

`1.12.2o` 风险点：

- `nc.tile.internal.inventory.ItemHandler#insertItem`。
- `nc.tile.internal.inventory.ItemHandler#getStackSplitSize`。
- `nc.tile.internal.inventory.ItemHandler#getSlotLimit`。
- `nc.tile.inventory.ITileInventory#setInventorySlotContents` 会截断 backing stack。

`1.12.2` 主要路径：

- 主要走 Forge `InvWrapper` / `SidedInvWrapper`。
- wrapper 包装 `IInventory`。
- 真实容量仍要回到库存写入路径确认。

NC 结论：

- 做 NC 兼容时，不打 UI/slot 层。
- 先查机器真实写入容量。
- late mixin 应打真实写入或 handler 容量路径。
- 对 `1.12.2o`，优先看 `ItemHandler` 和 `ITileInventory`。
- 对 `1.12.2`，优先看 Forge wrapper 背后的 `IInventory`。

## TR 研究记录

对象：

- Tech Reborn 1.12.x。
- TechReborn `1.12` 依赖 `RebornCore-1.12.2`。
- 机器库存核心在 RebornCore，不是 TR 自己的 UI slot。

关键风险路径：

- `reborncore.common.util.Inventory#setInventorySlotContents` 会截断。
- `reborncore.common.util.Inventory#getInventoryStackLimit` 返回真实上限。
- `InventoryItemHandler#insertItem` 空槽可能 `slot.putStack(stack)` 后返回 `ItemStack.EMPTY`。
- `InventoryItemHandler#getSlotLimit` 默认 64。

AE2 相关风险：

- AE2 走 capability。
- AE2 信任 handler 返回的 remainder。
- 如果 handler 返回 `ItemStack.EMPTY`，但 backing 只写入 64，就会吞物品。

TR 结论：

- TR 兼容优先 late mixin RebornCore。
- 目标是真实库存/handler 容量路径。
- UI/slot 层只能作为第二阶段体验补丁。

## 其他机器类

- 机器类同样先查真实写入容量。
- 不先改 `Slot#getItemStackLimit`。
- 不先改 GUI 显示。
- 先确认 `IInventory#getInventoryStackLimit()`、`IItemHandler#getSlotLimit()`、`insertItem()`、`setInventorySlotContents()`。
- 只有真实写入容量安全，slot 层才可以跟随扩大。

## 迁移历史结论

- 吞物品多来自容量错配。
- 客户端显示问题和服务端真实库存问题要分开。
- Ender IO 这类重载方法必须写完整 descriptor。
- AE2、CyclopsCore 等固定目标更适合显式 late mixin。
- 已迁到 Mixin 的目标不要放回泛化 ASM。

相关文档：

- [../StackUpUp-实现与兼容性说明.md](../StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)
- [../ASM-迁移状态.md](../ASM-%E8%BF%81%E7%A7%BB%E7%8A%B6%E6%80%81.md)
- [remainder-system.md](remainder-system.md)
