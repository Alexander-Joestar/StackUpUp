# DSL v2 迁移说明

本文面向已经用过旧版 StackUp 规则、现在准备迁到 StackUpUp 新规则系统的玩家和整合包作者。

## 1. 先记住一件事

新的公开入口只有规则文件：

```text
config/stackupup/main.su
```

如果文件不存在，StackUpUp 会自动创建一个带示例和注释的模板文件。

## 2. 为什么要迁移

旧思路最大的限制，是很难优雅处理这类物品：

1. 同一个物品 ID，下挂很多 `metadata` 子物品。
2. 需要按矿物辞典统一处理。
3. 需要把多个条件组合起来匹配。

这正是 1.12.2 大型整合包里最常见的情况，尤其是 GregTech CEu 一类模组。

DSL v2 的重点，就是把规则匹配统一到：

1. `item`
2. `meta`
3. `ore`
4. `mod`
5. `size`

## 3. 常见迁移写法

### 原来只想改一个物品

```text
item = minecraft:egg -> 256
```

### 原来想按模组统一改

```text
mod = thermal -> 1024
```

### 原来想改 GregTech 某个具体变体

```text
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512
```

### 原来想按矿物辞典统一处理

```text
ore = ingotSteel -> 2048
```

### 原来想给一组物品同样的上限

```text
item in [minecraft:egg, minecraft:snowball] -> 128
```

## 4. 当前支持的核心语法

```text
item = minecraft:egg -> 64
item = minecraft:*_ball -> 128

item = gregtech:gt.metaitem.01 -> 512
item = gregtech:gt.metaitem.01:11305 -> 1024
item = gregtech:gt.metaitem.01@11305 -> 1024

item in [minecraft:egg, minecraft:snowball] -> 128
mod in [thermal, ic2, enderio] -> 1024
ore in [ingotSteel, ingotIron] -> 2048

meta = 11305 -> 512
meta in [11305, 11306] -> 1024
size in [2, 16, 64] -> 1024

size > 2 && size < 64 -> 1024
2 < size < 64 -> 1024
```

## 5. 逻辑优先级

当前固定为：

```text
&& 高于 ||
```

暂时不支持括号。

这不是能力不够，而是为了避免规则系统被写得过于复杂，最后反而难维护。

## 6. 注释

```text
# 单行注释
// 单行注释
/* 块注释 */
```

## 7. 重载方式

修改文件后，在游戏内执行：

```text
/stackupup reload
```

如果想直接打开主规则文件：

```text
/stackupup edit
```

这个命令只适用于本地带桌面环境的客户端，不适用于无桌面的专用服务端。

## 8. 建议

1. 优先写短规则，不要一开始就堆很长的 `&&` 和 `||`。
2. 能按矿物辞典统一处理时，优先用 `ore = ...`。
3. 真正需要点名某个子物品时，再用 `item + meta`。
4. 如果多条规则都命中，后面的规则会覆盖或继续叠加前面的结果。
