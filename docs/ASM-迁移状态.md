# ASM 迁移原则

本文替代旧的乱码迁移记录，作为当前判断准则。

## 结论

StackUpUp 的现代兼容方向是 **MixinBooter + Mixin 优先**。

ASM 不再是新增兼容的首选，只作为以下场景的遗留工具保留：

1. coremod 早期加载仍必须参与的窄入口。
2. 无法稳定写成 Mixin 的未知旧类兼容兜底。
3. 历史补丁迁移完成前的过渡实现。

## 新增兼容怎么做

遇到新的模组容量问题时，按这个顺序判断：

1. 如果目标类、方法和签名明确，写 late mixin。
2. 如果是原版或 Forge 基础路径，写 early mixin。
3. 如果目标已由 Mixin 接管，加入或核对 ASM 固定跳过表，避免重复改写。
4. 只有 Mixin 受加载阶段或目标结构限制无法表达时，才考虑 ASM。

## 迁移标准

一个旧 ASM 目标可以迁到 Mixin 时，应满足：

- 目标类稳定存在于明确 mod 中。
- 方法签名可确认，重载方法能用 descriptor 精确区分。
- Mixin 可以表达返回值修改、常量替换或调用点包裹。
- 有测试或运行验证覆盖该目标不会被 dynamic ASM 二次命中。

## 不能回退的方向

- 不为了快速扩大未知库存而动态放大未知 `IItemHandler`。
- 不用 ASM 重新承担主要业务逻辑。
- 不以 remainder-system 作为吞物品修复方案。
- 不把已明确的 late mixin 目标重新放回泛化 ASM。

当前架构总览见 [StackUpUp-实现与兼容性说明.md](StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)。

未来维护者短记录见 [agent/compatibility-decision-record.md](agent/compatibility-decision-record.md)。
