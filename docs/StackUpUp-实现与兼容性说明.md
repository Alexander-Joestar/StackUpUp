# StackUpUp 实现与兼容性说明

本文记录当前架构和兼容原则。旧文档里关于“写入后再计算余量并回填”的 remainder-system 方案已经过时，现行方向是：**先保证对外广告的容量不超过真实可写容量，再用明确的 Mixin 扩展已知库存的真实容量。**

## 目标

StackUpUp 不是简单把所有 `64` 替换成更大的数字，而是让堆叠上限尽量符合 `ItemStack` 和库存真实写入语义：

1. 规则系统按物品、metadata、NBT、矿物辞典、创造标签页等条件计算目标上限。
2. 原版与 Forge 常见路径通过 early mixin 接入规则结果。
3. 已知模组的硬编码容量通过 late mixin 显式扩展。
4. 旧 ASM 只保留为早期加载和未知旧兼容兜底，不作为新增兼容首选。

## 当前分层

| 层 | 说明 |
| --- | --- |
| 规则层 | DSL v2 从 `.su` / `.su.md` 读取规则，输出每个 `ItemStack` 的动态上限。 |
| 原版接入层 | early mixin 修改 `ItemStack`、玩家库存、容器合并、实体掉落、网络同步、渲染等固定原版路径。 |
| 容量广告层 | `Slot` / `SlotItemHandler` 对外报告可放入数量，但必须受真实库存容量限制。 |
| 已知模组兼容层 | MixinBooter late mixin 只在目标 mod 存在且开关启用时加载，扩展该 mod 的真实库存上限。 |
| 遗留 ASM 层 | 处理少量早期加载或未知类的历史兼容场景；新增稳定目标应优先写 Mixin。 |

## 安全容量原则

吞物品的根因通常是：**槽位向 vanilla 广告的可放入容量，大于库存或 handler 的真实写入容量。**

典型错误路径：

1. `Slot#getItemStackLimit(stack)` 对外报告 256。
2. 实际 `IInventory#getInventoryStackLimit()` 或 `IItemHandler#getSlotLimit(slot)` 仍只有 64。
3. vanilla 按 256 尝试放入。
4. 库存实现内部把数量截断到 64，或把溢出作为自己的返回/丢弃逻辑处理。
5. 若调用方没有得到准确剩余物，就表现为吞物品、复制物品或客户端显示错乱。

因此当前策略是：

- `SlotLimitMixin` 的动态上限最终取 `min(dynamicLimit, inventory.getInventoryStackLimit())`。
- `SlotItemHandlerMixin` 只有在 handler 自己报告的真实 slot limit 已经大于 64 时，才继续提升对外 slot 上限。
- 未知 `IItemHandler` 不做动态扩大；如果 handler 仍报 64，就向 vanilla 如实报告 64。
- 对已知安全目标，用 late mixin 扩展真实 `getInventoryStackLimit()` / `getSlotLimit()`，再让槽位广告自然跟随。
- 不引入、不复活 remainder-system；写入后补救比写入前保持容量一致更脆弱，也更容易和模组自己的溢出语义冲突。

## MixinBooter 与 Mixin 方向

MixinBooter 是当前兼容层的首选入口：

- early mixin 负责原版和 Forge 基础路径，随 StackUpUp 启动固定加载。
- late mixin 负责第三方 mod，只有目标 mod 存在并且配置开关启用时才排队。
- MixinExtras 由 MixinBooter 运行时提供，源码侧只保留编译期依赖。
- 对重载方法必须写完整 descriptor，例如 Ender IO 的 `getInventoryStackLimit()I` 和 `getInventoryStackLimit(I)I` 不能混淆。

当前 late mixin 覆盖的模块包括：

- Applied Energistics 2
- Actually Additions
- BrandonsCore
- CyclopsCore
- Ender IO
- IC2
- Mantle
- Refined Storage
- Simple Storage Network
- IntegratedDynamics
- LimeLib
- ImmersiveEngineering

这些模块可以通过 `config/stackupup.cfg` 的 `compatibility` 分类独立开关，修改后需要重启 Minecraft。

### AE2 当前修复记录

AE2 经 `AdaptorItemHandler` 向未知或不安全 `IItemHandler` 插入大于 64 的堆叠时，若下游 handler 截断写入却返回空 remainder，可能吞物品。当前修复是在 AE2 late mixin 中包裹 `AdaptorItemHandler#addItems` 的 `insertItem` 调用：对 unknown handler 按 `min(64, getSlotLimit(slot))` 分片插入；已知 Forge fixed compat 基础 wrapper 保持直通。`CombinedInvWrapper` / `RangedWrapper` 可继续包任意第三方 handler，所以仍按 unknown 处理。本轮没有恢复 remainder-system，也没有扩大 dynamic ASM。

## ASM 边界

ASM 仍可能存在，但它的定位已经降级：

- 保留早期 coremod 引导所需的 transformer。
- 保留少量面向未知旧类的窄化兼容逻辑。
- 已经迁入显式 mixin 的类应加入固定跳过表，避免 dynamic ASM 重复补丁。
- 新增已知 mod 兼容时，优先新增 late mixin 和测试；只有 Mixin 无法表达或加载阶段不允许时，才考虑 ASM。

更多迁移原则见 [ASM-迁移状态.md](ASM-%E8%BF%81%E7%A7%BB%E7%8A%B6%E6%80%81.md)。面向未来维护者的短决策记录见 [agent/compatibility-decision-record.md](agent/compatibility-decision-record.md)。

## 规则与兼容的关系

规则系统决定“某个物品理论上允许多大”。兼容层决定“这条路径是否能安全承载这个数量”。

不要用模组特判绕过规则系统。如果需求可以用 DSL 表达，优先写规则；如果某个库存路径仍把数量截断到 64，优先修真实容量广告与写入容量的一致性。

规则字段仍由静态 `RuleField` enum 自描述，不引入动态注册表。昂贵/可选上下文由 `RuleField.contextProviders` 聚合成 `RuntimeContextRequirements` provider plan，`StackContextResolver` 只执行已编译 plan。`RuleContextRequirement` 只保留旧兼容和诊断查询，不再作为新增字段的主扩展点。

### 本轮收紧结论

- `RuleMatchContext`、`RuleField.requirements`、`DevProbeContextResolver` 已删除，不再作为实现或文档参考。
- 命令层保留 Forge 非空 override 签名；参数逻辑只做轻量验证，不依赖 `MinecraftServer` 测试替身。
- `RuntimeContextRequirements` 的主查询是 `requires(...)`，`fromProviders` 只做保序去重；`ORE_NAMES` 不进入 cache key。
- `RuleStateService` 继续承担三态契约：store unavailable 返回 `null`，state key missing 返回 `false`，`setState` 只反映底层写入是否改变文件。
- `RuleRuntimeCoordinator` 成功路径发布 runtime + restore backup + lastReport；失败路径保留失败 report，不替换 runtime。
- 不复活 `remainder-system`，吞物品问题仍优先修真实容量和写入语义。

## 已知限制

1. 自定义容器若完全绕过 vanilla `Slot` / `Container` 流程，可能需要单独兼容。
2. 只实现未知 `IItemHandler` 且仍报告 64 的 handler 不会被动态扩大；这是刻意的安全行为。
3. 客户端显示修复只处理同步后的显示副本问题，不替代服务端真实库存修复。
4. 旧 ASM 兜底必须保持窄边界，不能重新承担主要业务逻辑。
5. NuclearCraft、Tech Reborn 这类机器兼容应先查真实写入容量，再决定 late mixin 目标；不要先打 UI 或 slot 层。
