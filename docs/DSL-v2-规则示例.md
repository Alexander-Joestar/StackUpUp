# DSL v2 规则示例

本文只放最常用、最适合玩家和整合包作者直接抄用的写法。

主规则文件：

```text
config/stackupup/main.su
```

## 1. 最基础的写法

```text
# 精确匹配
item = minecraft:egg -> 64

# 通配匹配
item = minecraft:*_ball -> 128

# 按模组匹配
mod = thermal -> 1024

# 按物品类型匹配
type = block -> 1024

# 按矿物辞典匹配
ore = ingotSteel -> 2048
```

## 2. metadata 物品

这类写法适合 GregTech CEu 这类“同一物品 ID、不同 metadata 区分子物品”的模组。

```text
# 指定某个物品
item = gregtech:gt.metaitem.01 -> 512

# 指定某个 metadata
item = gregtech:gt.metaitem.01 && meta = 11305 -> 1024

# 同一个物品下匹配多个 metadata
item = gregtech:gt.metaitem.01 && meta in [11305, 11306, 11307] -> 2048
```

## 3. 列表

```text
item in [minecraft:egg, minecraft:snowball] -> 128
mod in [thermal, ic2, enderio] -> 512
ore in [ingotSteel, ingotIron] -> 2048
meta in [0, 1, 2] -> 256
size in [2, 16, 64] -> 1024
```

## 4. 条件组合

```text
size > 2 && size < 64 -> 1024
2 < size < 64 -> 1024
type = block && mod = minecraft -> 256

item = gregtech:gt.metaitem.01 && meta in [11305, 11306] -> 2048
ore = ingotSteel || ore = ingotIron -> 1024
```

当前优先级：

```text
&& 高于 ||
```

也就是说，下面这条规则：

```text
mod = thermal || item = gregtech:gt.metaitem.01 && meta = 11305 -> 256
```

会按下面这层逻辑理解：

```text
mod = thermal || (item = gregtech:gt.metaitem.01 && meta = 11305) -> 256
```

当前 DSL **不支持括号**，这里只是在说明优先级，不是可直接复制的语法。

## 5. 顺序执行

后面的规则会接着处理前面的结果。

```text
ore = ingotSteel -> 512
ore = ingotSteel -> *2
```

上面的结果等价于把 `ingotSteel` 先设为 `512`，再翻倍到 `1024`。

等价写法：
```text
ore = ingotSteel -> 512 -> *2
```
## 6. 注释

```text
# 单行注释
// 单行注释
/* 块注释 */
```

## 7. 书写建议

1. 能用 `ore = ...` 的地方，优先用矿物辞典。
2. 需要精确指定某个 `metadata` 变体时，再写 `item + meta`。
3. `type` 当前只支持 `item` 和 `block`。
4. 规则尽量短、尽量直，不要把一条规则写成很长的逻辑表达式。
5. 如果多条规则都命中，后面的规则优先级更高。
