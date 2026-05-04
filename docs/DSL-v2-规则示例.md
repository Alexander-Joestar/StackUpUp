# DSL v2 规则示例

StackUpUp 支持两种规则格式：

| 格式 | 放置位置 | 用途 |
|------|----------|------|
| `.su` | `config/stackupup/` | 纯 DSL 文本规则，全局配置级别 |
| `.su.md` | `config/stackupup/` 或 `<世界>/data/stackupup/` | Markdown 格式，支持 state 变量和 gate 条件 |

主入口文件是 `config/stackupup/main.su`。

两种格式的 DSL 语法完全相同（`item`、`ore`、`mod`、`meta`、`size` 等），区别在于 `.su.md` 额外支持 state 声明和 gate 标题。

---

## `.su` 格式示例

纯文本规则，每行一条，适合直接放在 `config/stackupup/main.su` 里：

### 常用写法

```text
# 所有可堆叠物品（排除工具、盔甲、桶等 baseSize=1 的物品）
item = * -> 128

# 普通物品
item = minecraft:egg -> 256

# 按模组统一
mod = thermal -> 1024

# 按矿物辞典
ore = ingotSteel -> 2048

# 列表匹配
item in [minecraft:egg, minecraft:snowball] -> 128
ore in [ingotSteel, ingotIron] -> 2048

# 数值范围
2 < size < 64 -> 256
100 < meta < 300 -> 512

# 按创造模式标签页
tab = buildingBlocks -> 256
tab = tools -> 128

# 按物品类别（药水瓶、附魔书）
category = potion -> 32
category = enchanted_book -> 16

# 类型条件
type = block && mod = minecraft -> 512
```

### metadata 简写

对于 GregTech CEu 这类同 ID、不同 metadata 的物品，可以用 `@` 分隔符省掉 `&& meta = ...`：

```text
# 完整写法
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512

# 简写（用 @ 分隔）
item = gregtech:gt.metaitem.01@11305 -> 512

# 也支持用额外冒号分隔（仅当 item ID 本身只有一个冒号时）
item = minecraft:wool:14 -> 512
```

### 条件组合

```text
# 多条件 AND
item = gregtech:gt.metaitem.01 && meta = 11305 && ore = ingotSteel -> 1024

# OR 条件
ore = ingotSteel || ore = ingotIron -> 2048

# 组合
item = gregtech:gt.metaitem.01@11305 && mod = gregtech -> 1024
```

### 运算符链

```text
# 先设值，再翻倍
ore = ingotSteel -> 512 -> *2

# 加减乘除
ore = ingotSteel -> 512
ore = ingotSteel -> +128
ore = ingotSteel -> *2
ore = ingotSteel -> /2
```

### 注释

```text
# 单行注释
// 单行注释
/* 块注释 */
```

---

## `.su.md` 格式示例

Markdown 容器格式，适合整合包分发。规则、说明和状态变量可以放在同一个文件里。

### 完整模板

```markdown
# state
- phase1 = true
- phase2 = false
- expert_mode = false

# rules
## always

```stackupup
item = minecraft:egg -> 256
item = minecraft:snowball -> 128
```

## state("phase1")

```stackupup
mod = thermal -> 1024
ore = ingotCopper -> 512
```

## state("expert_mode") && modLoaded("gregtech")

```stackupup
item = gregtech:gt.metaitem.01@11305 -> 2048
item = gregtech:gt.metaitem.01 && meta in [11306, 11307] -> 2048
```
```

### 关键结构

1. `# state` 声明可持久化的布尔变量，随存档保存
2. `# rules` 放规则，下面的 `##` 标题是 gate 条件
3. gate 后面跟 fenced code block，规则写在代码块里
4. `## always` 表示无条件启用
5. gate 支持 `state("名称")`、`modLoaded("modid")` 及 `&&`/`||`/`!` 组合

### Gate 表达式

```text
## always                                    无条件
## state("phase1")                            检查 state 变量
## modLoaded("gregtech")                      检查模组是否加载
## modLoaded("thermal", "gregtech")             多模组全部加载
## state("expert_mode") && modLoaded("gregtech")  组合条件
## !state("disable_thermal")                   取反
```

### State 管理

```text
/stackupup state get phase1          查看值
/stackupup state set phase1 true     设置值
/stackupup reload                    重载规则（state 变更后需要重载才生效）
```

### 世界级 `.su.md`

每个存档的世界目录下也有一个 `main.su.md`（位于 `<世界>/data/stackupup/main.su.md`），它的 state 变量随存档独立保存。整合包的 `config/stackupup/*.su.md` 是默认模板，世界的那份才是运行时数据。

---

## 加载顺序

StackUpUp 按以下顺序加载规则文件，后面的规则会覆盖或叠加前面的结果：

1. `<世界>/data/stackupup/main.su.md` — 世界级 Markdown 规则（带 state）
2. `<世界>/data/stackupup/world.su` — 世界级 DSL 规则
3. `config/stackupup/*.su` — 整合包 DSL 规则（按文件名排序）
4. `config/stackupup/*.su.md` — 整合包 Markdown 规则（按文件名排序）
5. `config/stackupup/main.su` — 主 DSL 规则
6. `config/stackupup/user.su` — 用户覆盖规则

如果存在旧版 `config/stackupup/stackupup-rules.su` 且 `main.su` 不存在，会先加载旧文件以兼容老整合包。

---

## DSL 语法要点

- 匹配字段：`item`、`mod`、`ore`、`meta`（别名 `metadata`）、`size`、`type`、`tab`、`category`
- 特殊匹配：`item = *` 匹配所有可堆叠物品（排除 baseSize=1 的工具、盔甲等）
- 比较运算：`= != > >= < <=`
- 列表匹配：`field in [value1, value2]`
- 范围写法：`2 < size < 64`、`100 < meta < 300`
- 逻辑组合：`&&`（优先级高于 `||`），当前不支持括号
- 动作链：`-> 128`、`-> +32`、`-> *2`、`-> /2`，可链式执行
- `type` 只支持 `item` 和 `block`
- `tab` 是创造模式标签页 ID，如 `buildingBlocks`、`tools`、`combat`
- `category` 当前支持 `potion`（药水瓶）和 `enchanted_book`（附魔书）

## 书写建议

1. 能用 `ore = ...` 的地方优先用矿物辞典
2. 精确指定某个 metadata 变体时用 `item@meta` 简写，比 `&& meta = ...` 更简洁
3. 需要对同一 item ID 下多个 metadata 做不同处理时用 `meta in [...]`
4. 规则尽量短，不要把一条规则写成很长的逻辑表达式
5. 多条规则都命中时，后面的规则优先级更高
6. 需要阶段控制的整合包优先用 `.su.md`，简单场景用 `.su` 就够了
