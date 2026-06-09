# StackUpUp Agent Entry

面向未来 agent 的短入口。

先读：

1. [compatibility-decision-record.md](compatibility-decision-record.md)
2. [../StackUpUp-实现与兼容性说明.md](../StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)
3. [../ASM-迁移状态.md](../ASM-%E8%BF%81%E7%A7%BB%E7%8A%B6%E6%80%81.md)

当前原则：

- MixinBooter + Mixin 优先。
- ASM 只做旧兼容和早期加载兜底。
- unknown `IItemHandler` 不动态扩大。
- 不复活 remainder-system。
- NC/TR 机器先查真实写入容量。
- Rules 运行态以 `StackContext` 为主入口；旧参数入口只保留兼容薄壳。
- `RuleField` enum 保持自描述，不新增动态注册表。
- 昂贵上下文需求走 `RuleContextRequirement` / `requirements` 查询，不新增 `needsXxx` 布尔。
