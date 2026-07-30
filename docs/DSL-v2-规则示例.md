# DSL v2 规则示例

本文面向整合包作者，语法说明建立在 **源码核对**和 **部分测试覆盖**两类证据上；两者明确区分，不能据此宣称当前语法已被全量行为测试覆盖。文中明确区分：

- **当前实现**：现在可以写入规则文件并由加载器处理。
- **已知限制**：当前行为可能不直观，不能按未来设计理解。
- **后续任务**：只标记方向，不表示已经实现。

## 文件格式和位置

### `.su`

`.su` 是逐行读取的纯 DSL 文件。常用主文件是：

```text
config/stackupup/main.su
```

`config/stackupup/` 下其他以 `.su` 结尾的普通文件也会参与加载；`user.su` 是最后加载的用户覆盖文件。`example.su`
只作为生成的语法参考，不参与规则加载。

最小完整示例：

```su
# .su 中允许注释
item = minecraft:egg -> 128
item = minecraft:snowball -> 64
```

### `.su.md`

`.su.md` 是 Markdown 容器。配置目录中的 `config/stackupup/*.su.md` 和当前存档中的：

```text
<save>/data/stackupup/main.su.md
```

都可以作为规则来源。只有 `# rules` 章节内、带受支持语言标签的 fenced code block 才会被当作 DSL 规则。`example.su.md`
只作为参考文件，不参与加载。

## `.su` 当前语法

### 条件、动作和顺序

一条规则的基本形状是：

```text
条件 -> 动作 [ -> 动作 ... ]
```

动作当前包括：

| 写法     | 作用                 |
|----------|----------------------|
| `-> 128` | 将当前结果设为 `128` |
| `-> +32` | 加上 `32`            |
| `-> -16` | 减去 `16`            |
| `-> *2`  | 乘以 `2`             |
| `-> /2`  | 除以 `2`             |

动作从左到右执行；后命中的规则继续作用于前一条规则的结果。例如，下面两条规则在原始上限为 `64` 时得到 `128`：

```su
item = minecraft:egg -> 64
item = minecraft:egg -> *2
```

动作参数必须是整数。当前实现对 `-> /0` 不抛错，除法步骤会保持当前值，但整个 action 结果最后仍至少为 `1`；不要依赖这种行为。

### 字段和比较运算符

当前字段及其有意义的比较运算如下：

| 字段                | 求值上下文                                                                               | 单值比较                        | 备注                                                    |
|---------------------|------------------------------------------------------------------------------------------|---------------------------------|---------------------------------------------------------|
| `item`              | 完整 item registry ID                                                                    | `=`、`!=`                       | 支持 item 字面量、列表和 `*` 通配；可附加 metadata 简写 |
| `mod`               | registry ID 的 namespace/mod ID                                                          | `=`、`!=`                       | 支持字符串 `*` 通配                                     |
| `type`              | `item` 或 `block`                                                                        | `=`、`!=`                       | 不是通用物品分类                                        |
| `ore`               | 当前栈的矿物辞典名称集合                                                                 | `=`、`!=`                       | 集合中任意名称命中即视为等值命中                        |
| `material`          | GT material resolver 返回的名称或 ID；优先 registry name，缺失时可能回退到 material name | `=`、`!=`                       | 只有成功解析出 material 时才有值                        |
| `meta` / `metadata` | ItemStack metadata 整数                                                                  | `=`、`!=`、`>`、`>=`、`<`、`<=` | `metadata` 是 `meta` 的别名                             |
| `size`              | 规则上下文中的原始 `baseLimit` 整数                                                      | `=`、`!=`、`>`、`>=`、`<`、`<=` | 不是前面动作已经算出的结果                              |
| `tab`               | 创造模式标签页 ID；没有标签页时为空                                                      | `=`、`!=`                       | 只匹配创造模式标签页 ID                                 |

比较运算符的完整集合是 `=`、`!=`、`>`、`>=`、`<`、`<=`。字符串、item 和集合字段只应使用 `=` 或 `!=`；解析器虽然会接受其他符号，但这些字段的
matcher 对排序比较返回不命中。数值字段 `meta` 和 `size` 才适合使用六种数值比较。

列表写法对所有字段都使用等值 matcher：

```su
item in [minecraft:egg, minecraft:snowball] -> 128
metadata in [1, 2, 3] -> 256
```

逻辑运算支持 `&&` 和 `||`，`&&` 优先级高于 `||`；当前不支持括号：

```su
item = minecraft:egg || item = minecraft:snowball && meta = 0 -> 128
```

范围可以写成两个数值条件，也可以写成当前支持的比较链：

```su
size > 2 && size < 64 -> 256
2 < size < 64 -> 256
100 < meta < 300 -> 512
```

这些示例的边界是严格不等式；需要包含边界时使用 `>=` 或 `<=`。

### item 字面量、metadata 与通配

