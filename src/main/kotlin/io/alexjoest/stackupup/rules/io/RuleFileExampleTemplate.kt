package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUpIds
import java.io.File

object RuleFileExampleTemplate {
    val exampleContent: String = """
EDITING THIS FILE WILL HAVE NO EFFECT.
This is a syntax reference. Place actual rules in config/stackupup/main.su.


═══ DSL v2 Rule Syntax ═══════════════════════════════════════════════════

Format: <condition> -> <action>
Rule without condition applies to all items.


═══ Fields ═══════════════════════════════════════════════════════════════

  field     meaning              example
  ─────     ───────              ───────
  item      item ID              item = minecraft:egg
  mod       mod ID               mod = thermal
  type      type                 type = block
  ore       ore dictionary       ore = ingotSteel
  material  GT material          material = steel
  meta      damage / meta        meta = 324
  metadata  alias for meta       metadata = 324
  size      current limit        size > 2
  tab       creative tab         tab = buildingBlocks


═══ Comparisons ═════════════════════════════════════════════════════════

  =     equal           item = minecraft:egg
  !=    not equal       mod != minecraft
  >     greater than    size > 64
  >=    greater/equal   size >= 64
  <     less than       2 < size
  <=    less/equal      size <= 128


═══ Lists ═══════════════════════════════════════════════════════════════

  field in [value1, value2, ...]

  item in [minecraft:egg, minecraft:snowball] -> 128
  mod in [thermal, ic2, enderio] -> 1024
  ore in [ingotSteel, ingotIron] -> 2048
  material in [steel, modid:material_name] -> 2048
  meta in [11305, 11306] -> 512


═══ Range ═══════════════════════════════════════════════════════════════

  size > 2 && size < 64 -> 1024
  2 < size < 64 -> 1024


═══ Logic ═══════════════════════════════════════════════════════════════

  && has higher precedence than ||
  Parentheses are not supported.


═══ Actions ═════════════════════════════════════════════════════════════

  -> 128      set to 128
  -> +32      add 32
  -> -16      subtract 16
  -> *2       multiply by 2
  -> /2       divide by 2

  Chained example:
  -> *2 -> +10 -> /2


═══ Item matching ═══════════════════════════════════════════════════════

  item = minecraft:egg
  item = gregtech:meta_ingot
  item = gregtech:meta_ingot@324
  item = gregtech:meta_ingot && meta = 324
  item in [minecraft:egg, minecraft:snowball]

  type = block && mod = minecraft
  type = item

  material = steel
  material = modid:material_name
  material is optional: it is set only when GregTech is loaded and
  the item resolves to a GT material. It is empty when GregTech is not
  loaded or for non-material items. Use material registry names: native
  GT materials such as steel, or modid:name for cross-mod material names.
  It does not cover every GT item.


═══ Wildcards ═══════════════════════════════════════════════════════════

  item = *              all stackable items (baseSize > 1)
  item = thermal:* -> 256
  item = minecraft:*_ball -> 128


═══ Examples ════════════════════════════════════════════════════════════

  item = minecraft:egg -> 64

  item in [minecraft:egg, minecraft:snowball] -> 128

  mod = thermal -> 1024

  type = block -> 1024

  ore = ingotSteel -> 1024
  ore = ingotSteel -> *2
  material = steel -> 2048

  item = gregtech:meta_ingot@324 -> 512
  item = gregtech:meta_ingot && meta = 324 -> 512

  item = * -> 128            all stackable items to 128
  tab = buildingBlocks -> 256

  2 < size < 64 -> 1024
  100 < meta < 300 -> 512

  size > 1 -> *2 -> +10


═══ Priority ═══════════════════════════════════════════════════════════

  Later rules override or continue from previous results.

  item = minecraft:egg -> 64
  item = minecraft:egg -> *2    # result: 128


═══ Rule file locations ═════════════════════════════════════════════════

  main rules:    config/stackupup/main.su
  user overrides: config/stackupup/user.su
  world rules:   <save>/data/stackupup/world.su

  Reload: /stackupup reload
    """.trimIndent()

    /**
     * Always overwrite the example file on startup.
     * Compares content first to avoid unnecessary disk writes.
     */
    fun refreshExample(globalDirectory: File) {
        val target = File(globalDirectory, StackUpUpIds.EXAMPLE_RULES_FILE_NAME)
        target.parentFile?.mkdirs()
        if (target.exists()) {
            val existing = target.readText(Charsets.UTF_8)
            if (existing == exampleContent) {
                return
            }
        }
        target.writeText(exampleContent, Charsets.UTF_8)
    }

    val markdownExampleContent: String = """
EDITING THIS FILE HAS NO EFFECT.
This is a syntax reference. Place actual rules in <save>/data/stackupup/main.su.md.


# state

State values are read by gate expressions in the rules section.
Change them via `/stackupup state set <name> <true|false>`.

- phase1 = false
- expert_mode = false


# rules

Gate expressions go in headings: state("name") and modLoaded("modid").
Combine with && / || / !. Parentheses are not supported.
## always means unconditional.

Rules inside ```stackupup (or ```su) fenced code blocks.
Same DSL v2 syntax as the `.su` rules.

## always

```stackupup
item = minecraft:egg -> 128
item in [minecraft:egg, minecraft:snowball] -> 256
```

## modLoaded("thermal")

```stackupup
mod = thermal -> 1024
```

## state("expert_mode")

```stackupup
type = block -> 2048
```

## state("phase1") && modLoaded("enderio")

```stackupup
mod = enderio -> 4096
```


═══ Gate Functions ════════════════════════════════════════════════════

  state("key")        Read a persisted boolean gate state
  modLoaded("modid")   True when the mod is loaded (constant)


═══ State Commands ════════════════════════════════════════════════════

  /stackupup state get <name>          Read a state value
  /stackupup state set <name> <bool>   Set a state value
  /stackupup reload                    Reload rules


═══ File Locations ═════════════════════════════════════════════════════

  config template:  config/stackupup/main.su.md  (author reference)
  world instance:   <save>/data/stackupup/main.su.md  (runtime, gates read state from here)

  Reload: /stackupup reload
    """.trimIndent()

    fun refreshMarkdownExample(globalDirectory: File) {
        val target = File(globalDirectory, StackUpUpIds.EXAMPLE_MARKDOWN_RULES_FILE_NAME)
        target.parentFile?.mkdirs()
        if (target.exists()) {
            val existing = target.readText(Charsets.UTF_8)
            if (existing == markdownExampleContent) {
                return
            }
        }
        target.writeText(markdownExampleContent, Charsets.UTF_8)
    }
}
