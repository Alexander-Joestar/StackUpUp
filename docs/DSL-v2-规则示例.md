# DSL v2 规则示例

## 基础示例

```text
# 精确匹配
item = minecraft:egg -> 64

# 通配匹配
item = minecraft:*_ball -> 128
mod = thermal -> 1024

# metadata 精确匹配
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512

# 矿物辞典
ore = ingotSteel -> 2048

# 列表
item in [minecraft:egg, minecraft:snowball, gregtech:gt.metaitem.01@11305] -> 1024
mod in [thermal, ic2, enderio] -> 512

# 数值条件
size > 2 && size < 64 -> 1024
2 < size < 64 -> 1024

# 顺序执行
ore = ingotSteel -> 512
ore = ingotSteel *= 2
```

## GregTech 示例

```text
# 指定某个 gt.metaitem 变体
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512

# 按矿物辞典统一处理钢锭
ore = ingotSteel -> 2048
```

## 书写建议

1. 能用 `ore = ...` 的地方，优先用矿物辞典。
2. 需要精确指定 GregTech 变体时，再使用 `item + meta`。
3. 多条规则按顺序执行，后面的规则可以覆盖或叠加前面的结果。