**裸 item** 不约束 metadata。它只按完整 item ID 比较，因此同一个 ID 的不同 metadata 都可以命中：

```su
item = minecraft:wool -> 128
```

**`@meta` 简写**只有在 `@` 后面是整数时才按 metadata 解释，并匹配指定 metadata；其他情况当前可能静默作为普通 pattern：

```su
item = minecraft:wool@14 -> 256
```

**旧的 `:meta` 简写**仍由当前 matcher 兼容。当字面量至少有两个冒号且最后一段能解析为整数时，最后一个冒号后的整数被当作
metadata：

```su
item = minecraft:wool:14 -> 256
```

列表中的 item 字面量复用同一套规则，因此也能使用 `@meta` 或旧的 `:meta` 写法：

```su
item in [minecraft:wool@14, minecraft:wool:15] -> 256
```

`item = *` 是特殊写法，只匹配原始 `baseLimit > 1` 的可堆叠物品；它不会把工具、装备或其他原始上限为 `1` 的物品变成可堆叠物品：

```su
item = * -> 128
```

其他带有 `*` 的 item ID，以及 `mod`、`ore`、`material`、`tab` 等字符串字段，使用字符串通配：一个 `*` 匹配任意长度的字符序列。当前只有
`*` 是通配符，没有 `?` 通配符：

```su
item = minecraft:* -> 128
mod = your_mod_id* -> 256
```

`your_mod_id` 这类写法只是需要替换为整合包实际 ID 的语法示意，不代表某个第三方模组一定存在。

1.12.2 的 `ResourceLocation` path 可以含多个冒号；当前 item matcher 不按冒号数量把字面量判为非法。与此同时，旧 `:meta`
逻辑会检查最后一个冒号后的部分：

- `namespace:path:part` 的最后一段不是整数时，当前实现把整串当作 item ID pattern。
- `namespace:path:14` 的最后一段是整数时，当前实现把它解释为 item ID `namespace:path` 加 metadata `14`。

因此，多冒号 path 与旧 `:meta` 语法在末段为整数时存在歧义；不要用冒号数量判断 ResourceLocation 是否有效。`@*` 当前也不是“任意
metadata”的写法，不能当作已实现语法。

### `.su` 中的注释和模组条件

纯 `.su` 的规则行会在加载时移除以下注释；`.su` 的 `if ... end` 条件预处理发生在注释清理之前：

```su
# 整行或行尾注释
item = minecraft:egg -> 128 // 行尾注释
/* 可以跨行的块注释 */
```

当前没有 DSL 字面量引号，因此不要用引号包住 item ID 或字段值。

当前 `.su` 还保留了按已加载模组过滤的条件块：

```su
if mod = your_mod_id
  item = minecraft:egg -> 256
end
```

这里的 `if mod = ...` 是 `.su` 的条件预处理语法，不是 `.su.md` 的 Markdown 标题；模组未加载时，块内规则被跳过。

## `.su.md` 当前语法

### 最小完整文件

下面文件同时包含 state、无条件规则和 state gate。将它保存为 `.su.md` 后，`expert_mode` 为 `false` 时第二个代码块不生效：

~~~markdown
# state

- expert_mode = false

# rules

## always

```su
item = minecraft:egg -> 128
```

## state ("expert_mode")

```stackupup
item = minecraft:snowball -> 256
```
~~~

### 章节和 fenced code block

- `# state` 和 `# rules` 是一级标题；标题下的普通文字可以作为说明。
- 规则只收集 `# rules` 章节中的代码块。
- 代码块开头的语言标签必须是 `stackupup` 或 `su`（大小写会规范化）；例如 ` ```text`、` ```markdown` 或无语言标签的代码块会被忽略。
- `##` 及更深层标题是 gate 标题；`## always`（小写）表示无条件启用。
- state 声明使用 `- name = true` 或 `* name = false`；值只能是 `true` / `false`。state 章节中的其他普通文字不会被当成声明。

Gate 当前支持：

```text
## always
## state("expert_mode")
## modLoaded("your_mod_id")
## !state("expert_mode") && modLoaded("your_mod_id")
## modLoaded("first_mod", "second_mod")
```

`state` 必须有一个字符串参数；`modLoaded` 的参数会全部检查。Gate 支持 `!`、`&&`、`||`，其中 `&&` 优先于 `||`
；当前不支持用于分组的括号。注意：双引号在 gate 参数中是当前实现的一部分，但 DSL 规则字面量本身还没有引号语法。

`.su.md` 的规则代码块仍使用 `.su` 的字段、动作、item 字面量和注释规则，但 `#` 有额外的 Markdown 含义。扫描器遇到 fenced code
block 内一行去掉前导空格后以 `# ` 开头的内容时，会把它当作新的 Markdown 标题并结束当前 fence。因此，`.su.md` 的 DSL
代码块内不要使用 `# comment`；请改用 `// comment`，或使用不含此类行的块注释。

