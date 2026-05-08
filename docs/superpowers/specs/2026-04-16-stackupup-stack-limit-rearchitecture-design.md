# StackUpUp 堆叠上限重构设计

## 目标

本阶段目标不是一次性推翻现有实现，而是在 **Minecraft 1.12.2** 环境下，先把以下能力做正确：

1. 支持同一 `Item`、不同 `metadata` 的独立堆叠上限。
2. 支持 GregTech CEu 一类 `MetaItem` / `gt.metaitem.*` 物品的精确匹配与矿物辞典匹配。
3. 让堆叠上限的运行时事实来源从 `Item.maxStackSize` 迁移到统一的查询服务。
4. 引入 `MixinBooter`，把稳定、显式、可维护的补丁迁移到 Mixin。
5. 仅保留少量动态 ASM，用于 Mixin 不擅长的泛化兼容场景。

本阶段不追求一次性替换所有旧补丁，也不追求立刻接入 CraftTweaker / GroovyScript。第一优先级是先让 `metadata` 与 GregTech 路径正确工作，并为后续逐步重写打下稳定内核。

## 现状与问题

当前代码存在以下结构性问题：

1. 规则系统的匹配与生效粒度都停留在 `Item` 级别，无法对同一 `Item` 的不同 `metadata` 变体分别生效。
2. 规则最终通过 `Item.setMaxStackSize(...)` 写回对象，这会覆盖模组自己的 `Item#getItemStackLimit(ItemStack)` 逻辑。
3. 原版与模组兼容依赖大量 ASM 与 splice，补丁职责与业务逻辑耦合过重。
4. 旧 DSL 与运行时内核强绑定，后续接入新的规则源时扩展成本高。
5. GregTech 在 1.12.2 中大量使用 `MetaItem` 与矿物辞典，当前实现无法优雅表达“钢锭”这类用户真正关心的目标。

## 总体方案

采用“**统一规则内核 + 多规则源 + Mixin/ASM 混合补丁**”方案。

核心原则：

1. **规则决定结果，补丁只负责接线。**
2. **统一以 `ItemStack` 为查询粒度。**
3. **保留模组原始逻辑的返回值，再在其上应用规则。**
4. **优先使用 Mixin 处理固定目标，保留少量动态 ASM 处理未知模组类。**

## 架构设计

### 1. 运行时堆叠上限服务

新增运行时唯一入口：

`StackLimitService.resolve(stack, baseLimit): Int`

其中：

1. `stack` 为当前正在查询的 `ItemStack`。
2. `baseLimit` 为原版或模组原始逻辑计算出的基础上限。
3. 返回值为规则系统计算后的最终上限。

该服务只负责以下职责：

1. 基于当前生效规则快照解析匹配结果。
2. 把匹配动作依序应用到 `baseLimit`。
3. 对结果做边界裁剪，例如保证结果位于 `[1, globalMaxStackSize]`。

该服务不负责：

1. 解析文本规则文件。
2. 直接修改 `Item.maxStackSize`。
3. 处理具体的 ASM / Mixin 注入细节。

### 2. 规则源与编译模型

第一阶段新增统一的规则内核，允许未来接入多个规则来源。

推荐抽象如下：

1. `RuleSource`
   规则来源接口。第一阶段仅实现文件规则源。
2. `DslRuleSource`
   读取新的 DSL v2 规则文件，并输出解析结果。
3. `CompiledRule`
   编译后的不可变规则对象。
4. `RuleSnapshot`
   当前生效的整套规则快照，替换时整体原子切换。

未来扩展方向：

1. `CraftTweakerRuleSource`
2. `GroovyScriptRuleSource`

这些扩展不应直接修改物品对象，而应最终编译为统一的 `CompiledRule`，交给 `StackLimitService` 执行。

### 3. DSL v2

新 DSL 的目标是“**像配置，不像编程**”，保留箭头与常用运算，同时限制复杂度。

#### 3.1 支持的基本形态

```text
item = minecraft:egg -> 64
item = minecraft:*_ball -> 128
mod = thermal -> 1024
ore = ingotSteel -> 2048

item in [minecraft:egg, minecraft:snowball, gregtech:gt.metaitem.01@11305] -> 1024
mod in [thermal, ic2, enderio] -> 1024

size > 2 && size < 64 -> 1024
2 < size < 64 -> 1024

item = gregtech:gt.metaitem.01 && meta = 11305 -> 512
ore = ingotSteel || ore = ingotIron -> 1024

* -> 64
```

