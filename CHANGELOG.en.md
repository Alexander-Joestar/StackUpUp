# Changelog

## 0.2.5

This release changes how modded containers are allowed to hold more than 64 items: instead of a per-mod patch, StackUpUp now identifies concrete container classes at class load by their hierarchy and replaces the literal `64` in their capacity getters with a dynamic lookup. Vanilla chests, most mod machines, and other `TileEntity` inventories whose capacity getter is a literal 64 therefore accept larger stacks without any per-mod work. Item stack limits themselves are still decided entirely by the rule files, item-level granularity is unchanged, and the rule evaluation path was not touched. A mod that genuinely conflicts can be added to `compat.inventoryStackLimitBlacklist.modIds` as a temporary bypass; an empty list does nothing. The safety boundary is also unchanged: the capacity advertised is never larger than the real capacity of the matching write path.

### Architecture

- Item rules are still the primary path and were not changed in this release: `ItemMixin` and `ItemStackMixin` hand every evaluation to the current rule snapshot (`StackLimitHooks.applyDynamicStackLimit`), and the result is then clamped by `general.maxStackSize` (`src/main/java/io/alexjoest/stackupup/mixin/early/ItemMixin.java:12-24`, `ItemStackMixin.java:17-18`, `src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:188-205`). The capacity patch below only deals with a hard-coded 64 inside inventory getters and takes no part in rule evaluation.
- Capacity getters are now patched structurally in bytecode: at class load, `InventoryStackLimitTransformer` identifies concrete container classes by hierarchy (classes that implement `IInventory`, implement `IItemHandler`, or extend `Slot`, regardless of package, so vanilla classes are matched too) and replaces the literal `64` (`BIPUSH 64`) inside their capacity methods with a dynamic lookup call, equivalent to StackUp's literal 64 replacement (`src/main/java/io/alexjoest/stackupup/core/InventoryStackLimitTransformer.java:72-104,160-188`). Targets are not chosen from a class-name allowlist, and write methods are not rewritten.
- Only capacity getters are replaced: `getInventoryStackLimit()I`/`func_70297_j_`, `getSlotLimit(I)I`, `getSlotStackLimit()I`/`func_75219_a`, and `getItemStackLimit(ItemStack)`/`func_178170_b` (`InventoryStackLimitTransformer.java:127-151`). The write side is still handled by `TileEntityLockableLoot#setInventorySlotContents` as wrapped by `InventoryWriteLimitMixin` (`src/main/java/io/alexjoest/stackupup/mixin/early/InventoryWriteLimitMixin.java:16-29`); the vanilla subclasses that this Mixin does not cover are listed in the transformer's class comment and remain a known limitation, not a guarantee added by this release.
- The decision happens at call time rather than at class load: the injected call is unconditional, and the real decision (expansion switch, whether the policy is ready, the blacklist, and the compatibility limit) is read from the current state by `ClassLoadPolicy.shouldPatch` while the getter runs. Classes transformed before the startup policy is published therefore do not stay unpatched for the whole session; they return the vanilla 64 until the policy is ready (`InventoryStackLimitTransformer.java:26-28,168-186`, `InventoryStackLimitHook.java:126-137`).
- The exclusion list only covers capacity managed by configuration: `InventoryCrafting` and `InventoryCraftResult` are managed by `compat.vanilla.craftingSlotLimit` and `VanillaInventoryLimitMixin`, so the transformer skips them; interfaces, abstract classes, and this mod's own package are skipped as well (`InventoryStackLimitTransformer.java:46-55,86-95,113-125`).

### Compatibility