### state 命令与 reload

状态命令读写当前存档的：

```text
/stackupup state get <name>
/stackupup state set <name> <true|false>
```

`set` 还接受 `1` / `0`、`yes` / `no`、`on` / `off`。命令只操作当前存档的 `<save>/data/stackupup/main.su.md`；每个 `.su.md`
文件声明的 state 只用于该文件自己的 gate，不会自动共享给其他 Markdown 文件。

`state set` 写入内容发生变化后会自动调用规则 reload，不需要再执行 `/stackupup reload`。设置成文件中已有的相同值时不会写文件，也不会触发这次自动
reload。直接编辑规则文件、修改 `.su` 或修改配置目录中的 `.su.md` 后，仍需执行：

```text
/stackupup reload
```

当前 `state get` 的底层缺失值和显式 `false` 都是 `false`；命令层因此可能把显式 `false` 与未声明状态显示成同一类“状态不存在”提示。

## 当前有效加载顺序

下面列的是 reload 后快照中的实际规则评估顺序；不存在的文件会跳过：

1. `<save>/data/stackupup/main.su.md`。
2. `config/stackupup/*.su.md`，按文件名排序，排除 `example.su.md`。
3. 旧兼容文件 `config/stackupup-rules.su`，仅在 `config/stackupup/main.su` 不存在时加载。
4. `<save>/data/stackupup/world.su`。
5. `config/stackupup/*.su`，按文件名排序，排除 `user.su` 和 `example.su`；`main.su` 包含在这一组中。
6. `config/stackupup/user.su`。

加载器先按后缀把 Markdown 文件和纯 `.su` 文件分流，再分别合并，所以不要把源码中“发现文件”的顺序误认为最终规则顺序。每个文件内部保留原有行顺序；命中的规则会按上面顺序依次应用。

## 诊断、已知限制与后续任务

### 当前实现和已知限制

- DSL 解析错误和 gate 错误当前带来源文件名和行号；state 声明错误目前以 `[state]` 报告，不带 Markdown
  来源文件名。所有这些错误都没有可供作者依赖的列号或 token 精确位置。
- 同一个 `RuleLineLoader` 输入批次内，规则解析遇到第一个错误后停止继续编译该批；错误前已经成功编译的规则仍会保留在报告中。
  `DslRuleSource.fromFiles` 会把纯 `.su` 文件合并到同一批，而 Markdown 文件分别处理。
- `material` 只有在运行时成功解析出 material 时才有值；缺失值不会命中 `material = ...`、`material != ...` 或
  `material in [...]`。
- `ore = ...` 会检查 ore 名称集合中的任意元素；`ore != ...` 是对该命中判断取反，因此空集合也可能命中负比较。
- 多冒号 ResourceLocation path 当前不会按冒号数量拒绝，但与旧 `:meta` 简写的整数后缀存在歧义。
- DSL 字面量中不支持引号；`@` 或旧 `:` 后跟非整数时，当前实现可能把整串静默保留为 pattern，而不是给出专门的非法 metadata
  诊断。

### 测试覆盖边界

以下行为目前尚无完整独立的行为测试；相关说明来自源码核对和部分测试覆盖：

- 多冒号 ResourceLocation path 与旧 `:meta` 简写的歧义。
- `legacy reload`：旧 `config/stackupup-rules.su` 仅在 `config/stackupup/main.su` 缺失时加载的回退路径。
- fence `# `：fenced code block 内去掉前导空格后以 `# ` 开头的行结束当前 fence 的行为。
- state reload：`state set` 改变值时触发自动 reload、设置为相同值时不触发 reload 的行为。

因此，上述说明不能写成完整行为验证，也不能宣称当前语法已被全量测试覆盖。

### 后续任务（不是当前实现）

以下内容只作为后续解析器/诊断任务的边界记录，不能用于判断当前版本已经支持：

- T7.1 的两段式 tokenizer 仍未实现：尚未先按 `->` 分出条件侧与动作侧，再分别使用条件字面量模式和动作表达式模式；`+`、`-`、
  `*`、`/` 的条件/动作边界（条件侧字面量字符、动作侧运算符）以及 DSL 规则字面量的引号词法仍未实现。当前文中的动作和 gate
  示例只描述现有实现，不代表 T7.1 已完成。
- 为 DSL 字面量增加经过定义的引号语法，并对显式非法 metadata 进行 fail-fast 诊断。
- 增加真实的精确位置（尤其列号）诊断；当前的文件名和行号不能冒充列级定位。
- 增加专用 `RangeConditionAst` 并重新定义比较链的 AST 产出；当前范围链只是由现有比较条件组合得到。
- 在保留 1.12.2 多冒号 ResourceLocation path 的前提下，解决旧 `:meta` 语法的歧义。

本页此次只更新文档，不实现上述后续任务，也不改变 README 中已有的文档路径。
