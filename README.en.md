# StackUpUp

**Raise item stack limits beyond 64 for Minecraft 1.12.2.**

[![CurseForge](https://img.shields.io/badge/CurseForge-StackUpUp-orange)](https://www.curseforge.com/minecraft/mc-mods/stackupup)

StackUpUp is a stack-limit mod for Minecraft 1.12.2 modpacks and servers. It lets pack authors describe stack-size rules in text files, with first-class support for metadata items, Ore Dictionary names, vanilla inventory paths, and compatibility patches for common hard-coded `64` limits.

中文说明：[README.md](README.md)

## Status

- Target: Minecraft **1.12.2** + Forge **14.23.5.2847**
- Current version: **0.2.4**
- Rule system: DSL v2, using `.su` files or Markdown `.su.md` containers with `state` and `gate`
- Compatibility layer: MixinBooter + Mixin first, with ASM kept only for legacy compatibility and early-loading fallbacks
- New in 0.2.4: late mixin support for IntegratedDynamics, LimeLib, and ImmersiveEngineering

## Download

Download StackUpUp from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackupup).

## Installation

1. Install Minecraft **1.12.2** and Forge **14.23.5.2847**.
2. Install the required dependencies:
   - [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) **10.0 or newer**
   - [Forgelin-Continuous](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous) **2.1.0.0 or newer**
3. Put the StackUpUp jar into the `mods/` folder.
4. Start the game or server once so the config and rule directories are generated.

## Rule Files

Rules live under `config/stackupup/`. The recommended entry point is:

```text
config/stackupup/main.su
```

Use a Markdown container when you want comments, state switches, or conditional gates in the same file:

```text
config/stackupup/main.su.md
```

Later matching rules continue from or override the result produced by earlier rules. The current effective evaluation order is:

1. `<save>/data/stackupup/main.su.md`.
2. `config/stackupup/*.su.md`, sorted by file name, excluding example files.
3. Legacy `config/stackupup/stackupup-rules.su`, only when `main.su` does not exist.
4. `<save>/data/stackupup/world.su`.
5. `config/stackupup/*.su`, sorted by file name, excluding `user.su` and example files; `main.su` is included in this step.
6. `config/stackupup/user.su`.

See [docs/DSL-v2-规则示例.md](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md) for syntax examples.

## Commands

```text
/stackupup reload
/stackupup edit
/stackupup state
```

- `reload`: reload rule files.
- `edit`: open the main rule file on the client.
- `state`: inspect or update state switches declared in `.su.md` rule files.

## DSL Examples

```su
item = minecraft:egg -> 256
item = * -> 128
mod = thermal -> 1024
ore = ingotSteel -> 2048
material = steel -> 2048

item in [minecraft:egg, minecraft:snowball] -> 128
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512
item = gregtech:gt.metaitem.01@11305 -> 512
item = minecraft:wool:14 -> 512

2 < size < 64 -> 256
tab = buildingBlocks -> 256
```

Actions can be chained to keep transforming the current result:

```su
item = gregtech:gt.metaitem.01@11305 && ore = ingotSteel -> 1024
material = steel && mod = gregtech -> 1024
ore = ingotSteel -> 512 -> *2
```

`material` is an optional match field. It only has a value when GregTech is loaded and the item can be resolved to a GT material; when GT is not loaded, resolution fails, or the item is not a GT material item, every `material` condition is treated as not matched, including `!=` and list matches. Use the material registry name: native GT materials can use names such as `steel`; use the `modid:name` format when you need to distinguish materials across mods, without treating unverified concrete material IDs as examples. This does not promise support for every GT item.

The old `category` field is not needed on 1.12.2 and should not be used:

```su
# Not recommended
category = potion -> 32
category = enchanted_book -> 16
```

## Client Display

Large stack counts are automatically scaled or abbreviated in inventory slots. Version 0.2.4 adds `alwaysCompactNumbers`, which forces capped compact text before fitting and any font scaling:

```text
1-999
1K-99K
0.1M-99M
0.1B-2.1B
```

If abbreviated slot text is not enough context, enable the tooltip stack display option to show the current count and max stack limit as `count/limit`.

## Compatibility

StackUpUp usually works out of the box for mods that follow vanilla stack-size semantics. Mods that hard-code `64`, bypass `ItemStack#getMaxStackSize()`, or implement custom inventory logic may need targeted patches.

The core safety rule is: **advertised capacity must not be larger than real write capacity.** Dynamic ASM is retained only for old unknown `IInventory`, `Slot`, and similar legacy inventory paths; dynamic ASM for unknown `IItemHandler` implementations is intentionally disabled. Even when an unknown `IItemHandler#getSlotLimit()` literally returns 64, that is not proof that the real write capacity can be raised. Advertising a higher value in that case lets vanilla push too many items into storage that cannot actually accept them, which can cause truncation, item loss, or conflicts with the mod's own overflow handling.

For known mods, StackUpUp uses MixinBooter late mixins to raise the real `getInventoryStackLimit()` / `getSlotLimit()` style entry points, then lets slot limits follow that real capacity. Old ASM remains only as a legacy and early-loading fallback. New compatibility work should prefer mixins, and the old write-after-the-fact remainder-system is not part of the current design.

Current late mixin coverage includes:

- Applied Energistics 2
- Actually Additions
- BrandonsCore
- CyclopsCore
- Ender IO
- IC2
- Mantle
- Refined Storage
- Simple Storage Network
- IntegratedDynamics
- LimeLib
- ImmersiveEngineering

Each compatibility module can be toggled under the `compatibility` section of `config/stackupup.cfg`. Changes require a game or server restart.

For implementation notes, see [docs/StackUpUp-实现与兼容性说明.md](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md).

## Differences From StackUp

- StackUpUp targets Minecraft **1.12.2**.
- Rules use **DSL v2**, designed for item IDs, metadata, Ore Dictionary names, and creative-tab matching.
- Compatibility is centered on **MixinBooter + Mixin**, with ASM reduced to legacy and early-loading support.
- Client display includes scaling, abbreviation, and tooltip support for very large stack counts.

## Development And Verification

Common verification commands:

```powershell
.\gradlew.bat test
.\gradlew.bat compileTestKotlin
.\gradlew.bat spotlessCheck
```

The repository also includes local development auto-test tasks for server and client rule checks. See [docs/runServer-自动化回归.md](docs/runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md).

## Origin

StackUpUp descends from [StackUp](https://github.com/asiekierka/StackUp) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackup), LGPLv3).
