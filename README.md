## StackUpUp

StackUpUp 是一个面向 **Minecraft 1.12.2** 的堆叠上限重构模组。

它脱胎于 [StackUp](https://github.com/asiekierka/StackUp)，但当前目标已经不再是简单延续旧实现，而是逐步重建一套更稳定、对 `metadata` 物品更友好的新堆叠内核。

## 现在能解决什么

1. 支持普通物品堆叠突破 64。
2. 支持针对同一物品 ID、不同 `metadata` 变体编写规则。
3. 支持 1.12.2 的 **矿物辞典** 条件。
4. 支持对 GregTech CEu 这类大量使用 `metadata` 区分子物品的模组进行更上层的统一兼容，而不是只对单一模组写特判。
5. 支持在物品数量显示和 tooltip 中更清楚地展示大堆叠数量。

## 和原版 StackUp 的主要差异

1. 目标版本固定为 **1.12.2**。
2. 规则系统已经转向新的 **DSL v2**，重点支持 `item + meta + ore` 组合匹配。
3. 规则匹配粒度不再停留在粗糙的 `Item` 级，而是尽量向 `ItemStack` 语义靠拢。
4. 兼容修复优先采用更现代的 `MixinBooter + Mixin` 路线，逐步收缩旧 ASM 边界。
5. 不再把“全局最大堆叠值”当作主要配置入口，规则文件才是公开入口。

## 快速使用

主规则文件：

```text
config/stackupup/main.su
```

如果文件不存在，游戏会自动创建带注释的示例模板。

修改规则后可在游戏内执行：

```text
/stackupup reload
```

如果想直接打开主规则文件：

```text
/stackupup edit
```

这个命令只适用于本地带桌面环境的客户端，不适用于无桌面的专用服务端。

## 规则示例

```text
# 让鸡蛋堆到 256
item = minecraft:egg -> 256

# 某个模组下的物品统一提高上限
mod = thermal -> 1024

# 同 ID、不同 metadata 的物品精确匹配
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512

# 按矿物辞典处理
ore = ingotSteel -> 2048

# 列表匹配
item in [minecraft:egg, minecraft:snowball] -> 128

# 条件匹配
2 < size < 64 -> 256
```

更完整的写法见：

1. [docs/DSL-v2-规则示例.md](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)
2. [docs/DSL-v2-迁移说明.md](docs/DSL-v2-%E8%BF%81%E7%A7%BB%E8%AF%B4%E6%98%8E.md)

## 当前规则重点

1. 只支持 **1.12.2**，因此只有 **OreDictionary**，没有高版本标签系统。
2. 支持 `#`、`//`、`/* ... */` 注释。
3. 支持 `item`、`mod`、`ore`、`meta`、`size` 等条件。
4. 支持 `&&` 与 `||`，当前优先级为 `&&` 高于 `||`。
5. 规则只在加载和重载时解析，运行时不会为每个 `ItemStack` 重新解析 DSL。

## 适合谁使用

1. 想在 1.12.2 整合包里统一调整物品堆叠上限的玩家。
2. 想给 GregTech CEu、热力、工业、仓储类模组做更稳定堆叠规则的整合包作者。
3. 想保留简单文本配置，而不是引入过重脚本系统的服主或包作者。

## 安装注意

1. 适用游戏版本为 **Minecraft 1.12.2**。
2. 当前开发与测试环境基于 **Forge 14.23.5.2847**。
3. 运行时需要同时安装 [`MixinBooter`](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) 与 [`Forgelin-Continuous`](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous)。
4. 当前依赖下限为 `MixinBooter >= 10.0`、`Forgelin-Continuous >= 2.1.0.0`。

## 兼容性说明

StackUpUp 追求的是“尽量在更上层统一兼容”，不是围绕单个模组堆越来越多的特判。

这意味着：

1. 对遵循原版堆叠语义的大多数模组，通常可以直接生效。
2. 对自己额外写死 `64`、或自带特殊库存逻辑的模组，仍可能需要额外兼容补丁。
3. 当前已经优先覆盖了一批常见仓储与容器路径，但迁移仍在继续。

面向玩家的兼容性概览见：

1. [docs/StackUpUp-实现与兼容性说明.md](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)

开发细节与迁移状态见：

1. [docs/developer/Cleanroom-对齐与架构说明.md](docs/developer/Cleanroom-%E5%AF%B9%E9%BD%90%E4%B8%8E%E6%9E%B6%E6%9E%84%E8%AF%B4%E6%98%8E.md)
2. [docs/ASM-迁移状态.md](docs/ASM-%E8%BF%81%E7%A7%BB%E7%8A%B6%E6%80%81.md)

## 来源与致谢

1. 本项目脱胎于 [StackUp](https://github.com/asiekierka/StackUp)。
2. 原项目的 [CurseForge 页面](https://www.curseforge.com/minecraft/mc-mods/stackup) 标注许可为 `LGPLv3`。
3. StackUpUp 当前是在其基础上面向 1.12.2 长期维护、逐步重构与现代化迁移的分支。
