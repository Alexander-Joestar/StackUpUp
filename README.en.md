# StackUpUp

**Raise item stack limits beyond 64 for Minecraft 1.12.2.**

[![CurseForge](https://img.shields.io/badge/CurseForge-StackUpUp-orange)](https://www.curseforge.com/minecraft/mc-mods/stackupup)

StackUpUp is a stack-limit mod for Minecraft 1.12.2 modpacks and servers. It lets pack authors describe stack-size
rules in text files, with first-class support for metadata items, Ore Dictionary names, vanilla inventory paths, and
compatibility patches for common hard-coded `64` limits.

Chinese README: [README.md](README.md) (Chinese)

## Status

- Target: Minecraft **1.12.2** + Forge **14.23.5.2847**
- Current version: **0.2.4**
- Rule system: DSL v2, using `.su` files or Markdown `.su.md` containers with `state` and `gate`
- Compatibility layer: MixinBooter **11.17** + CleanMix **0.7.2**, with the current compatibility layer implemented through Mixin configs registered by `IMixinConnector`; `StackUpUpCore` returns no ASM transformers. MixinBooter 10.7 and the old loaders are historical baselines only

## Download

Download StackUpUp from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackupup).

## Installation

1. Install Minecraft **1.12.2** and Forge **14.23.5.2847**.
2. Install the required dependencies:
    - [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) **11.17**
    - [Forgelin-Continuous](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous) **2.3.0.0**
      Current project build validation uses these versions; other versions have not been verified by this repository.
3. Put the StackUpUp jar into the `mods/` folder.
4. Start the game or server once so the config and rule directories are generated.

## Rule Files

Rules live under `config/stackupup/`. The recommended entry point is `config/stackupup/main.su`; use the Markdown
container `config/stackupup/main.su.md` when you want comments, state switches, or conditional gates in the same file.

Later matching rules continue from or override the result produced by earlier rules. The current effective evaluation
order is:

1. `<save>/data/stackupup/main.su.md`.
2. `config/stackupup/*.su.md`, sorted by file name, excluding example files.
3. Legacy `config/stackupup-rules.su`, only when `main.su` does not exist.
4. `<save>/data/stackupup/world.su`.
5. `config/stackupup/*.su`, sorted by file name, excluding `user.su` and example files; `main.su` is included in this step.
6. `config/stackupup/user.su`.

See [docs/DSL-v2-规则示例.md](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md) (Chinese) for syntax examples.

## Commands

Commands: `/stackupup reload`, `/stackupup edit`, `/stackupup state get <name>`, `/stackupup state set <name> <value>`.

- `reload`: reload rule files.
- `edit`: open the main rule file on the client.
- `state get <name>`: read a state from the current save's `<save>/data/stackupup/main.su.md`; other `.su.md` files do not share these states.
- `state set <name> <value>`: update a state in the same file; `<value>` is case-insensitive and accepts `true` / `false`, `1` / `0`, `yes` / `no`, and `on` / `off`; a `reload` is triggered only when the write actually changes the evaluation result of some gate.

If `config/stackupup/main.su` is absent while the legacy `config/stackupup-rules.su` exists, the first DSL-enabled
`reload` includes the legacy file through the fallback and creates the main file; later `reload`s may then no longer
load the legacy file, so its rules may stop taking effect (migration behavior is not finalized).

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

Actions can be chained to keep transforming the current result, e.g. `item = gregtech:gt.metaitem.01@11305 && ore = ingotSteel -> 1024`,
`material = steel && mod = gregtech -> 1024`, `ore = ingotSteel -> 512 -> *2`.

`material` is an optional match field: it only has a value when GregTech is loaded and the item can be resolved to a GT
material; when GT is not loaded, resolution fails, or the item is not a GT material item, every `material` condition
(including `!=` and list matches) is treated as not matched. Use the material registry name: native GT materials can use
names such as `steel`; use the `modid:name` format when you need to distinguish materials across mods, without treating
unverified concrete material IDs as examples. This does not promise support for every GT item.

The old `category` field is not needed on 1.12.2 and should not be used, e.g. `category = potion -> 32`,
`category = enchanted_book -> 16`.

## Client Display

Large stack counts are automatically scaled or abbreviated in inventory slots; version 0.2.4 adds `alwaysCompactNumbers`,
which forces capped compact text before fitting and any font scaling, e.g. `1-999`, `1K-99K`, `0.1M-99M`, `0.1B-2.1B`.

If abbreviated slot text is not enough context, enable the tooltip stack display option to show the current count and
max stack limit as `count/limit`.

## Configuration

After the first game or server launch, the configuration file is `config/stackupup.cfg`; its groups match the in-game
configuration screen. Save the configuration and restart, or run `/stackupup reload` to reapply values that are reloadable:

- `general.maxStackSize`: global compatibility stack limit, default `64`; rule results and compatibility entry points are clamped to this value.
- `compat.vanilla.craftingSlotLimit`: workbench grid and crafting-result slot limit, default `64`. `0` keeps vanilla/default behavior; values above `0` use the custom limit. This is mainly a Performance setting: holding Shift to batch-craft very large stacks can cause severe lag, not a balance switch. The effective limit can still be reduced by `general.maxStackSize` and item/recipe limits.
- `compat.nuclearcraft.speedUpgradeLimit` and `compat.nuclearcraft.energyUpgradeLimit`: NuclearCraft speed/energy upgrade stack limits, default `0` (keep NuclearCraft's default `64`); values above `0` use a custom limit. These settings have no effect when NuclearCraft is not loaded.

## Compatibility

StackUpUp usually works out of the box for mods that follow vanilla stack-size semantics. Mods that hard-code `64`,
bypass `ItemStack#getMaxStackSize()`, or implement custom inventory logic may need targeted patches.

The core safety rule is: **advertised capacity must not be larger than real write capacity.** Unknown `IItemHandler`
implementations are not dynamically expanded. Even when an unknown `IItemHandler#getSlotLimit()` literally returns 64,
that is not proof that the real write capacity can be raised. Advertising a higher value in that case lets vanilla push
too many items into storage that cannot actually accept them, which can cause truncation, item loss, or conflicts with the
mod's own overflow handling.

Current compatibility patches are Mixin-only: `StackUpUpMixinConnector` registers Mixin configs through
`IMixinConnector`, and `StackUpUpCore` returns no ASM transformers. For registered targets currently attempted for
loading, these configs target real `getInventoryStackLimit()` / `getSlotLimit()` style entry points so slot limits follow.
When third-party source is unavailable, the real write path is "无源码不可判定" (cannot be determined without source),
and class names or registration alone do not establish write capacity. AE2 Plan A is archived: its hot path passes
`insertItem` through without a project wrapper, relying on the vanilla/Forge remainder contract and a boundary probe; no
JSONL conservation report or write-after-the-fact remainder refill is used.

Current late mixin targets registered for attempted loading (the `StackUpUpMixinConnector` module table, 17 configs):

- Applied Energistics 2
- Applied Energistics 2 Supergiant
- Actually Additions
- BrandonsCore
- ColossalChests
- CyclopsCore
- Ender IO
- GregTech
- IC2
- Mantle
- NuclearCraft
- Tech Reborn (via RebornCore)
- Refined Storage
- Simple Storage Network
- IntegratedDynamics
- LimeLib
- ImmersiveEngineering

### Disabling a single mod's compatibility patch

StackUpUp does not ship a per-mod toggle, and does not need one: use MixinBooter's own blacklist to turn off any
single patch.

Edit `config/mixinbooter.cfg` and add the config file name to `blacklistedConfigs` in the `general` section:

```
general {
    # Mixin configurations that should never be loaded.
    S:blacklistedConfigs <
        mixins.stackupup.late.enderio.json
     >
}
```

- The entry is the **config file name** (including `.json`), not a toggle name. MixinBooter matches the whole string
  exactly, so `enderio` or `EnderIO` will not match.
- Restart the game or server afterwards: MixinBooter reads this config and applies the blacklist during the coremod
  phase, so a runtime reload such as `/stackupup reload` will neither apply nor undo it.
- A blacklisted config is simply not loaded, so that mod's compatibility patch has no effect at all. Listing a config
  that would not have been loaded anyway is harmless. Do not blacklist the core config
  `mixins.stackupup.early.json`, as that also disables the base paths such as vanilla inventories.

The 17 config file names currently available (taken from the `mixins.stackupup.late.*.json` files actually present in
the jar):

```
mixins.stackupup.late.actuallyadditions.json
mixins.stackupup.late.ae2.json
mixins.stackupup.late.ae2supergiant.json
mixins.stackupup.late.brandonscore.json
mixins.stackupup.late.colossalchests.json
mixins.stackupup.late.cyclopscore.json
mixins.stackupup.late.enderio.json
mixins.stackupup.late.gregtech.json
mixins.stackupup.late.ic2.json
mixins.stackupup.late.immersiveengineering.json
mixins.stackupup.late.integrateddynamics.json
mixins.stackupup.late.limelib.json
mixins.stackupup.late.mantle.json
mixins.stackupup.late.nuclearcraft.json
mixins.stackupup.late.refinedstorage.json
mixins.stackupup.late.techreborn.json
mixins.stackupup.late.storagenetwork.json
```

For implementation notes, see [docs/StackUpUp-实现与兼容性说明.md](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md) (Chinese).

## Differences From StackUp

Compared with StackUp, StackUpUp targets Minecraft **1.12.2**, uses **DSL v2** rules designed for item IDs, metadata,
Ore Dictionary names, and creative-tab matching, centers compatibility on **MixinBooter + Mixin**, and adds client display
scaling, abbreviation, and tooltip support for very large stack counts.

## Development And Verification

**The build requires JDK 25 and Gradle 9.2+.** Minecraft is wired in through RetroFuturaGradle (RFG) **2.0.2**, whose
plugin classes are compiled to class file 69 (Java 25) and which itself requires Gradle >= 9.2. Running Gradle on JDK 21
or lower fails during configuration (`UnsupportedClassVersionError: com/gtnewhorizons/retrofuturagradle/UserDevPlugin
... class file version 69.0`); the build never starts. The bundled wrapper is Gradle 9.4.0, which satisfies the
minimum, so no wrapper change is needed.

This constrains the **Gradle JVM used for builds** only: the project Java toolchain stays pinned to 8
(`build-logic/convention/src/main/kotlin/jvm.gradle.kts:9`), and the dev client is launched by that Java 8 toolchain.
The mod's runtime requirements are unchanged.

Common verification commands (with `JAVA_HOME` pointing at JDK 25):

```powershell
.\gradlew.bat test
.\gradlew.bat compileTestKotlin
.\gradlew.bat spotlessCheck
```

The repository also includes local development auto-test tasks for server and client rule checks.
See [docs/runServer-自动化回归.md](docs/runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md) (Chinese).

## Origin

StackUpUp descends from [StackUp](https://github.com/asiekierka/StackUp)
([CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackup), LGPLv3).
