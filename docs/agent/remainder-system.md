# Remainder 系统状态

> **已废弃，不是现行方案。**

这个文件保留给后续代理理解历史背景：StackUpUp 曾尝试在 `Slot.putStack()` 写入之后计算“未被库存接受的余量”，再把余量回填到光标、源栈或客户端显示副本。该方案现在不应作为实现方向继续扩展，也不应被复活为吞物品问题的默认修复。

## 为什么废弃

吞物品的核心根因不是“缺少一个事后回填器”，而是：

**对外广告容量和真实写入容量不一致。**

如果 `Slot` 对 vanilla 报告可以放入 256，但背后的 `IInventory` / `IItemHandler` 真实只接受 64，写入路径就会发生截断、丢弃或模组自定义溢出处理。事后 remainder 只能猜测发生了什么，无法稳定区分这些情况：

- 库存确实截断并丢失了多余数量。
- 库存把传入参数改写成剩余数量。
- 库存自己返回或保留了溢出物。
- 自定义容器替换了目标槽位或绕过了 vanilla 流程。
- 客户端显示副本和服务端真实库存暂时不同步。

在这些路径上强行回填，容易从“吞物品”变成“复制物品”或“和模组自身溢出语义打架”。

## 当前替代原则

现行修复应放在写入前：

1. `Slot` 对外广告的上限不能超过 `inventory.getInventoryStackLimit()`。
2. `SlotItemHandler` 只有在 handler 自己的真实 slot limit 已经大于 64 时，才提升对外上限。
3. 未知 `IItemHandler` 不动态扩大；仍报 64 的 handler 就按 64 处理。
4. 已知会硬编码 64 的库存，用 MixinBooter late mixin 显式扩展真实写入容量。
5. 不新增 `Container.slotClick` ordinal 回填逻辑，不恢复 ThreadLocal pending remainder，不新增 remainder guard。

## 可保留的信息

历史 remainder 文档中的有用经验是：

- 吞物品最常见于 `Slot.putStack()` 到 `setInventorySlotContents()` 或 `IItemHandler#insertItem()` 的容量错配。
- 客户端显示问题和服务端真实库存问题要分开处理。
- 对 Ender IO、AE2、CyclopsCore 等固定目标，显式 late mixin 比动态猜测更可维护。
- 写兼容补丁时必须确认目标方法签名，重载方法要使用完整 descriptor。
- NC/TR 这类机器应先查真实写入容量，不先打 UI 或 slot 层。

## 文档指向

当前架构说明见 [../StackUpUp-实现与兼容性说明.md](../StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)。

ASM 与 Mixin 迁移原则见 [../ASM-迁移状态.md](../ASM-%E8%BF%81%E7%A7%BB%E7%8A%B6%E6%80%81.md)。

短决策记录见 [compatibility-decision-record.md](compatibility-decision-record.md)。
