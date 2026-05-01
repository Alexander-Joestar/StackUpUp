# DSL v2 规则示例

本文只放最常用、最适合玩家和整合包作者直接抄用的写法。

主规则文件：

```text
config/stackupup/main.su.md
```

## 0. 推荐的完整 `.su.md` 写法

下面这个例子把 `state`、`rules`、gate 标题和规则块放在一起，适合作为整合包主入口文件的参考模板。

```md
# state
- phase1 = true
- phase2 = false
- expert_mode = false

# rules
## always

```stackupup
item = minecraft:egg -> 64
item = minecraft:snowball -> 128
```

## state("phase1")

```stackupup
mod = thermal -> 1024
ore = ingotCopper -> 512
```

## state("phase2") && modLoaded("gregtech")

```su
item = gregtech:gt.metaitem.01 && meta = 11305 -> 1024
item = gregtech:gt.metaitem.01 && meta in [11306, 11307] -> 2048
```
```

### 这份模板里最重要的几件事

1. `# state` 用来声明会被保存的状态值，例如 `phase1`、`phase2`、`expert_mode`。
2. `# rules` 用来放规则，下面的 `##` 标题就是 gate。
3. gate 后面直接跟 fenced code block，规则写在代码块里。
4. `config/stackupup/main.su.md` 是模板和入口文件，真正会变化的是 world 里的数据。
5. `config` 更像整合包默认模板，`world` 才是运行时会被修改、保存和重载的那份状态。

### 常用命令

```text
/stackupup reload
/stackupup edit
/stackupup state get phase1
/stackupup state set phase1 true
/stackupup state get expert_mode
/stackupup state set expert_mode false
```

## 1. 最基础的写法

```text
# state
- phase1 = true
- expert_mode = false

# rules
## always

```stackupup
item = minecraft:egg -> 64
```

## modLoaded("thermal")

```su
mod = thermal -> 1024
```
```

## 2. metadata 物品

这类写法适合 GregTech CEu 这类"同一物品 ID、不同 metadata 区分子物品"的模组。

```text
# rules
## always

```stackupup
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512
```

## state("expert_mode") && modLoaded("gregtech")

```su
item = gregtech:gt.metaitem.01 && meta in [11305, 11306, 11307] -> 2048
```
```

## 3. 列表

```text
# state
- phase1 = true
- expert_mode = false

# rules
## always

```stackupup
item in [minecraft:egg, minecraft:snowball] -> 128
mod in [thermal, ic2, enderio] -> 512
ore in [ingotSteel, ingotIron] -> 2048
meta in [0, 1, 2] -> 256
size in [2, 16, 64] -> 1024
```
```

## 4. 条件组合

```text
# rules
## state("phase1") && modLoaded("thermal")

```stackupup
size > 2 && size < 64 -> 1024
2 < size < 64 -> 1024
type = block && mod = minecraft -> 256
```

## state("expert_mode") && modLoaded("gregtech")

```su
item = gregtech:gt.metaitem.01 && meta in [11305, 11306] -> 2048
ore = ingotSteel || ore = ingotIron -> 1024
```
```

当前优先级：

```text
&& 高于 ||
```

当前 DSL 不支持括号；如果需要更复杂的条件，请拆成多个 gate 标题或拆成多条规则。

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

## 7. `modLoaded` 多 Mod ID

`modLoaded` 可以接受多个 mod ID，只有全部加载时才为 true：

```text
## modLoaded("thermal", "gregtech")

```stackupup
mod = thermal -> 512
mod = gregtech -> 256
```
```

等价于 `modLoaded("thermal") && modLoaded("gregtech")` 的缩写。

## 8. 书写建议

1. 能用 `ore = ...` 的地方，优先用矿物辞典。
2. 需要精确指定某个 `metadata` 变体时，再写 `item + meta`。
3. `type` 当前只支持 `item` 和 `block`。
4. 规则尽量短、尽量直，不要把一条规则写成很长的逻辑表达式。
5. 如果多条规则都命中，后面的规则优先级更高。
