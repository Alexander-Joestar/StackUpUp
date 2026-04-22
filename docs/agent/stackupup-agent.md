# StackUpUp Agent Card

## 当前状态

1. 项目已可发布 `0.1.0-alpha`。
2. 元数据物品、GT 前缀物品、相对动作链、AE2UEL 构造器崩溃均已修。
3. 主线目标从“补功能”转为“持续收口旧样板与旧结构”。
4. 当前优先用 IDEA MCP 做读写与检查；Shell 主要保留给 `gradle/git`。

## 核心事实

1. 目标版本固定：`Minecraft 1.12.2`。
2. 运行依赖：`MixinBooter >= 10.0`、`Forgelin-Continuous >= 2.1.0.0`。
3. Kotlin 主导业务层；Mixin 统一放 `src/main/java`。
4. 固定目标优先用 `MixinBooter + Mixin`；动态 ASM 只保留最小兼容内核。

## 规则系统

1. 主规则文件：`config/stackupup/main.su`
2. 用户覆盖文件：`config/stackupup/user.su`
3. 世界持久化文件：`<save>/data/stackupup/world.su`
4. 只保留 DSL v2；不再兼容 DSL v1。
5. 规则只在加载/重载时解析；运行时只做匹配与缓存。

## DSL 边界

1. 字段：`item` `mod` `ore` `meta` `metadata` `size`
2. 比较：`=` `!=` `>` `>=` `<` `<=`
3. 列表：`item in [...]` `mod in [...]` `meta in [...]`
4. 区间：`2 < size < 64`
5. 逻辑：`&&` 高于 `||`
6. 动作：`-> 128` `-> +32` `-> -16` `-> *2` `-> /2`
7. 动作链：`size > 1 -> *2 -> +10`
8. 注释：`#` `//` `/* ... */`
9. 括号暂不支持。

## 关键语义

1. 精确规则入口：`StackLimitHooks.applyDynamicStackLimit`
2. 兼容常量入口：`StackLimitHooks.getCompatibilityStackSize`
3. `size` 代表 `baseLimit`，不是 `ItemStack.count`
4. GT 前缀物品仍需要 `ItemStackMixin` 补入口；普通物品靠 `ItemMixin`
5. `SlotItemHandler#getItemStackLimit` 必须同时看 `stack.maxStackSize` 与 `getSlotStackLimit()`

## 运行与回归

1. 主回归：`.\gradlew.bat test`
2. GT/兼容矩阵入口：`.\gradlew.bat runServerAutoTestMatrix`
3. 重点样例：`gregtech:meta_ingot@324` `gregtech:meta_plate@324` `gregtech:meta_dust@324` `gregtech:meta_item_1@516`
4. 当前规则模板已扩成自解释说明，尽量不依赖外部 wiki。

## 高风险区

1. `core/` 早期路径禁止无意义高阶集合、字符串花样和重型抽象。
2. 构造器上的 `@ModifyConstant` handler 必须是 `static`。
3. 只要是“继续包裹原逻辑”，优先 `MixinExtras`，不要回退到 ASM 或 `@Redirect`。
4. 不要再次引入“同一上限路径双重应用规则”的情况。

## 当前方向

1. 继续压普通 Kotlin 协调层样板。
2. 继续压规则解析/加载/持久化层分配噪音。
3. 低风险清理 mixin 样板。
4. 不动已验证通过的主行为链路。
5. 优先做“一次能收一小片”的低风险减法，不在单点细节上久耗。
6. 最近已收口：`RuleRuntimeCoordinator` 空报告工厂、命令重载回读、`DslTokenType` 运算符分类。
7. 世界规则写回已直接回到底层 `RuleBlockFileStore`，不再保留 `WorldRuleStore` 包装层。
8. `DslRuleSource` 已收平为直接加载函数，不再保留 provider + `.load()` 包装链。