- Added capacity support for the [Mystical Agriculture](https://github.com/BlakeBr0/MysticalAgriculture) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mystical-agriculture)) Essence Reprocessor family: the new capacity-only config `mixins.stackupup.capacity.late.mysticalagriculture.json` registers a single `@Pseudo` target, the abstract base class `TileEssenceReprocessor`, and intercepts `func_70297_j_()I` and `getInventoryStackLimit()I` with `@ModifyReturnValue` (`src/main/java/io/alexjoest/stackupup/mixin/late/mysticalagriculture/MysticalAgricultureInventoryLimitMixin.java:25-35`, `src/main/resources/mixins.stackupup.capacity.late.mysticalagriculture.json:1-8`, registered at `src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpMixinConnector.kt:324`).
- Only the abstract base class is targeted instead of registering each subclass because `InventoryStackLimitTransformer` skips abstract classes at class level via `ACC_ABSTRACT` (`InventoryStackLimitTransformer.java:86-95`), while `TileEssenceReprocessor` is a `public abstract class` whose getter body is the literal `bipush 64; ireturn`; none of the six concrete subclasses (`TileInferiumReprocessor`, `TileIntermediumReprocessor`, `TilePrudentiumReprocessor`, `TileSuperiumReprocessor`, `TileSupremiumReprocessor`, `TileUltimateReprocessor`) overrides that getter, so patching the base covers all six machines (`javap` on `MysticalAgriculture-1.12.2-1.7.5.jar`). EnderIO's abstract base class `AbstractInventoryMachineEntity` is the precedent for the same approach (`src/main/java/io/alexjoest/stackupup/mixin/late/enderio/EnderIOMachineInventoryLimitMixin.java:21-34`).
- The write side was not changed and does not need to be: `TileEssenceReprocessor.func_70299_a` (`setInventorySlotContents`) calls the same `func_70297_j_()` before committing and clamps oversized counts back to that value with `ItemStack.func_190920_e` (same jar bytecode, `func_70299_a` offsets 57-71), so raising the getter raises the real stored limit. This conclusion comes from jar bytecode plus a repository structural test (`src/test/kotlin/io/alexjoest/stackupup/mixin/MysticalAgricultureMixinSourceTest.kt:12-103`); in addition, the maintainer tested the six essence reprocessors in a running game and did not reproduce the oversized-insert issue. That in-game test covers this path only, was not a matrix-wide validation, and is not a runtime compatibility promise for other environments or other mods.

### Cleanup

- Removed the Farmer's Delight Legacy capacity Mixin: its Basket, Cabinet, Cooking Pot, and Skillet are concrete `IInventory` implementations whose `getInventoryStackLimit()` only returns the literal `64`, which the structural transformer above already covers, so `FarmersDelightInventoryLimitMixin` and both `farmersdelight` configs were deleted (commit `925fda5e7ca7`).
- Removed the AE2 Supergiant `InternalInventoryMixin`, which never took effect: the `ae2.core.inventory.BaseInternalInventory` it targeted does not exist in the Supergiant source tree (the actual path is `ae2/api/inventories/BaseInternalInventory.java`), and `@Pseudo` + `require=0` kept that mistake silently inert; the config keeps `AppEngInternalInventoryMixin` and `ContainerAdapterMixin` (commit `191beb94560a`, `src/main/resources/mixins.stackupup.capacity.late.ae2supergiant.json:8-10`).
- Removed the EnderIO `TileEnchanter` target and the Immersive Engineering `IEInventoryHandler#getSlotLimit` handler: both were literals of 64 on concrete classes that the transformer already rewrites at class load. The EnderIO abstract base class `AbstractInventoryMachineEntity` handler and the IE wrapper around `IIEInventory#getSlotLimit` inside `insertItem` are kept (`EnderIOMachineInventoryLimitMixin.java:9-34`, `IEInventoryHandlerMixin.java:9-33`, commit `8a68e83e7000`).
- `compat.inventoryStackLimitBlacklist.modIds` is an emergency bypass only: an empty list does nothing; when non-empty it uses Forge `ownedPackages` to bypass capacity expansion for the actual receiver or a supported public owner and skips the listed mods' capacity-late configs, without disabling their ordinary/safety configs (`src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpMixinConnector.kt:117-159,315-332`). It can bypass capacity; it cannot be used to prove that shared ownerless instances or private delegate paths are safe.

### Other

- Added the diagnostic switch `debug.mergeLimitTrace` (default `false`): it logs each container merge slot-limit resolution for a single reproduction and does not change the resolved value (`src/main/kotlin/io/alexjoest/stackupup/StackUpUpConfig.kt:162-169`).
- Added repository regression tests for `item = * -> *N` wildcard multiplication and large chest merging. These are in-repository test evidence, not third-party runtime validation or a blanket compatibility promise.

## 0.2.4

### Stability and fixes

- Fixed startup crashes caused by the `func_70297_j_` method mismatch under production/Notch-obfuscated environments and by Forge Loader initialization.
- Fixed a startup crash where [AE2 Supergiant](https://github.com/CleanroomMC/Applied-Energistics-2) interface validation raised `InvalidMixinException`.
- Fixed [NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/nuclearcraft-overhauled)) output slots clamping at 64 and swallowing items.
- Fixed [TechReborn](https://github.com/TechReborn/TechReborn) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/techreborn)) / [RebornCore](https://github.com/TechReborn/RebornCore) machines wrongly treating the inventory as full and stalling after reaching 64 output.
- Improved AE2 compatibility: both legacy [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2)) and the newer AE2 Supergiant are supported.
- Container-insert safety guard: Shift quick-store now aligns with mouse insert, and modded containers that are not adapted safely fall back to the vanilla 64 limit.

### Configuration and features

- Added the `compat.vanilla.craftingSlotLimit` config, which can limit workbench slot capacity to prevent stutter when batch-crafting very large stacks with Shift.
- Added the `client.alwaysCompactNumbers` config, which can show compact abbreviated counts such as `1.5K` and `0.1M` in slots.
- Opened stack-limit configuration for [NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft) speed and energy upgrade slots.
- Broad compatibility update and package restructuring: redundant pseudo-switches were removed and MixinBooter's official blacklist mechanism is used throughout.
- Compatibility targets cover [Ender IO](https://github.com/SleepyTrousers/EnderIO-1.5-1.12), [GregTech CEu](https://github.com/GregTechCEu/GregTech), [ProjectE](https://github.com/sinkillerj/ProjectE), [IC2](https://www.curseforge.com/minecraft/mc-mods/industrial-craft), [Mantle](https://github.com/SlimeKnights/Mantle), [Refined Storage](https://github.com/refinedmods/refinedstorage), [Integrated Dynamics](https://github.com/CyclopsMC/IntegratedDynamics), [Immersive Engineering](https://github.com/BluSunrize/ImmersiveEngineering), and others.

## 0.2.3

- Removed the `category` field, which is no longer needed on 1.12.2
- Fixed rules possibly being evaluated more than once
- Optimized the code structure

## 0.2.2

- Fixed a crash on clicking the ModularUI/RSB backpack GUI: `BackpackContainer cannot be cast to ContainerAccessor`
- Fixed an `IllegalClassLoadError` caused by `ContainerState` living in a mixin-owned package
- Fixed injection failures when `@WrapOperation` wraps `setItemStack` and `dropItem` without an instance-method receiver
- Simplified the duplicated IDEA run configuration and auto test task boilerplate in `build.gradle.kts`
- Simplified `EarlyMixinConfigTest` by merging similar IO reading tests
- Added the `item = *` wildcard, matching every stackable item (excluding tools, armor, and others with `baseSize=1`)
- Added the `tab` field for matching by creative tab (e.g. `tab = buildingBlocks`)
- Added the `category` field (removed in a later version)
- Added the `meta` range syntax (`100 < meta < 300`)
- `RuleField` simplification: dropped the redundant `id` constructor parameter, made `matchers`/`byName` lazy, and made `fromIdentifier` do a single uppercase lookup
- Introduced the `FieldType` enum, with the compiler dispatching by type so a new field only needs one enum line
- Removed the empty `RuleStepAst`/`RuleActionAst` shells; the parser now emits `RuleStep`/`RuleAction` directly
- Removed the empty `RuleReloadWarning`/`RuleComplexityWarning` typealiases
- Simplified `RuleComplexityAnalyzer.analyze()` to return `List<LocalizedMessage>` directly

## 0.2.1

- Fixed falling back to the original stack limit on pickup

## 0.2.0

- Corrected several localization errors