#### 3.2 字段白名单

第一阶段仅支持以下字段：

1. `item`
   完整注册名，支持 `*` 通配，支持 `@meta` 语法糖。
2. `mod`
   模组 ID。
3. `ore`
   1.12.2 矿物辞典名称，支持 `*` 通配。
4. `meta`
   物品 metadata。
5. `size`
   原始默认堆叠上限，不受前序规则影响。
6. `type`
   仅支持 `item` 与 `block`。

#### 3.3 运算符

匹配运算：

1. `=`
2. `!=`
3. `>`
4. `>=`
5. `<`
6. `<=`
7. `in`
8. `&&`
9. `||`

动作运算：

1. `->`
2. `+=`
3. `-=`
4. `*=`
5. `/=`

#### 3.4 语义规则

1. 所有匹配条件都基于原始栈属性计算。
2. 所有规则按文件顺序执行。
3. 命中的规则作用于“当前结果值”，因此 `+=`、`*=` 有确定语义。
4. `size` 始终表示原始默认上限，不表示前面规则作用后的值。
5. `2 < size < 64` 这类链式比较在编译阶段展开为 `2 < size && size < 64`。
6. `item in [...]`、`mod in [...]` 等列表语法在编译阶段展开为 `||` 链。

#### 3.5 有意不支持的能力

为控制复杂度，第一阶段明确不支持：

1. 自定义函数
2. 任意脚本执行
3. 正则表达式
4. 变量定义
5. 多行规则
6. 嵌套列表与嵌套表达式块
7. 标签系统

注意：本项目目标版本是 **1.12.2**，因此不引入高版本的标签概念，仅支持矿物辞典。

### 4. 补丁分工

#### 4.1 使用 MixinBooter 的部分

第一阶段使用 `MixinBooter` 处理目标明确、签名稳定的类：

1. `net.minecraft.item.Item`
   在 `getItemStackLimit(ItemStack)` 返回前后接入 `StackLimitService`。
2. `net.minecraft.item.ItemStack`
   处理 `Count` NBT 的整数化读写。
3. `net.minecraft.network.PacketBuffer`
   扩展大堆叠数量的网络序列化。
4. `net.minecraftforge.common.util.PacketUtil`
   处理客户端到服务端的大堆叠数量同步。
5. `net.minecraft.network.NetHandlerPlayServer`
   创造模式堆叠校验。
6. `net.minecraft.client.renderer.RenderItem`
   数量文本渲染。
7. `net.minecraft.client.renderer.entity.RenderEntityItem`
   地面掉落渲染层数与偏移。
8. `net.minecraft.util.ServerRecipeBookHelper`
   原版配方书的固定上限问题。
9. `net.minecraft.inventory.InventoryHelper`
   掉落拆分逻辑。

这些补丁适合用 Mixin，因为它们：

1. 目标类固定。
2. 行为明确。
3. 可读性显著高于现有 splice/ASM。

#### 4.2 保留 ASM 的部分

第一阶段不计划动态生成 Mixin，也不计划为未知模组类逐个手写 Mixin。

ASM 仅保留两类职责：

1. **动态泛化兼容**
   对所有实现 `IInventory` / `IItemHandler` / `Slot` 的未知类做窄化补丁，把返回值接入统一 hook。
2. **覆盖 `getItemStackLimit(ItemStack)` 的 `Item` 子类**
   对重写该方法的物品类追加一个“返回值后处理”适配器，避免模组自定义逻辑被绕过。

这部分 ASM 必须保持极窄边界：

1. 只做返回值转接。
2. 不解析规则。
3. 不保存业务状态。
4. 不直接决定最终上限。

这样 ASM 会从“业务核心”降级为“兼容适配器”。

### 5. GregTech 与矿物辞典兼容

第一阶段优先采用通用方案解决 GregTech：

1. 支持 `item = gregtech:gt.metaitem.01 && meta = 11305` 这类精确匹配。
2. 支持 `item in [gregtech:gt.metaitem.01@11305, ...]` 这类列表语法。
3. 支持 `ore = ingotSteel` 这类矿物辞典匹配。

其中 `ore = ingotSteel` 对普通用户最友好，因为用户通常关心“钢锭”，而不是 `11305` 这样的 metadata。

第一阶段不强制引入 GregTech 专属 API 绑定；如后续发现矿辞典无法覆盖所有用户需求，再补充 GregTech 友好的扩展匹配器。

### 6. 数据结构与缓存

推荐核心数据结构如下：

