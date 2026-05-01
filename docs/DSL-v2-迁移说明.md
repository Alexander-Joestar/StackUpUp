# DSL v2 迁移说明

本文面向已经用过旧版 StackUp 规则、现在准备迁到 StackUpUp 新规则系统的玩家和整合包作者。

## 1. 先记住一件事

新的公开入口已经变成单文件 Markdown 容器：

```text
config/stackupup/main.su.md
```

如果文件不存在，StackUpUp 会自动创建一个带示例和注释的模板文件。

这里要特别区分两层含义：

1. `config/stackupup/main.su.md` 是整合包和配置侧的模板文件，适合分发默认规则。
2. world 里的 state 是可变的运行时数据，会随着存档保存和读取。

换句话说，config 提供默认结构，world 负责保存实际变化。

## 2. 从旧 `.su` 到新 `.su.md`

旧版通常是单独的 `.su` 规则文件；新版本把规则、说明和可持久化 state 合并进同一个 `.su.md` 文件里。

要先说明一点：旧 `.su` 仍然可以工作，这不是破坏性升级。

也就是说：

1. 老整合包里的 `.su` 还能继续加载。
2. 新的 `.su.md` 只是能力更完整，推荐逐步迁移过去。
3. 迁移不是"立刻替换所有旧文件"，而是"先兼容运行，再慢慢整理结构"。

迁移时主要做三件事：

1. 把旧规则内容放进 `# rules` 区域里的 fenced code block。
2. 给规则块补上 `## gate` 标题，例如 `## always`、`## state("phase1")`。
3. 把需要持久化的布尔开关放进 `# state` 区域。
4. 把原来分散在外部脚本或配置里的 gate 逻辑，改写成标题上的 gate 表达式。

## 3. 为什么要迁移

旧思路最大的限制，是很难优雅处理这类物品和逻辑：

1. 同一个物品 ID，下挂很多 `metadata` 子物品。
2. 需要按矿物辞典统一处理。
3. 需要把多个条件组合起来匹配。
4. 需要把整合包阶段或进度状态持久化下来。

DSL v2 的重点，就是把规则匹配统一到：

1. `item`
2. `meta`
3. `ore`
4. `mod`
5. `size`
6. `state()` / `modLoaded()` gate 组合

## 4. 常见迁移写法

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

### 原来依赖外部阶段开关

```text
# state
- phase1 = true

# rules
## state("phase1") && modLoaded("thermal")

```stackupup
mod = thermal -> 1024
```
```

## 5. 新格式的关键变化

```text
旧：config/stackupup/main.su
新：config/stackupup/main.su.md
```

```text
旧：把 gate 逻辑放在别的文件里或靠外部约定
新：在 `.su.md` 的标题里直接写 gate 表达式
```

```text
旧：state 往往分散在别处
新：state 直接写在 `# state` 区域里，和规则同文件保存
```

## 6. 兼容性说明

为了兼容老整合包，StackUpUp 会继续保留旧 `.su` 规则文件的读取能力。

也就是说：

1. 旧 `.su` 文件仍然可以加载。
2. 新 `.su.md` 是推荐格式。
3. 同一个整合包里可以先保留旧文件，再逐步迁移到新文件。
4. 迁移完成后，建议把主入口统一到 `.su.md`，减少维护成本。

如果你还在维护一个现成整合包，最稳妥的做法是：先让旧 `.su` 和新 `.su.md` 并行一段时间，确认新文件完全覆盖旧逻辑后，再逐步收口到 `.su.md`。

## 7. 当前支持的核心语法

```text
# state
- phase1 = true
- expert_mode = false

# rules
## always

```stackupup
item = minecraft:egg -> 64
```

## state("phase1") && modLoaded("thermal")

```su
mod = thermal -> 1024
```
```

## 8. 逻辑优先级

当前固定为：

```text
&& 高于 ||
```

暂时不支持括号。

## 9. 注释

```text
# 单行注释
// 单行注释
/* 块注释 */
```

## 10. 重载方式

修改文件后，在游戏内执行：

```text
/stackupup reload
```

如果想直接打开主规则文件：

```text
/stackupup edit
```

查看或修改 state：

```text
/stackupup state get phase1
/stackupup state set phase1 true
```

## 11. 建议

1. 先把旧 `.su` 内容原样搬到 `# rules`，确认加载正常，再慢慢整理 gate 和 state。
2. 需要持久化的开关，优先放到 `# state`，不要继续散在别的配置里。
3. gate 表达式尽量短，复杂逻辑拆成多个标题层次。
4. 如果多条规则都命中，后面的规则会覆盖或继续叠加前面的结果。
