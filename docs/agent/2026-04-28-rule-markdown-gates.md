# Rule Markdown Gates Notes

## Decision

Use Markdown as a rule container, but keep rule bodies in fenced code blocks using the existing StackUpUp DSL.

## Format

```md
# StackUpUp Rules

## always

```stackupup
item = minecraft:egg -> 128
```

## modLoaded(gregtech)

```stackupup
item = gregtech:meta_item_1 && meta = 516 -> 512
```

### gate(expert_mode)

```su
ore = ingotSteel -> 1024
```
```

## Semantics

- Markdown headings express gate scope.
- Heading nesting means inherited gates and logical AND.
- Same-level headings are independent branches.
- `always` is unconditional.
- Only `stackupup` and `su` fenced code blocks contain rules.
- Plain Markdown text is documentation and is ignored.
- Rule blocks are predefined; gate state only enables or disables blocks.

## First Gates

- `always`
- `modLoaded(id[, id...])`
- `gate(name)` for server-wide boolean state

Future optional gates:

- `gameStages(id[, id...])`
- `ftbQuestCompleted(id[, id...])`

## Runtime Model

- Parse Markdown and compile rules only on explicit reload, world load, or gate state changes.
- Do not parse Markdown on stack-limit hot paths.
- Gate state changes recompute the enabled block signature.
- If the enabled block signature is unchanged, keep the current `RuleSnapshot` and cache.
- If it changes, rebuild a full `RuleSnapshot` and replace `StackLimitService`.

## Scope

Gate state is server/world-wide only. Per-player or per-team stack limits are out of scope because machines, pipes, slots, and item entities need one consistent stack-limit view.
