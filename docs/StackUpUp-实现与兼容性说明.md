# StackUpUp 实现与兼容性说明

## 1. 实现方式与原理

StackUpUp 的核心思路不是改写 Minecraft 的物品系统，而是在 **加载期** 和 **运行期** 两头补丁：

1. **加载期核心补丁**：`StackUpCore` 作为 Forge coremod 先于普通模组加载，注册 `StackUpTransformer`。
2. **字节码替换**：`StackUpTransformer` 在类被加载时，按类名、父类、接口来判断是否需要 patch。
3. **上限常量替换**：把大量硬编码的 `64` 替换为 `StackUpHelpers.getMaxStackSize()`，这样堆叠上限就不再只受原版常数限制。
4. **网络协议扩展**：`PacketBufferWriterSplice` / `PacketUtilWriterSplice` 把 `count` 从单字节扩展为“魔数 + int”，同时仍保留 `metadata` 和 `NBT`。
5. **运行期规则系统**：`ScriptContext` 会遍历注册表里的 `Item`，按脚本规则修改 `Item.maxStackSize`，并在重载时恢复旧值。

也就是说，这个模组本质上是 **“修改 Item 级堆叠上限 + 修补少数硬编码 64 的调用点”**，而不是给整个游戏改一套新的库存模型。

## 2. 兼容性是怎么做出来的

兼容性主要靠三层：

### 2.1 按类名和继承关系打补丁

`StackUpTransformer` 不只认一个固定类名，还会通过 `StackUpClassTracker` 判断目标类是否：

- 实现了 `IInventory`
- 实现了 `IItemHandler`
- 继承了 `Slot`

这样很多“间接继承原版库存接口”的模组类也能被命中，而不是只处理原版类。

### 2.2 按模组开关控制补丁范围

配置里有几组显式开关：

- Refined Storage
- Mantle
- IC2
- Applied Energistics 2
- Actually Additions

这些开关本质上是在控制：**是否对该模组的已知类名/方法名做定向 ASM patch**。关闭后，模组不会被强行改写。

`Chisels & Bits` 在当前代码里只有配置入口，没有看到对应的核心补丁逻辑，因此更像预留项，而不是已完成的独立兼容实现。

### 2.3 只改“必要点”

当前实现没有去大范围重写物品/容器逻辑，而是尽量只碰：

- 堆叠上限常量
- 物品堆叠读写
- 渲染数字显示
- 少数创意栏和掉落物逻辑

因此它能和大多数模组共存，前提是对方不要在同一条逻辑链上再做强干预。

## 3. 哪些情况会不兼容

主要有四类：

1. **对同一方法打补丁的模组冲突**  
   如果别的 coremod/mixin 也改了同一个方法，最后生效顺序可能不同，甚至互相覆盖。

2. **自己实现了独立的堆叠规则**  
   有些模组不是走原版 `Item.maxStackSize`，而是自己在容器、能力、网络包里单独校验 64。

3. **类名/方法名不匹配**  
   当前 patch 是按已知目标写死的。目标模组换版本、重命名、改内部结构后，就可能失效。

4. **物品本身不可堆叠或依赖 NBT**  
   如果某个物品逻辑上就不允许堆叠，或者每个 stack 的 NBT 都不同，那么改上限也不一定有意义。

## 4. 为什么 GregTech 的锭这类物品会有问题

GregTech CEu 在 1.12.2 里很多物品是 **同一个 `Item` 对象 + 不同 metadata** 的形式，也就是：

- `registryName` 相同
- `Item` 相同
- 通过 `metadata` 区分具体变体

而 StackUpUp 当前修改的是 **`Item` 级别的 `maxStackSize`**，不是 `ItemStack` 的某个 metadata 子类型。

所以结果是：

- 你不能给“同一个 Item 的不同 metadata”设置不同的堆叠上限
- 只要改了这个 Item，所有 metadata 变体都会一起受影响

这也是 GT 锭、粉、板、机壳等“同 ID、靠 metadata 区分”的物品会表现出不兼容的根本原因。

另外，脚本层当前也是按 `registryName` / 类 / 数值属性匹配，没有看到专门的 metadata token；但即使补上 metadata 选择器，如果运行时仍然把堆叠上限存到 `Item` 上，也还是只能得到 **Item 级** 行为。

## 5. 可以怎么改进

可以改，但需要比较明确的架构升级：

1. **增加 metadata 维度的规则语法**  
   这只能解决“怎么选中目标”的问题，不能单独解决“怎么存储和生效”的问题。

2. **把规则存储和查询从 Item 改为 ItemStack/Item+meta**  
   这才是支持 GT 这类 metadata 变体的关键改法；它会影响脚本、重载、序列化和大部分 patch 逻辑，是最直接但也最重的改法。

3. **把兼容补丁表做成显式注册表**  
   现在很多模组目标是写死在 transformer 里的，后续可以整理成更清晰的补丁表，方便维护。

4. **补更多测试模组组合**  
   目前更偏“实战验证”，如果想提高可维护性，最好补出常见模组组合的回归测试/启动验证清单。

## 6. 结论

StackUpUp 的兼容性来自“**尽量只改原版公共路径**”和“**对已知模组做定向 patch**”。

它对大多数只依赖原版 `Item.maxStackSize` 的模组都能工作；对 GregTech 这类 **同一 Item、不同 metadata** 的体系，则因为实现粒度停在 `Item` 层，所以无法天然做到逐 metadata 区分。