1. `CompiledRule`
   包含匹配条件树、动作类型、目标值、源码位置。
2. `RuleSnapshot`
   不可变规则快照，附带版本号。
3. `StackIdentity`
   用于缓存的轻量键，至少包含 `item registry name`、`meta`、`type`。
4. `OreDictIndex`
   提前构建 `item + meta -> ore names` 缓存。

性能策略：

1. 规则文件加载时完成词法、语法与编译，不在查询期解析文本。
2. 匹配优先使用轻量字段，例如 `item`、`mod`、`meta`、`size`、`type`。
3. 矿物辞典匹配做缓存，避免运行期重复扫描。
4. 缓存按快照版本号隔离，重载后整批失效。
5. 不缓存完整 `ItemStack` 对象，只缓存稳定身份信息。

### 7. 错误提示与用户体验

错误提示、日志与文档一律使用中文。

规则文件解析错误必须至少包含：

1. 行号
2. 原始文本
3. 错误原因
4. 可用字段 / 运算符提示

示例：

1. `第 12 行：未知字段 "tag"，1.12.2 仅支持 item/mod/ore/meta/size/type。`
2. `第 18 行：动作右侧必须是正整数。`
3. `第 25 行：item in [...] 列表中的 "abc" 不是合法物品标识。`

加载成功时也应输出简洁中文日志，帮助用户确认规则命中情况。

### 8. 测试策略

第一阶段以“规则正确性”和“运行时接线正确性”为核心。

建议测试分三层：

1. **DSL 解析测试**
   覆盖：
   - `item = ...`
   - `item in [...]`
   - `size > 2 && size < 64`
   - `2 < size < 64`
   - `+=` / `*=` 等动作
   - 错误输入
2. **规则执行测试**
   构造轻量栈上下文，验证：
   - `metadata` 精确匹配
   - `ore` 匹配
   - 顺序执行
   - 链式比较
   - 默认规则
3. **集成验证**
   验证：
   - `Item#getItemStackLimit(ItemStack)` 被统一接管
   - 网络同步正常
   - 创造模式不被固定 `64` 卡死
   - 渲染与掉落数量逻辑不回归

### 9. 第一阶段成功标准

第一阶段视为成功，至少需要满足：

1. `item + metadata` 规则可用。
2. GregTech `gt.metaitem` 指定 metadata 可用。
3. `ore = ingotSteel` 一类矿物辞典规则可用。
4. `size > 2 && size < 64` 与 `2 < size < 64` 均可解析并生效。
5. `+=`、`*=` 等相对运算按顺序正确执行。
6. 大堆叠数量的 NBT 与网络同步正常。
7. 原版与常见容器不再被固定 `64` 卡死。
8. `MixinBooter` 完成引导接入，旧 coremod 仅保留少量泛化 ASM。

## 分阶段落地建议

### 阶段 1：规则内核与 metadata 兼容

1. 引入新 DSL v2。
2. 实现规则编译与运行时服务。
3. 先打通 `item + meta` 与矿物辞典匹配。
4. 把最终上限查询统一导入 `StackLimitService`。

### 阶段 2：补丁现代化

1. 接入 `MixinBooter`。
2. 把稳定目标逐步迁移到 Mixin。
3. 精简旧 ASM，只保留动态泛化适配。

### 阶段 3：规则源扩展

1. 抽象 `RuleSource` SPI。
2. 预留 CraftTweaker / GroovyScript 桥接点。
3. 支持运行时规则重载与快照替换。

## 风险与取舍

1. **不一次性移除 ASM**
   这是刻意取舍。1.12.2 环境下，动态发现未知模组类的兼容逻辑仍适合保留少量 ASM。
2. **不兼容旧 DSL**
   既然用户已接受重做格式，就不建议同时维护双语法，否则会拖慢内核重构。
3. **不在第一阶段接入 CRT / GrS**
   先完成统一规则内核，再接脚本桥接，风险更低。

## 结论

第一阶段最优路径不是“大爆炸重写”，而是：

1. 先把堆叠上限的事实来源统一到 `ItemStack` 粒度。
2. 先让 `metadata`、GregTech 与矿物辞典路径可用。
3. 再把明确、稳定的补丁迁移到 `MixinBooter`。
4. 最终把旧 coremod 压缩成一个薄薄的兼容层。

这样既能快速解决当前最痛的兼容问题，又能保证后续重写不是再次堆债，而是在稳定内核上逐步替换外层实现。
> **已过时**
